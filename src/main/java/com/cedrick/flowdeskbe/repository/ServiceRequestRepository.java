package com.cedrick.flowdeskbe.repository;

import com.cedrick.flowdeskbe.entity.ServiceRequest;
import com.cedrick.flowdeskbe.enums.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

    @EntityGraph(attributePaths = "owner")
    Optional<ServiceRequest> findWithOwnerById(Long id);

    @EntityGraph(attributePaths = "owner")
    Page<ServiceRequest> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    Page<ServiceRequest> findByStatus(RequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    Page<ServiceRequest> findByOwnerId(Long ownerId, Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    Page<ServiceRequest> findByOwnerIdAndStatus(Long ownerId, RequestStatus status, Pageable pageable);
}
