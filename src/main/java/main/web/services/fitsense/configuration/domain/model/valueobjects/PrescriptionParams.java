package main.web.services.fitsense.configuration.domain.model.valueobjects;

import java.util.Map;

/**
 * Bloque "prescription" de calculation_configs.params, anadido en V12,
 * ampliado en V14 con los parametros de estimacion de duracion y cambiado en
 * V18 por los principios de prescripcion P-1.0.
 * <p>
 * Los limites de repeticiones y el coste temporal de una serie no son
 * constantes del generador: son umbrales de calculo, y como el resto viven en
 * la configuracion versionada. Calibrarlos tras el piloto es insertar una fila
 * nueva, no recompilar, y cada plan queda ligado a la version con la que se
 * produjo.
 */
public record PrescriptionParams(
        Integer sessionMinutesFloorPct,
        Integer secondsPerRep,
        Integer transitionSeconds,
        Integer warmupMinutes,
        Integer defaultRestSeconds,
        Integer durationTolerancePct,

        /**
         * Rangos por objetivo de V12. Ya NO vienen en la configuracion activa
         * (MVP-1.5): hacian ilegal la prescripcion coherente. Se conservan
         * para leer versiones antiguas y poder revalidar planes historicos con
         * las reglas con las que se generaron.
         */
        Map<String, RepRange> byGoal,

        /**
         * V18, principios P-1.0: limite amplio que el backend verifica. Las
         * repeticiones las decide la IA por ejercicio; esto solo rechaza lo
         * absurdo. max_reps = 30 es provisional y sin referencia, hasta tener
         * limites por categoria de ejercicio.
         */
        RepLimits repLimits,

        /**
         * Subida maxima del peso SUGERIDO sobre el ultimo peso usado. 10 % es el
         * tope del rango 2-10 % de ACSM (2009), condicionado a cumplir lo pedido.
         * Sin clave en la configuracion se usa 10.
         */
        Integer maxLoadIncreasePct
) {
    public record RepRange(Integer minReps, Integer maxReps) {}

    public record RepLimits(Integer minReps, Integer maxReps) {
        public boolean isComplete() {
            return minReps != null && maxReps != null;
        }
    }

    /** Solo configuraciones anteriores a MVP-1.5. Sin rango declarado no se valida. */
    public RepRange forGoal(String goalType) {
        return byGoal == null ? null : byGoal.get(goalType);
    }

    public int floorPct() {
        return sessionMinutesFloorPct == null ? 70 : sessionMinutesFloorPct;
    }

    // Los valores por defecto replican MVP-1.2. Existen para que una
    // configuracion antigua sin el bloque de duracion no deje al estimador sin
    // parametros: se estima igual y queda registrado con que version se hizo.
    public int secondsPerRepOrDefault()      { return secondsPerRep      == null ? 3  : secondsPerRep; }
    public int transitionSecondsOrDefault()  { return transitionSeconds  == null ? 60 : transitionSeconds; }
    public int warmupMinutesOrDefault()      { return warmupMinutes      == null ? 5  : warmupMinutes; }
    public int defaultRestSecondsOrDefault() { return defaultRestSeconds == null ? 60 : defaultRestSeconds; }
    public int maxLoadIncreasePctOrDefault() {
        return maxLoadIncreasePct == null ? 10 : maxLoadIncreasePct;
    }

    public int durationTolerancePctOrDefault() {
        return durationTolerancePct == null ? 20 : durationTolerancePct;
    }
}