package main.web.services.fitsense.shared.domain.model.aggregates;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Base para agregados que solo llevan created_at.
 * <p>
 * A diferencia del proyecto de referencia, esta clase NO declara la clave
 * primaria: el diseno nombra cada PK de forma distinta (user_id, plan_id,
 * session_id...) y con ddl-auto=validate los nombres deben coincidir
 * exactamente con las migraciones.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class CreatedAuditableAbstractAggregateRoot<T extends AbstractAggregateRoot<T>>
        extends AbstractAggregateRoot<T> {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
