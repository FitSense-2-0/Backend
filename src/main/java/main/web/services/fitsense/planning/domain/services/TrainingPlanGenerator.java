package main.web.services.fitsense.planning.domain.services;

import main.web.services.fitsense.planning.domain.model.valueobjects.GenerationSource;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanDraft;
import main.web.services.fitsense.planning.domain.model.valueobjects.PlanGenerationContext;

import java.util.List;

/**
 * Puerto del generador. El dominio no sabe si detras hay un modelo de lenguaje o
 * un motor de reglas: solo exige que la propuesta llegue en el mismo formato y
 * pase las mismas trece validaciones.
 */
public interface TrainingPlanGenerator {

    /**
     * @param previousProblems validaciones incumplidas en el intento anterior.
     *                         Vacio en el primer intento. La IA las usa para
     *                         corregir (19.4); el motor de reglas las ignora
     *                         porque su salida es determinista.
     */
    PlanDraft generate(PlanGenerationContext context, List<String> previousProblems);

    GenerationSource source();

    /** Queda en model_name. Null para el motor de reglas. */
    default String modelName() {
        return null;
    }
}
