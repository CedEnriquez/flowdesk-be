package com.cedrick.flowdeskbe.repository;

import com.cedrick.flowdeskbe.entity.RequestActivity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestActivityRepository extends JpaRepository<RequestActivity, Long> {

    @EntityGraph(attributePaths = "actor")
    List<RequestActivity> findByRequestIdOrderByCreatedAtAscIdAsc(Long requestId);
}
