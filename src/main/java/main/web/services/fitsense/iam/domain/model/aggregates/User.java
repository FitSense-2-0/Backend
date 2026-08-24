package main.web.services.fitsense.iam.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import main.web.services.fitsense.iam.domain.model.commands.SignUpCommand;
import main.web.services.fitsense.iam.domain.model.valueobjects.*;
import main.web.services.fitsense.shared.domain.exceptions.DomainRuleViolationException;
import main.web.services.fitsense.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;

import java.time.OffsetDateTime;

/**
 * Raiz de agregado de la cuenta. Concentra credenciales, estado y condicion de
 * participante del estudio (tabla {@code users} del diseno, seccion 4).
 */
@Getter
@Entity
@Table(name = "users")
public class User extends AuditableAbstractAggregateRoot<User> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Embedded
    private EmailAddress email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Embedded
    private PersonName name;

    @Embedded
    private UserTimezone timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Embedded
    private StudyEnrollment enrollment;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    protected User() {
        // JPA
    }

    public User(SignUpCommand command, String passwordHash, String consentVersion, String participantCode) {
        this.email = new EmailAddress(command.email());
        this.passwordHash = passwordHash;
        this.name = new PersonName(command.firstName(), command.lastName());
        this.timezone = new UserTimezone(command.timezone());
        this.status = UserStatus.ACTIVE;
        this.enrollment = command.acceptsStudyParticipation()
                ? StudyEnrollment.enrolledNow(consentVersion, participantCode)
                : StudyEnrollment.notParticipating();
    }

    public void updateTimezone(String zone) {
        this.timezone = new UserTimezone(zone);
    }

    public void changePassword(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank())
            throw new DomainRuleViolationException("El hash de la contrasena no puede estar vacio.");
        this.passwordHash = newPasswordHash;
    }

    public void registerLogin() {
        if (!canSignIn())
            throw new DomainRuleViolationException("La cuenta no esta activa.");
        this.lastLoginAt = OffsetDateTime.now();
    }

    /**
     * Retiro voluntario del estudio. La cuenta sigue existiendo y los datos ya
     * recolectados se conservan bajo el participant_code.
     */
    public void withdrawFromStudy() {
        if (!enrollment.studyParticipant())
            throw new DomainRuleViolationException("La cuenta no participa en el estudio.");
        this.enrollment = enrollment.withdraw();
    }

    public void deactivate() {
        this.status = UserStatus.INACTIVE;
    }

    public boolean canSignIn() {
        return status == UserStatus.ACTIVE;
    }

    /** Solo un participante activo recibe planes nuevos en el cierre semanal. */
    public boolean isEligibleForPlanGeneration() {
        return canSignIn() && enrollment.isActiveParticipant();
    }

    public String emailAddress() {
        return email.address();
    }
}
