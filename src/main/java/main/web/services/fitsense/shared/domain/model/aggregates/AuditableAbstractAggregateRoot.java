package main.web.services.fitsense.shared.domain.model.aggregates;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.OffsetDateTime;

/**
 * Base para agregados que llevan created_at y updated_at.
 */
@Getter
@MappedSuperclass
public abstract class AuditableAbstractAggregateRoot<T extends AbstractAggregateRoot<T>>
        extends CreatedAuditableAbstractAggregateRoot<T> {

    /** Mismo motivo que created_at: @LastModifiedDate no admite OffsetDateTime. */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}