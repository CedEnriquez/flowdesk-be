package com.cedrick.flowdeskbe.fulfillment;

import com.cedrick.flowdeskbe.entity.ExternalOperation;
import com.cedrick.flowdeskbe.entity.User;
import com.cedrick.flowdeskbe.enums.ExternalOperationState;
import com.cedrick.flowdeskbe.enums.ExternalOperationType;
import com.cedrick.flowdeskbe.enums.RequestStatus;
import com.cedrick.flowdeskbe.enums.Role;
import com.cedrick.flowdeskbe.exception.FulfillmentRejectedException;
import com.cedrick.flowdeskbe.repository.ExternalOperationRepository;
import com.cedrick.flowdeskbe.repository.RequestActivityRepository;
import com.cedrick.flowdeskbe.repository.ServiceRequestRepository;
import com.cedrick.flowdeskbe.repository.UserRepository;
import com.cedrick.flowdeskbe.security.AuthUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Provider succeeds but the response is lost; the admin retries. The work must happen exactly once. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fulfilltest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FulfillmentIdempotencyIntegrationTest.FakeProvider.class)
class FulfillmentIdempotencyIntegrationTest {

    /** Dedupes by idempotency key like a real provider, and can lose its response or refuse the call. */
    static class ControllableClient implements FulfillmentClient {
        final Map<String, FulfillmentResult> processed = new ConcurrentHashMap<>();
        final AtomicInteger executions = new AtomicInteger();
        volatile boolean loseResponse;
        volatile boolean lookupAvailable = true;
        volatile boolean reject;

        @Override
        public FulfillmentResult fulfill(String key, long requestId) {
            if (reject) throw new FulfillmentRejectedException("Provider refused the operation");
            FulfillmentResult result = processed.computeIfAbsent(key, k -> {
                executions.incrementAndGet();
                return new FulfillmentResult("REF-" + UUID.randomUUID());
            });
            if (loseResponse) throw new FulfillmentTimeoutException("read timed out");
            return result;
        }

        @Override
        public Optional<FulfillmentResult> lookup(String key) {
            if (!lookupAvailable) throw new FulfillmentTimeoutException("lookup timed out");
            return Optional.ofNullable(processed.get(key));
        }
    }

    @TestConfiguration
    static class FakeProvider {
        @Bean @Primary ControllableClient controllableClient() { return new ControllableClient(); }
    }

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserRepository users;
    @Autowired ServiceRequestRepository requests;
    @Autowired RequestActivityRepository activities;
    @Autowired ExternalOperationRepository operations;
    @Autowired ControllableClient provider;

    RequestPostProcessor alice, admin;

    @BeforeEach
    void setUp() {
        operations.deleteAll();
        activities.deleteAll();
        requests.deleteAll();
        users.deleteAll();
        provider.processed.clear();
        provider.executions.set(0);
        provider.loseResponse = false;
        provider.lookupAvailable = true;
        provider.reject = false;
        alice = login(users.save(new User("alice@test.dev", "Alice", "x", Role.REQUESTER)));
        admin = login(users.save(new User("admin@test.dev", "Admin", "x", Role.ADMIN)));
    }

    private RequestPostProcessor login(User u) {
        AuthUserPrincipal p = new AuthUserPrincipal(u);
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private long approvedRequest() throws Exception {
        MvcResult res = mvc.perform(post("/api/requests").with(alice).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Laptop\",\"description\":\"Need one\",\"type\":\"SERVICE\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated()).andReturn();
        long id = json.readTree(res.getResponse().getContentAsString()).get("data").get("id").asLong();
        transition(id, "IN_REVIEW", 200);
        transition(id, "APPROVED", 200);
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions transition(long id, String to, int expected) throws Exception {
        return mvc.perform(post("/api/requests/" + id + "/transitions").with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"toStatus\":\"" + to + "\"}"))
                .andExpect(status().is(expected));
    }

    private RequestStatus statusOf(long id) {
        return requests.findById(id).orElseThrow().getStatus();
    }

    private ExternalOperation operationOf(long id) {
        return operations.findByRequestIdAndOperationType(id, ExternalOperationType.FULFILL).orElseThrow();
    }

    @Test
    void happyPathCallsProviderOnceAndStoresReference() throws Exception {
        long id = approvedRequest();
        transition(id, "FULFILLED", 200);

        assertThat(provider.executions.get()).isEqualTo(1);
        assertThat(statusOf(id)).isEqualTo(RequestStatus.FULFILLED);
        assertThat(operationOf(id).getState()).isEqualTo(ExternalOperationState.SUCCEEDED);
        assertThat(operationOf(id).getExternalReference()).startsWith("REF-");
    }

    @Test
    void timeoutAfterSuccessIsResolvedByLookupWithoutRepeatingTheWork() throws Exception {
        long id = approvedRequest();
        provider.loseResponse = true;

        transition(id, "FULFILLED", 200);

        assertThat(provider.executions.get()).isEqualTo(1);
        assertThat(statusOf(id)).isEqualTo(RequestStatus.FULFILLED);
    }

    @Test
    void unknownOutcomeLeavesRequestUntouchedAndRetryDoesNotProcessTwice() throws Exception {
        long id = approvedRequest();
        provider.loseResponse = true;
        provider.lookupAvailable = false;

        transition(id, "FULFILLED", 504).andExpect(jsonPath("$.code").value("FULFILLMENT_OUTCOME_UNKNOWN"));

        assertThat(statusOf(id)).isEqualTo(RequestStatus.APPROVED);
        assertThat(operationOf(id).getState()).isEqualTo(ExternalOperationState.PENDING);
        assertThat(provider.executions.get()).isEqualTo(1); // the provider DID process it

        // Admin clicks Retry; the network is healthy again.
        provider.loseResponse = false;
        provider.lookupAvailable = true;
        transition(id, "FULFILLED", 200);

        assertThat(provider.executions.get()).isEqualTo(1); // still exactly once
        assertThat(statusOf(id)).isEqualTo(RequestStatus.FULFILLED);
        assertThat(operationOf(id).getState()).isEqualTo(ExternalOperationState.SUCCEEDED);
        assertThat(operationOf(id).getAttempts()).isEqualTo(2);
        assertThat(activities.findByRequestIdOrderByCreatedAtAscIdAsc(id))
                .filteredOn(a -> a.getNewStatus() == RequestStatus.FULFILLED).hasSize(1);
    }

    @Test
    void fulfillingAgainAfterSuccessIsRejectedAndNeverCallsProvider() throws Exception {
        long id = approvedRequest();
        transition(id, "FULFILLED", 200);
        transition(id, "FULFILLED", 409);

        assertThat(provider.executions.get()).isEqualTo(1);
    }

    @Test
    void definitiveRejectionChangesNothingAndCanBeRetriedLater() throws Exception {
        long id = approvedRequest();
        provider.reject = true;

        transition(id, "FULFILLED", 502).andExpect(jsonPath("$.code").value("FULFILLMENT_REJECTED"));
        assertThat(statusOf(id)).isEqualTo(RequestStatus.APPROVED);
        assertThat(provider.executions.get()).isZero();

        provider.reject = false;
        transition(id, "FULFILLED", 200);
        assertThat(provider.executions.get()).isEqualTo(1);
    }
}
