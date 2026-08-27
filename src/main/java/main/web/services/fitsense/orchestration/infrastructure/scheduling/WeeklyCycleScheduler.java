package main.web.services.fitsense.orchestration.infrastructure.scheduling;

import main.web.services.fitsense.iam.interfaces.acl.IamContextFacade;
import main.web.services.fitsense.orchestration.application.internal.WeeklyCycleService;
import main.web.services.fitsense.shared.domain.model.valueobjects.TrainingWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Dispara el ciclo semanal los lunes a las 03:00 hora LOCAL de cada participante.
 * <p>
 * Corre cada hora y pregunta que zonas horarias tienen participantes activos, en
 * vez de un unico cron sobre el reloj del servidor. Con participantes en zonas
 * distintas, un cron global cerraria la semana de unos el domingo por la noche y
 * la de otros el lunes por la tarde, y la adherencia dejaria de ser comparable
 * entre ellos.
 * <p>
 * Las 03:00 y no medianoche: deja margen para las sesiones que empiezan tarde el
 * domingo y para la tarea de vencimiento de las 00:30.
 */
@Component
public class WeeklyCycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyCycleScheduler.class);
    private static final int CLOSING_HOUR = 3;

    private final IamContextFacade iamContextFacade;
    private final WeeklyCycleService weeklyCycleService;

    public WeeklyCycleScheduler(IamContextFacade iamContextFacade,
                                WeeklyCycleService weeklyCycleService) {
        this.iamContextFacade = iamContextFacade;
        this.weeklyCycleService = weeklyCycleService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void runHourly() {
        for (String zoneId : iamContextFacade.fetchActiveTimezones()) {
            ZonedDateTime localNow;
            try {
                localNow = ZonedDateTime.now(ZoneId.of(zoneId));
            } catch (RuntimeException e) {
                log.warn("Zona horaria invalida en la base: {}", zoneId);
                continue;
            }

            if (localNow.getDayOfWeek() != DayOfWeek.MONDAY) continue;
            if (localNow.getHour() != CLOSING_HOUR) continue;

            runForZone(zoneId, localNow.toLocalDate());
        }
    }

    private void runForZone(String zoneId, LocalDate localToday) {
        var newWeek = TrainingWeek.containing(localToday);
        var measuredWeek = newWeek.previous();

        var participants = iamContextFacade.fetchActiveParticipantIdsInTimezone(zoneId);
        log.info("Cierre semanal en {}: {} participantes, semana medida {}",
                zoneId, participants.size(), measuredWeek.startDate());

        for (Long userId : participants) {
            try {
                weeklyCycleService.runFor(userId, measuredWeek.startDate(), newWeek.startDate());
            } catch (RuntimeException e) {
                // Aislar por participante: un perfil incompleto o un catalogo sin
                // material no puede dejar sin plan al resto de la cohorte.
                log.error("Fallo el ciclo semanal del usuario {}: {}", userId, e.getMessage(), e);
            }
        }
    }
}
