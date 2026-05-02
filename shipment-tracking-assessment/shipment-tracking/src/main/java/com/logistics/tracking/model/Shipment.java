package com.logistics.tracking.model;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "shipments",
    uniqueConstraints = @UniqueConstraint(columnNames = {"shipment_id", "company_id"}))
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Shipment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "shipment_id", nullable = false)
    private String shipmentId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "carrier")
    private String carrier;

    @Type(type = "jsonb")
    @Column(name = "origin", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> origin;

    @Type(type = "jsonb")
    @Column(name = "destination", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 50)
    private EventType currentStatus;

    @Type(type = "jsonb")
    @Column(name = "current_location", columnDefinition = "jsonb")
    private Map<String, Object> currentLocation;

    @Column(name = "eta")
    private Instant eta;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
        if (currentStatus == null) currentStatus = EventType.CREATED;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
