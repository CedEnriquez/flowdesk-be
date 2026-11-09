package com.cedrick.flowdeskbe.entity;

import com.cedrick.flowdeskbe.enums.Priority;
import com.cedrick.flowdeskbe.enums.RequestStatus;
import com.cedrick.flowdeskbe.enums.RequestType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private User owner;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic locking: a concurrent modification fails with a 409 instead of silently overwriting. */
    @Version
    @Column(nullable = false)
    private Long version;

    public ServiceRequest(String title, String description, RequestType type, Priority priority, User owner) {
        this.title = title;
        this.description = description;
        this.type = type;
        this.priority = priority;
        this.owner = owner;
        this.status = RequestStatus.SUBMITTED;
    }

    /** Status only changes through the workflow service; there is deliberately no generic setter. */
    public void changeStatus(RequestStatus newStatus) {
        this.status = newStatus;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
