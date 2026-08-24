package main.web.services.fitsense.shared.domain.model.aggregates;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.OffsetDateTime;

/**
 * Base para agregados que llevan created_at y updated_at.
 */
@Getter
@MappedSuperclass
public abstract class AuditableAbstractAggregateRoot<T extends AbstractAggregateRoot<T>>
        extends CreatedAuditableAbstractAggregateRoot<T> {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
