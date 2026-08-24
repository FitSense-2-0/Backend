package main.web.services.fitsense.profiling.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import main.web.services.fitsense.profiling.domain.model.commands.CreateUserProfileCommand;
import main.web.services.fitsense.profiling.domain.model.commands.UpdateUserProfileCommand;
import main.web.services.fitsense.profiling.domain.model.valueobjects.*;
import main.web.services.fitsense.shared.domain.exceptions.DomainRuleViolationException;
import main.web.services.fitsense.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Todo lo que la IA necesita para generar el plan. Una fila por usuario.
 * <p>
 * Absorbe cinco tablas del diseno 2.0 (user_profiles, user_goals,
 * user_availability, user_equipment, user_training_constraints): en un MVP donde
 * la meta no cambia durante el estudio, cinco tablas producen cinco JOINs para
 * armar un objeto que siempre se lee completo.
 */
@Getter
@Entity
@Table(name = "user_profiles")
public class UserProfile extends AuditableAbstractAggregateRoot<UserProfile> {

    /** Comparte la clave con users. No hay relacion JPA: son contextos distintos. */
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "biological_sex", length = 20)
    private BiologicalSex biologicalSex;

    @Embedded
    private BodyMeasurements measurements;

    @Enumerated(EnumType.STRING)
    @Column(name = "fitness_level", nullable = false, length = 20)
    private FitnessLevel fitnessLevel;

    @Embedded
    private TrainingGoal goal;

    @Enumerated(EnumType.STRING)
    @Column(name = "training_location", nullable = false, length = 20)
    private TrainingLocation trainingLocation;

    /** Entero exacto, nunca un rango: es el denominador base de la adherencia. */
    @Column(name = "days_per_week", nullable = false)
    private Short daysPerWeek;

    /**
     * Dias concretos, 1 lunes ... 7 domingo. La IA necesita saber en que dias
     * programar, no solo cuantos.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "available_days", nullable = false, columnDefinition = "smallint[]")
    private Short[] availableDays;

    @Column(name = "session_minutes", nullable = false)
    private Short sessionMinutes;

    /** Vacio significa solo peso corporal. Reemplaza a la tabla user_equipment. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "equipment_codes", nullable = false, columnDefinition = "varchar(40)[]")
    private String[] equipmentCodes;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "blocked_exercise_ids", nullable = false, columnDefinition = "bigint[]")
    private Long[] blockedExerciseIds;

    /** Texto libre. Lo interpreta la IA, no una consulta SQL. */
    @Column(name = "health_notes", length = 500)
    private String healthNotes;

    protected UserProfile() {
        // JPA
    }

    public UserProfile(Long userId, CreateUserProfileCommand command) {
        this.userId = userId;
        this.birthDate = requireBirthDate(command.birthDate());
        this.biologicalSex = command.biologicalSex();
        this.measurements = new BodyMeasurements(
                command.heightCm(), command.currentWeightKg(), command.targetWeightKg());
        this.fitnessLevel = require(command.fitnessLevel(), "El nivel de condicion fisica es obligatorio.");
        this.goal = new TrainingGoal(command.goalType(), command.goalText());
        this.trainingLocation = require(command.trainingLocation(), "El lugar de entrenamiento es obligatorio.");
        applyAvailability(command.daysPerWeek(), command.availableDays(), command.sessionMinutes());
        this.equipmentCodes = normalizeEquipment(command.equipmentCodes());
        this.blockedExerciseIds = normalizeBlocked(command.blockedExerciseIds());
        this.healthNotes = normalizeNotes(command.healthNotes());
    }

    /**
     * Los cambios NO regeneran el plan vigente: se aplican en la generacion del
     * lunes siguiente (seccion 17.6). Regenerar en cada edicion produciria varias
     * versiones por semana y volveria la adherencia imposible de interpretar.
     */
    public void update(UpdateUserProfileCommand command) {
        this.measurements = new BodyMeasurements(
                command.heightCm(), command.currentWeightKg(), command.targetWeightKg());
        this.biologicalSex = command.biologicalSex();
        this.fitnessLevel = require(command.fitnessLevel(), "El nivel de condicion fisica es obligatorio.");
        this.goal = new TrainingGoal(command.goalType(), command.goalText());
        this.trainingLocation = require(command.trainingLocation(), "El lugar de entrenamiento es obligatorio.");
        applyAvailability(command.daysPerWeek(), command.availableDays(), command.sessionMinutes());
        this.equipmentCodes = normalizeEquipment(command.equipmentCodes());
        this.blockedExerciseIds = normalizeBlocked(command.blockedExerciseIds());
        this.healthNotes = normalizeNotes(command.healthNotes());
    }

    // ---------------------------------------------------------------- consultas

    public int age() {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    public List<Short> availableDaysAsList() {
        return Arrays.asList(availableDays);
    }

    public List<String> equipmentCodesAsList() {
        return Arrays.asList(equipmentCodes);
    }

    public List<Long> blockedExerciseIdsAsList() {
        return Arrays.asList(blockedExerciseIds);
    }

    /** Sin equipamiento declarado: solo peso corporal. */
    public boolean bodyWeightOnly() {
        return equipmentCodes.length == 0;
    }

    public int maxDifficultyLevel() {
        return fitnessLevel.maxDifficultyLevel();
    }

    /** Techo de duracion aceptado por la validacion 4: session_minutes + 15 %. */
    public int maxSessionMinutesAllowed() {
        return (int) Math.floor(sessionMinutes * 1.15);
    }

    public boolean isDayAvailable(short day) {
        for (Short available : availableDays) if (available == day) return true;
        return false;
    }

    // ------------------------------------------------------------------ helpers

    private void applyAvailability(Short daysPerWeek, List<Short> availableDays, Short sessionMinutes) {
        if (daysPerWeek == null || daysPerWeek < 1 || daysPerWeek > 7)
            throw new DomainRuleViolationException("Los dias por semana deben estar entre 1 y 7.");
        if (sessionMinutes == null || sessionMinutes < 15 || sessionMinutes > 180)
            throw new DomainRuleViolationException("La duracion por sesion debe estar entre 15 y 180 minutos.");
        if (availableDays == null || availableDays.isEmpty())
            throw new DomainRuleViolationException("Indica en que dias puedes entrenar.");

        var unique = new LinkedHashSet<Short>();
        for (Short day : availableDays) {
            if (day == null) throw new DomainRuleViolationException("Hay un dia disponible sin valor.");
            WeekDay.requireValid(day);
            unique.add(day);
        }
        if (unique.size() < daysPerWeek)
            throw new DomainRuleViolationException(
                    "Marcaste %d dias disponibles pero quieres entrenar %d veces por semana."
                            .formatted(unique.size(), daysPerWeek));

        this.daysPerWeek = daysPerWeek;
        this.sessionMinutes = sessionMinutes;
        this.availableDays = unique.stream().sorted().toArray(Short[]::new);
    }

    private static String[] normalizeEquipment(List<String> codes) {
        if (codes == null) return new String[0];
        return codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toLowerCase())
                .distinct()
                .toArray(String[]::new);
    }

    private static Long[] normalizeBlocked(List<Long> ids) {
        if (ids == null) return new Long[0];
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toArray(Long[]::new);
    }

    private static String normalizeNotes(String notes) {
        if (notes == null || notes.isBlank()) return null;
        var trimmed = notes.trim();
        if (trimmed.length() > 500)
            throw new DomainRuleViolationException("Las notas de salud no pueden exceder 500 caracteres.");
        return trimmed;
    }

    private static LocalDate requireBirthDate(LocalDate birthDate) {
        if (birthDate == null)
            throw new DomainRuleViolationException("La fecha de nacimiento es obligatoria.");
        if (!birthDate.isBefore(LocalDate.now()))
            throw new DomainRuleViolationException("La fecha de nacimiento debe ser anterior a hoy.");
        return birthDate;
    }

    private static <T> T require(T value, String message) {
        if (value == null) throw new DomainRuleViolationException(message);
        return value;
    }
}
