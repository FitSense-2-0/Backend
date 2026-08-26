package main.web.services.fitsense.shared.domain.model.aggregates;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.AbstractAggregateRoot;

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
public abstract class CreatedAuditableAbstractAggregateRoot<T extends AbstractAggregateRoot<T>>
        extends AbstractAggregateRoot<T> {

    /**
     * Timestamp de Hibernate y no @CreatedDate de Spring Data: la auditoria de
     * Spring Data no admite OffsetDateTime como tipo destino (solo LocalDateTime,
     * Instant, Date y similares), y OffsetDateTime es el tipo correcto contra un
     * TIMESTAMPTZ. Con @CreatedDate cualquier INSERT falla en tiempo de ejecucion.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}