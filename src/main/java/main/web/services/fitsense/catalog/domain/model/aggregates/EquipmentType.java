package main.web.services.fitsense.catalog.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;

/** Tabla de traduccion. Solo lectura: se carga en V3 y no se edita desde la API. */
@Getter
@Entity
@Table(name = "equipment_types")
public class EquipmentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "equipment_id")
    private Short id;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name_es", nullable = false, length = 80)
    private String nameEs;

    /** true si no es razonable tenerlo en casa: se excluye con training_location = HOME. */
    @Column(name = "requires_gym", nullable = false)
    private boolean requiresGym;

    @Column(name = "display_order", nullable = false)
    private Short displayOrder;

    protected EquipmentType() {
        // JPA
    }
}
