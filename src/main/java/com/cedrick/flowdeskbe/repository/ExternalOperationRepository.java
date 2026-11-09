package com.cedrick.flowdeskbe.repository;

import com.cedrick.flowdeskbe.entity.ExternalOperation;
import com.cedrick.flowdeskbe.enums.ExternalOperationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExternalOperationRepository extends JpaRepository<ExternalOperation, Long> {

    Optional<ExternalOperation> findByRequestIdAndOperationType(Long requestId, ExternalOperationType type);
}
