package main.web.services.fitsense.planning.domain.model.valueobjects;

import java.util.List;

/**
 * Debe coincidir con ck_pw_focus. Las partes corporales de cada enfoque son las
 * de la tabla 20.2 del diseno, literal.
 */
public enum WorkoutFocus {
    FULL_BODY(List.of("chest", "back", "upper legs", "shoulders", "waist")),
    UPPER_BODY(List.of("chest", "back", "shoulders", "upper arms")),
    LOWER_BODY(List.of("upper legs", "lower legs", "waist")),
    PUSH(List.of("chest", "shoulders", "upper arms")),
    PULL(List.of("back", "upper arms")),
    LEGS(List.of("upper legs", "lower legs")),
    CORE(List.of("waist"));

    private final List<String> bodyPartCodes;

    WorkoutFocus(List<String> bodyPartCodes) {
        this.bodyPartCodes = bodyPartCodes;
    }

    public List<String> bodyPartCodes() {
        return bodyPartCodes;
    }

    public boolean accepts(String bodyPartCode) {
        return bodyPartCode != null && bodyPartCodes.contains(bodyPartCode);
    }
}
