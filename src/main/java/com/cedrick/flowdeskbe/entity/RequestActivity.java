package com.cedrick.flowdeskbe.entity;

import com.cedrick.flowdeskbe.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * Append-only audit row. {@link Immutable} stops Hibernate from ever issuing an UPDATE,
 * there are no setters, and a database trigger rejects UPDATE/DELETE as a last line of defence.
 */
@Entity
@Immutable
@Table(name = "request_activity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, updatable = false)
    private ServiceRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20, updatable = false)
    private RequestStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20, updatable = false)
    private RequestStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private RequestActivity(ServiceRequest request, RequestStatus previousStatus, RequestStatus newStatus,
                            User actor, String comment) {
        this.request = request;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.actor = actor;
        this.comment = comment;
        this.createdAt = Instant.now();
    }

    public static RequestActivity of(ServiceRequest request, RequestStatus previousStatus,
                                     RequestStatus newStatus, User actor, String comment) {
        return new RequestActivity(request, previousStatus, newStatus, actor, comment);
    }
}
