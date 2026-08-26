package main.web.services.fitsense.planning.infrastructure.scheduling;

import main.web.services.fitsense.planning.domain.model.commands.ExpireOverdueWorkoutsCommand;
import main.web.services.fitsense.planning.domain.services.WeeklyTrainingPlanCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Primer punto de la tarea diaria de la seccion 22: marcar SKIPPED los
 * entrenamientos vencidos que no tuvieron sesion.
 * <p>
 * Corre cada hora en vez de una vez al dia porque expires_at ya esta en hora
 * local de cada usuario: la consulta compara instantes, asi que no hace falta
 * iterar por zona horaria. Basta con pasar seguido para que ningun vencimiento
 * espere mas de una hora.
 * <p>
 * Sin esta tarea, un entrenamiento no hecho se quedaria SCHEDULED para siempre y
 * el cierre semanal no podria distinguir "no lo hizo" de "todavia puede hacerlo".
 */
@Component
public class OverdueWorkoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueWorkoutScheduler.class);

    private final WeeklyTrainingPlanCommandService commandService;

    public OverdueWorkoutScheduler(WeeklyTrainingPlanCommandService commandService) {
        this.commandService = commandService;
    }

    @Scheduled(cron = "0 30 * * * *")
    public void expireOverdue() {
        try {
            int expired = commandService.handle(new ExpireOverdueWorkoutsCommand(OffsetDateTime.now()));
            if (expired > 0) log.info("Tarea de vencimiento: {} entrenamientos cerrados", expired);
        } catch (RuntimeException e) {
            // Una tarea programada que revienta deja de ejecutarse en silencio.
            log.error("Fallo la tarea de vencimiento de entrenamientos: {}", e.getMessage(), e);
        }
    }
}
