package main.web.services.fitsense.catalog.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import main.web.services.fitsense.catalog.domain.model.valueobjects.PrescriptionType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Catalogo de solo lectura. Se importa una vez y no se edita nunca desde la API:
 * por eso no extiende de los agregados auditables (no hay escritura que auditar)
 * y created_at se mapea como no insertable.
 * <p>
 * Solo el subconjunto con is_active = true es elegible por el generador.
 */
@Getter
@Entity
@Table(name = "exercises")
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exercise_id")
    private Long id;

    @Column(name = "source_code", nullable = false, length = 20)
    private String sourceCode;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_es")
    private String nameEs;

    @Column(name = "body_part_id", nullable = false)
    private Short bodyPartId;

    @Column(name = "equipment_id", nullable = false)
    private Short equipmentId;

    @Column(name = "target_muscle", nullable = false, length = 60)
    private String targetMuscle;

    @Column(name = "synergist_muscle", length = 60)
    private String synergistMuscle;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "secondary_muscles", columnDefinition = "varchar(60)[]")
    private String[] secondaryMuscles;

    @Column(name = "instructions_es")
    private String instructionsEs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "instruction_steps_es", columnDefinition = "jsonb")
    private String instructionStepsEs;

    @Column(name = "instructions_en")
    private String instructionsEn;

    @Column(name = "image_path")
    private String imagePath;

    @Column(name = "gif_path")
    private String gifPath;

    /** Credito obligatorio de las imagenes y GIF. Se devuelve siempre en la API. */
    @Column(name = "media_attribution", length = 200)
    private String mediaAttribution;

    @Column(name = "difficulty_level", nullable = false)
    private Short difficultyLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_prescription", nullable = false, length = 25)
    private PrescriptionType defaultPrescription;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Exercise() {
        // JPA
    }

    public List<String> secondaryMusclesAsList() {
        return secondaryMuscles == null ? List.of() : Arrays.asList(secondaryMuscles);
    }

    /** Nombre para mostrar. Cae al ingles si la traduccion falta (solo posible si no esta activo). */
    public String displayName() {
        return nameEs != null ? nameEs : nameEn;
    }
}
