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
        Integer maxLoadIncreasePct,

        /**
         * V20 (config): descanso maximo entre series. 180 s es el tope de los
         * 2-3 min que ACSM (2009) indica para los ejercicios multiarticulares
         * pesados. Evita que la duracion minima se cumpla inflando descansos en
         * vez de con trabajo. Sin clave se usa 180.
         */
        Integer maxRestSeconds
) {
    public record RepRange(Integer minReps, Integer maxReps) {}

    /**
     * @param minRepsByBodyPart V20: minimo PROVISIONAL por body_part (gemelos 12,
     *                          abdomen 10). Decision de diseno sin referencia:
     *                          aproxima la exigencia de cada ejercicio hasta que
     *                          el catalogo este clasificado por categoria. Solo
     *                          aplica a SETS_REPS.
     */
    public record RepLimits(Integer minReps, Integer maxReps, Map<String, Integer> minRepsByBodyPart) {
        public boolean isComplete() {
            return minReps != null && maxReps != null;
        }

        /** Minimo efectivo para un ejercicio: el de su body_part si es mayor que el general. */
        public int minRepsFor(String bodyPartCode) {
            int general = minReps == null ? 0 : minReps;
            if (minRepsByBodyPart == null || bodyPartCode == null) return general;
            Integer zona = minRepsByBodyPart.get(bodyPartCode);
            return zona == null ? general : Math.max(general, zona);
        }

        /**
         * Piso de repeticiones para principiantes: 8. ACSM (2009) recomienda
         * 8-12 repeticiones para principiantes; usar el extremo inferior como
         * piso es criterio de diseno. Lo respetan la IA (V16), el reductor de
         * volumen y el motor de reglas, para que 3x6 no aparezca en un plan de
         * principiante.
         */
        public static final int MIN_REPS_BEGINNER = 8;

        /** Minimo efectivo segun zona Y nivel. */
        public int minRepsFor(String bodyPartCode, String fitnessLevel) {
            int base = minRepsFor(bodyPartCode);
            return "BEGINNER".equals(fitnessLevel) ? Math.max(base, MIN_REPS_BEGINNER) : base;
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

    public int maxRestSecondsOrDefault() {
        return maxRestSeconds == null ? 180 : maxRestSeconds;
    }

    public int durationTolerancePctOrDefault() {
        return durationTolerancePct == null ? 20 : durationTolerancePct;
    }
}