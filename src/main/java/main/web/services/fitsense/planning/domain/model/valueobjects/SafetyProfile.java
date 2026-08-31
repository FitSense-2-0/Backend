package main.web.services.fitsense.planning.domain.model.valueobjects;

/**
 * Restricciones de seguridad derivadas de la edad del participante.
 * <p>
 * Se calculan aqui y no en el perfil porque son una politica del generador, no
 * un dato del usuario: si manana se decide que el corte de impacto son 55 anos
 * y no 60, cambia esta clase y no hay que migrar ningun perfil.
 * <p>
 * POR QUE EXISTE
 * El conjunto elegible filtraba por equipamiento, ubicacion, dificultad y
 * bloqueos, pero no por edad. Nivel de condicion fisica y edad no son lo mismo:
 * una persona de 68 anos puede ser INTERMEDIATE y aun asi no convenirle un
 * burpee.
 * <p>
 * Y no es solo seguridad. Si el plan no encaja con la persona, la adherencia
 * baja, y el sistema interpreta esa caida como falta de compromiso: reduce
 * volumen cuando el problema era la seleccion. El ajuste responde a la senal
 * equivocada.
 * <p>
 * ATENCION: los cortes de edad son criterio conservador, no una guia clinica
 * validada. Conviene contrastarlos con el asesor antes del piloto.
 */
public record SafetyProfile(
        boolean excludeHighImpact,
        boolean excludeFloorWork,
        boolean excludeAxialLoad
) {

    /** Desde esta edad se retiran saltos, pliometria y levantamientos olimpicos. */
    public static final int EDAD_SIN_IMPACTO = 60;

    /** Desde esta edad se retira tambien lo que exige bajar al suelo y levantarse. */
    public static final int EDAD_SIN_SUELO = 70;

    /** Desde esta edad se retira ademas la carga axial y los invertidos. */
    public static final int EDAD_SIN_CARGA_AXIAL = 65;

    public static SafetyProfile forAge(int age) {
        return new SafetyProfile(
                age >= EDAD_SIN_IMPACTO,
                age >= EDAD_SIN_SUELO,
                age >= EDAD_SIN_CARGA_AXIAL);
    }

    public static SafetyProfile none() {
        return new SafetyProfile(false, false, false);
    }

    public boolean hasRestrictions() {
        return excludeHighImpact || excludeFloorWork || excludeAxialLoad;
    }

    /**
     * Texto para el prompt. El filtro ya retiro los ejercicios, pero decirselo
     * a la IA evita que proponga variantes agresivas de los que si quedan: un
     * "wall sit" profundo o una sentadilla completa siguen siendo elegibles y
     * el modelo puede moderar la prescripcion.
     */
    public String describe() {
        if (!hasRestrictions()) return null;
        var partes = new StringBuilder("El participante tiene restricciones de seguridad por edad. ");
        if (excludeHighImpact)
            partes.append("Evita cualquier movimiento explosivo o de impacto, aunque el ejercicio lo permita. ");
        if (excludeAxialLoad)
            partes.append("Evita cargar peso sobre la columna y las posiciones invertidas. ");
        if (excludeFloorWork)
            partes.append("Evita ejercicios que obliguen a bajar al suelo y levantarse. ");
        partes.append("Prefiere rangos de movimiento moderados y progresion conservadora.");
        return partes.toString();
    }
}
