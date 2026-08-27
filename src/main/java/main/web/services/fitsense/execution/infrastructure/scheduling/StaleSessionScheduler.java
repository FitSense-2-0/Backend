package main.web.services.fitsense.execution.infrastructure.scheduling;

import main.web.services.fitsense.execution.domain.model.valueobjects.SessionStatus;
import main.web.services.fitsense.execution.infrastructure.persistence.jpa.repositories.WorkoutSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Segundo punto de la tarea diaria de la seccion 22: cerrar como ABANDONED las
 * sesiones IN_PROGRESS de mas de seis horas.
 * <p>
 * Importa por ux_session_active, que solo admite una sesion en curso por
 * usuario: alguien que abrio una sesion y cerro la app quedaria bloqueado sin
 * poder empezar otra.
 * <p>
 * Se cierran como ABANDONED y no como PARTIAL: no hay evidencia de que el
 * usuario entrenara seis horas, y contarla inflaria su adherencia.
 */
@Component
public class StaleSessionScheduler {

    private static final Logger log = LoggerFactory.getLogger(StaleSessionScheduler.class);
    private static final int STALE_AFTER_HOURS = 6;

    private final WorkoutSessionRepository sessionRepository;

    public StaleSessionScheduler(WorkoutSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Scheduled(cron = "0 35 * * * *")
    @Transactional
    public void abandonStaleSessions() {
        try {
            var cutoff = OffsetDateTime.now().minusHours(STALE_AFTER_HOURS);
            var stale = sessionRepository.findByStatusAndStartedAtBefore(
                    SessionStatus.IN_PROGRESS, cutoff);

            stale.forEach(session -> session.abandon());
            sessionRepository.saveAll(stale);

            if (!stale.isEmpty())
                log.info("Tarea de sesiones colgadas: {} cerradas como ABANDONED", stale.size());
        } catch (RuntimeException e) {
            log.error("Fallo la tarea de sesiones colgadas: {}", e.getMessage(), e);
        }
    }
}
