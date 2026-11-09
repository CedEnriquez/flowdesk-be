package com.cedrick.flowdeskbe.service;

import com.cedrick.flowdeskbe.dto.*;
import com.cedrick.flowdeskbe.entity.ExternalOperation;
import com.cedrick.flowdeskbe.entity.RequestActivity;
import com.cedrick.flowdeskbe.entity.ServiceRequest;
import com.cedrick.flowdeskbe.entity.User;
import com.cedrick.flowdeskbe.enums.ExternalOperationType;
import com.cedrick.flowdeskbe.enums.RequestStatus;
import com.cedrick.flowdeskbe.exception.BusinessValidationException;
import com.cedrick.flowdeskbe.exception.ForbiddenOperationException;
import com.cedrick.flowdeskbe.exception.FulfillmentOutcomeUnknownException;
import com.cedrick.flowdeskbe.exception.FulfillmentRejectedException;
import com.cedrick.flowdeskbe.fulfillment.FulfillmentClient;
import com.cedrick.flowdeskbe.fulfillment.FulfillmentResult;
import com.cedrick.flowdeskbe.fulfillment.FulfillmentTimeoutException;
import com.cedrick.flowdeskbe.repository.ExternalOperationRepository;
import com.cedrick.flowdeskbe.exception.InvalidTransitionException;
import com.cedrick.flowdeskbe.exception.ResourceNotFoundException;
import com.cedrick.flowdeskbe.mapper.RequestMapper;
import com.cedrick.flowdeskbe.repository.RequestActivityRepository;
import com.cedrick.flowdeskbe.repository.ServiceRequestRepository;
import com.cedrick.flowdeskbe.repository.UserRepository;
import com.cedrick.flowdeskbe.security.AuthUserPrincipal;
import com.cedrick.flowdeskbe.workflow.Transition;
import com.cedrick.flowdeskbe.workflow.WorkflowRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ServiceRequestRepository requestRepository;
    private final RequestActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final ExternalOperationRepository operationRepository;
    private final FulfillmentClient fulfillmentClient;
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public RequestDetailResponse create(CreateRequestDto dto, AuthUserPrincipal currentUser) {
        User owner = userRepository.getReferenceById(currentUser.getId());
        ServiceRequest request = requestRepository.save(new ServiceRequest(
                dto.title().trim(), dto.description().trim(), dto.type(), dto.priority(), owner));

        // First audit row (null -> SUBMITTED) so the history is complete from the start.
        activityRepository.save(RequestActivity.of(request, null, RequestStatus.SUBMITTED, owner, null));

        return RequestMapper.toDetail(requestRepository.findWithOwnerById(request.getId()).orElseThrow(), currentUser);
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestSummaryResponse> list(RequestStatus status, Integer page, Integer size,
                                                     AuthUserPrincipal currentUser) {
        int safePage = page == null ? 0 : Math.max(page, 0);
        int safeSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<ServiceRequest> result;
        if (currentUser.isAdmin()) {
            result = status == null
                    ? requestRepository.findAllBy(pageable)
                    : requestRepository.findByStatus(status, pageable);
        } else {
            result = status == null
                    ? requestRepository.findByOwnerId(currentUser.getId(), pageable)
                    : requestRepository.findByOwnerIdAndStatus(currentUser.getId(), status, pageable);
        }
        return PageResponse.from(result.map(RequestMapper::toSummary));
    }

    @Transactional(readOnly = true)
    public RequestDetailResponse get(Long id, AuthUserPrincipal currentUser) {
        return RequestMapper.toDetail(loadVisible(id, currentUser), currentUser);
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> getActivity(Long id, AuthUserPrincipal currentUser) {
        loadVisible(id, currentUser);
        return activityRepository.findByRequestIdOrderByCreatedAtAscIdAsc(id).stream()
                .map(RequestMapper::toActivity)
                .toList();
    }

    /**
     * Status change + audit row in ONE transaction: both commit or both roll back.
     * Order of checks: visibility (404) -> transition exists (409) -> role/ownership (403) -> comment (400).
     *
     * Deliberately NOT @Transactional: moving to FULFILLED calls an external provider, and that call
     * must never run inside (or be rolled back with) a database transaction. See {@link #fulfill}.
     */
    public RequestDetailResponse transition(Long id, TransitionRequestDto dto, AuthUserPrincipal currentUser) {
        if (dto.toStatus() == RequestStatus.FULFILLED) {
            return fulfill(id, dto, currentUser);
        }
        return inTransaction(() -> {
            ServiceRequest request = loadVisible(id, currentUser);
            return applyTransition(request, dto, currentUser);
        });
    }

    /**
     * Fulfilling triggers an external operation, so it is split into three steps to make a retry safe
     * when the provider succeeded but its response never reached us (timeout):
     *
     * 1. (tx) validate, then record the intent in {@code external_operations} with a STABLE idempotency key.
     * 2. (no tx) call the provider with that key. On a timeout, ask the provider what happened (lookup)
     *    instead of assuming failure; if it is still unknown, answer 504 and leave everything unchanged.
     * 3. (tx) mark the operation SUCCEEDED and move the request to FULFILLED, with its audit row.
     *
     * Every retry reuses the same key, so the provider returns the original result instead of repeating the
     * work, and an operation already SUCCEEDED locally skips the provider call entirely.
     */
    private RequestDetailResponse fulfill(Long id, TransitionRequestDto dto, AuthUserPrincipal currentUser) {
        // Step 1
        ExternalOperation operation = inTransaction(() -> {
            ServiceRequest request = loadVisible(id, currentUser);
            authorize(request, dto, currentUser);
            ExternalOperation op = operationRepository
                    .findByRequestIdAndOperationType(id, ExternalOperationType.FULFILL)
                    .orElseGet(() -> operationRepository.save(ExternalOperation.pending(id, ExternalOperationType.FULFILL)));
            if (!op.isSucceeded()) op.recordAttempt();
            return op;
        });

        // Step 2
        String reference = operation.isSucceeded()
                ? operation.getExternalReference()
                : callProvider(operation);

        // Step 3
        return inTransaction(() -> {
            ServiceRequest request = loadVisible(id, currentUser);
            ExternalOperation op = operationRepository.findById(operation.getId()).orElseThrow();
            if (!op.isSucceeded()) op.markSucceeded(reference);
            return applyTransition(request, dto, currentUser);
        });
    }

    private String callProvider(ExternalOperation operation) {
        String key = operation.getIdempotencyKey();
        try {
            return fulfillmentClient.fulfill(key, operation.getRequestId()).reference();
        } catch (FulfillmentTimeoutException timeout) {
            log.warn("Fulfillment call for {} timed out; asking the provider for the outcome", key);
            Optional<FulfillmentResult> known = lookupQuietly(key);
            if (known.isPresent()) return known.get().reference();
            recordFailure(operation.getId(), "Timed out; provider could not confirm the outcome");
            throw new FulfillmentOutcomeUnknownException();
        } catch (FulfillmentRejectedException rejected) {
            recordFailure(operation.getId(), rejected.getMessage());
            throw rejected;
        }
    }

    private Optional<FulfillmentResult> lookupQuietly(String key) {
        try {
            return fulfillmentClient.lookup(key);
        } catch (RuntimeException e) {
            log.warn("Provider lookup for {} failed", key, e);
            return Optional.empty();
        }
    }

    private void recordFailure(Long operationId, String message) {
        inTransaction(() -> {
            operationRepository.findById(operationId).ifPresent(op -> op.recordFailure(message));
            return null;
        });
    }

    /** Workflow, role, ownership and comment checks. Throws the matching ApiException. */
    private Transition authorize(ServiceRequest request, TransitionRequestDto dto, AuthUserPrincipal currentUser) {
        RequestStatus from = request.getStatus();
        Transition rule = WorkflowRules.find(from, dto.toStatus())
                .orElseThrow(() -> new InvalidTransitionException(
                        "Cannot move a request from " + from + " to " + dto.toStatus()));

        boolean isOwner = request.getOwner().getId().equals(currentUser.getId());
        if (!rule.allowedRoles().contains(currentUser.getRole())) {
            throw new ForbiddenOperationException("Your role is not allowed to move a request to " + dto.toStatus());
        }
        if (rule.ownerOnly() && !isOwner) {
            throw new ForbiddenOperationException("Only the owner of the request can move it to " + dto.toStatus());
        }
        if (rule.requiresComment() && cleanComment(dto) == null) {
            throw new BusinessValidationException("A comment is required when moving to " + dto.toStatus(), "comment");
        }
        return rule;
    }

    /** Must run inside a transaction: validates, changes the status and writes the audit row. */
    private RequestDetailResponse applyTransition(ServiceRequest request, TransitionRequestDto dto,
                                                  AuthUserPrincipal currentUser) {
        RequestStatus from = request.getStatus();
        authorize(request, dto, currentUser);

        request.changeStatus(dto.toStatus());
        requestRepository.saveAndFlush(request); // surfaces an optimistic-lock conflict right here

        User actor = userRepository.getReferenceById(currentUser.getId());
        activityRepository.save(RequestActivity.of(request, from, dto.toStatus(), actor, cleanComment(dto)));

        return RequestMapper.toDetail(request, currentUser);
    }

    private static String cleanComment(TransitionRequestDto dto) {
        return dto.comment() == null || dto.comment().isBlank() ? null : dto.comment().trim();
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    /** Admins see everything; requesters only their own. A hidden request is reported as 404, not 403. */
    private ServiceRequest loadVisible(Long id, AuthUserPrincipal currentUser) {
        ServiceRequest request = requestRepository.findWithOwnerById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        if (!currentUser.isAdmin() && !request.getOwner().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Request not found");
        }
        return request;
    }
}
