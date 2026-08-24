package main.web.services.fitsense.iam.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.OffsetDateTime;

/**
 * Participacion en el estudio. El consentimiento son dos columnas y no una
 * tabla porque se otorga una sola vez, en el registro.
 *
 * @param studyParticipant false para las cuentas de prueba del equipo. Toda
 *                         consulta de analisis filtra por true.
 * @param withdrawnAt      retiro voluntario: se detiene la generacion de planes
 *                         nuevos, no se borra lo ya recolectado.
 */
@Embeddable
public record StudyEnrollment(
        @Column(name = "consent_granted_at") OffsetDateTime consentGrantedAt,
        @Column(name = "consent_version", length = 20) String consentVersion,
        @Column(name = "is_study_participant", nullable = false) boolean studyParticipant,
        @Column(name = "participant_code", length = 20) String participantCode,
        @Column(name = "enrolled_at") OffsetDateTime enrolledAt,
        @Column(name = "withdrawn_at") OffsetDateTime withdrawnAt
) {
    /** Cuenta interna del equipo: no entra en el analisis. */
    public static StudyEnrollment notParticipating() {
        return new StudyEnrollment(null, null, false, null, null, null);
    }

    public static StudyEnrollment enrolledNow(String consentVersion, String participantCode) {
        var now = OffsetDateTime.now();
        return new StudyEnrollment(now, consentVersion, true, participantCode, now, null);
    }

    public StudyEnrollment withdraw() {
        if (withdrawnAt != null) return this;
        return new StudyEnrollment(consentGrantedAt, consentVersion, studyParticipant,
                participantCode, enrolledAt, OffsetDateTime.now());
    }

    /** Un participante retirado no recibe planes nuevos. */
    public boolean isActiveParticipant() {
        return studyParticipant && consentGrantedAt != null && withdrawnAt == null;
    }
}
