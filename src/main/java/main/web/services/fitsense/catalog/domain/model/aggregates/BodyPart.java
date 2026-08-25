package main.web.services.fitsense.catalog.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;

/** Tabla de traduccion. Solo lectura: se carga en V3 y no se edita desde la API. */
@Getter
@Entity
@Table(name = "body_parts")
public class BodyPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "body_part_id")
    private Short id;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name_es", nullable = false, length = 80)
    private String nameEs;

    @Column(name = "display_order", nullable = false)
    private Short displayOrder;

    protected BodyPart() {
        // JPA
    }
}
