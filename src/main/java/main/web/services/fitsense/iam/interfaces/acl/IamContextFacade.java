package main.web.services.fitsense.iam.interfaces.acl;

import main.web.services.fitsense.iam.domain.model.queries.GetUserByIdQuery;
import main.web.services.fitsense.iam.domain.services.UserQueryService;
import main.web.services.fitsense.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Unico punto de entrada al contexto IAM desde otros contextos. Ninguno debe
 * inyectar UserRepository ni el agregado User directamente.
 */
@Service
public class IamContextFacade {

    private final UserQueryService userQueryService;
    private final UserRepository userRepository;

    public IamContextFacade(UserQueryService userQueryService, UserRepository userRepository) {
        this.userQueryService = userQueryService;
        this.userRepository = userRepository;
    }

    public boolean existsUser(Long userId) {
        return userRepository.existsById(userId);
    }

    /** Zona horaria del usuario: define el cierre semanal y las fechas locales. */
    @Transactional(readOnly = true)
    public Optional<ZoneId> fetchTimezone(Long userId) {
        return userQueryService.handle(new GetUserByIdQuery(userId))
                .map(user -> user.getTimezone().zoneId());
    }

    @Transactional(readOnly = true)
    public boolean isEligibleForPlanGeneration(Long userId) {
        return userQueryService.handle(new GetUserByIdQuery(userId))
                .map(user -> user.isEligibleForPlanGeneration())
                .orElse(false);
    }

    /** Zonas horarias distintas: la tarea semanal itera por zona, no por reloj del servidor. */
    @Transactional(readOnly = true)
    public List<String> fetchActiveTimezones() {
        return userRepository.findDistinctActiveTimezones();
    }

    /** Participantes activos de una zona: los que reciben plan en el cierre. */
    @Transactional(readOnly = true)
    public List<Long> fetchActiveParticipantIdsInTimezone(String zone) {
        return userRepository.findActiveParticipantsInTimezone(zone).stream()
                .map(user -> user.getId())
                .toList();
    }
}
