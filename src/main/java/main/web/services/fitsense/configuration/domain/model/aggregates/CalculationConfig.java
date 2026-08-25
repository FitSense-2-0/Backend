package main.web.services.fitsense.configuration.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Umbrales versionados. Se escribe por migracion, no por API: una fila nueva es
 * una version nueva del calculo, y toda metrica guarda en calculation_version
 * con que fila se produjo. Editar una fila existente invalidaria semanas ya
 * cerradas.
 * <p>
 * params se mapea como String, no como objeto: el parseo tipado ocurre en la
 * capa de aplicacion (ConfigurationContextFacade) para no atar el agregado al
 * serializador de turno.
 */
@Getter
@Entity
@Table(name = "calculation_configs")
public class CalculationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Short id;

    @Column(name = "version", nullable = false, length = 30)
    private String version;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false, columnDefinition = "jsonb")
    private String params;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "valid_from", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime validFrom;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected CalculationConfig() {
        // JPA
    }
}
