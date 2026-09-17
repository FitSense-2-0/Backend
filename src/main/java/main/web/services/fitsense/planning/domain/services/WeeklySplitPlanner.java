package main.web.services.fitsense.planning.domain.services;

import main.web.services.fitsense.planning.domain.model.valueobjects.WorkoutFocus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Division semanal de la rutina: que fechas se entrena y con que enfoque.
 * <p>
 * Sale de tres principios, en este orden de prioridad:
 * <ol>
 *   <li>P1 Recuperacion: 48 h antes de volver a trabajar pecho, espalda,
 *       hombros o piernas (ACSM, Garber et al. 2011).</li>
 *   <li>P2 Frecuencia: cada grupo grande 2 veces por semana cuando los dias lo
 *       permiten sin romper P1 (ACSM 2026 pide 2 o mas sesiones semanales;
 *       aplicarlo por grupo es criterio de diseno).</li>
 *   <li>P3 Simplicidad: la division mas simple que cumpla P1 y P2. Cuerpo
 *       completo antes que superior/inferior, y esa antes que
 *       empuje/traccion/piernas (ACSM 2009: cuerpo completo para
 *       principiantes, divisiones con experiencia).</li>
 * </ol>
 * Si P1 y P2 chocan, gana P1.
 * <p>
 * El tope de sesiones por nivel (principiante 4, resto 6) lo aplica
 * PlanGenerationContext.effectiveDaysPerWeek, antes de llegar aqui.
 * <p>
 * Lo usan el motor de reglas (tal cual), el snapshot de la IA (como
 * suggested_split) y el validador (para decirle a la IA que enfoque usar en cada
 * fecha cuando se equivoca). Una sola fuente para los tres.
 */
public final class WeeklySplitPlanner {

    public record PlannedSession(LocalDate date, WorkoutFocus focus) {}

    private WeeklySplitPlanner() {}

    public static List<PlannedSession> plan(LocalDate weekStart, List<Short> availableDays,
                                            int sessions, int weekNumber) {
        var dates = chooseDates(weekStart, availableDays, sessions);
        var focuses = focusesFor(dates, weekNumber);
        var result = new ArrayList<PlannedSession>();
        for (int i = 0; i < dates.size(); i++) result.add(new PlannedSession(dates.get(i), focuses.get(i)));
        return List.copyOf(result);
    }

    static List<WorkoutFocus> focusesFor(List<LocalDate> dates, int weekNumber) {
        int n = dates.size();
        boolean consecutive = hasConsecutive(dates);
        return switch (n) {
            case 0 -> List.of();
            case 1 -> List.of(WorkoutFocus.FULL_BODY);
            // P3 con dias separados; P1 obliga a dividir si hay dias pegados.
            case 2 -> consecutive
                    ? List.of(WorkoutFocus.UPPER_BODY, WorkoutFocus.LOWER_BODY)
                    : List.of(WorkoutFocus.FULL_BODY, WorkoutFocus.FULL_BODY);
            // Con dias pegados, superior/inferior/superior da 2 veces al tren
            // superior (P2) y la semana siguiente se invierte para equilibrar.
            case 3 -> !consecutive
                    ? List.of(WorkoutFocus.FULL_BODY, WorkoutFocus.FULL_BODY, WorkoutFocus.FULL_BODY)
                    : (weekNumber % 2 == 1
                    ? List.of(WorkoutFocus.UPPER_BODY, WorkoutFocus.LOWER_BODY, WorkoutFocus.UPPER_BODY)
                    : List.of(WorkoutFocus.LOWER_BODY, WorkoutFocus.UPPER_BODY, WorkoutFocus.LOWER_BODY));
            case 4 -> List.of(WorkoutFocus.UPPER_BODY, WorkoutFocus.LOWER_BODY,
                    WorkoutFocus.UPPER_BODY, WorkoutFocus.LOWER_BODY);
            case 5 -> List.of(WorkoutFocus.PUSH, WorkoutFocus.PULL, WorkoutFocus.LEGS,
                    WorkoutFocus.UPPER_BODY, WorkoutFocus.LOWER_BODY);
            default -> {
                var six = new ArrayList<WorkoutFocus>();
                WorkoutFocus[] ppl = {WorkoutFocus.PUSH, WorkoutFocus.PULL, WorkoutFocus.LEGS};
                for (int i = 0; i < n; i++) six.add(ppl[i % 3]);
                yield List.copyOf(six);
            }
        };
    }

    public static boolean hasConsecutive(List<LocalDate> dates) {
        for (int i = 1; i < dates.size(); i++)
            if (dates.get(i - 1).plusDays(1).equals(dates.get(i))) return true;
        return false;
    }

    /**
     * Primeros "sessions" dias de available_days, separando los consecutivos
     * cuando la lista lo permite: con 3 sesiones sobre lunes a sabado salen
     * lunes, miercoles y viernes, y P3 puede usar cuerpo completo.
     */
    static List<LocalDate> chooseDates(LocalDate weekStart, List<Short> availableDays, int sessions) {
        var sorted = availableDays.stream().distinct().sorted().toList();
        if (sessions <= 0 || sorted.isEmpty()) return List.of();
        if (sessions >= sorted.size())
            return sorted.stream().map(day -> weekStart.plusDays(day - 1L)).toList();

        var chosen = new ArrayList<Short>();
        double step = (double) sorted.size() / sessions;
        for (int i = 0; i < sessions; i++) {
            int index = Math.min((int) Math.round(i * step), sorted.size() - 1);
            short day = sorted.get(index);
            if (!chosen.contains(day)) chosen.add(day);
        }
        for (short day : sorted) {
            if (chosen.size() >= sessions) break;
            if (!chosen.contains(day)) chosen.add(day);
        }
        return chosen.stream().sorted().map(day -> weekStart.plusDays(day - 1L)).toList();
    }

    /** "2026-09-14 UPPER_BODY, 2026-09-15 LOWER_BODY" para mensajes de validacion. */
    public static String describe(List<PlannedSession> split) {
        return String.join(", ", split.stream().map(s -> s.date() + " " + s.focus()).toList());
    }
}