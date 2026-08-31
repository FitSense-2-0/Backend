package main.web.services.fitsense.catalog.infrastructure.persistence.jpa.repositories;

import main.web.services.fitsense.catalog.domain.model.aggregates.Exercise;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    /**
     * Filtro del catalogo para la app.
     * <p>
     * Se usa COALESCE en vez de "(:param IS NULL OR columna = :param)": ese
     * patron obliga a PostgreSQL a deducir el tipo de un parametro que solo
     * aparece comparado contra NULL, y falla con "could not determine data type
     * of parameter". Dentro de COALESCE el tipo lo aporta la columna.
     */
    @Query("""
           SELECT e FROM Exercise e
           WHERE e.active = true
             AND e.bodyPartId = COALESCE(:bodyPartId, e.bodyPartId)
             AND e.equipmentId = COALESCE(:equipmentId, e.equipmentId)
             AND e.difficultyLevel <= :maxDifficulty
             AND LOWER(e.nameEs) LIKE :search
           ORDER BY e.nameEs ASC
           """)
    List<Exercise> search(@Param("bodyPartId") Short bodyPartId,
                          @Param("equipmentId") Short equipmentId,
                          @Param("maxDifficulty") short maxDifficulty,
                          @Param("search") String search,
                          Limit limit);

    /**
     * Conjunto elegible. Los tres ultimos parametros son las restricciones de
     * seguridad: cuando llegan en true, se descarta lo marcado.
     * <p>
     * La forma "(:excluir = false OR e.campo = false)" permite pasar la
     * restriccion como bandera en vez de tener cuatro consultas distintas.
     * <p>
     * equipmentIds nunca llega vacio (el query service garantiza al menos
     * 'body weight') y blockedIds tampoco (se rellena con un centinela): un IN
     * vacio en JPQL es un error de sintaxis en tiempo de ejecucion, no una
     * lista sin resultados.
     */
    @Query("""
           SELECT e FROM Exercise e
           WHERE e.active = true
             AND e.equipmentId IN :equipmentIds
             AND e.difficultyLevel <= :maxDifficulty
             AND e.id NOT IN :blockedIds
             AND (:excludeHighImpact = false OR e.highImpact = false)
             AND (:excludeFloorWork  = false OR e.requiresFloor = false)
             AND (:excludeAxialLoad  = false OR e.axialLoad = false)
           ORDER BY e.bodyPartId ASC, e.difficultyLevel ASC, e.id ASC
           """)
    List<Exercise> findEligible(@Param("equipmentIds") List<Short> equipmentIds,
                                @Param("maxDifficulty") short maxDifficulty,
                                @Param("blockedIds") List<Long> blockedIds,
                                @Param("excludeHighImpact") boolean excludeHighImpact,
                                @Param("excludeFloorWork") boolean excludeFloorWork,
                                @Param("excludeAxialLoad") boolean excludeAxialLoad);

    long countByActiveTrue();
}
