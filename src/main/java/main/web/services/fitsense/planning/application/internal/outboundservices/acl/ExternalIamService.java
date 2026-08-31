package main.web.services.fitsense.planning.application.internal.outboundservices.acl;

import main.web.services.fitsense.iam.interfaces.acl.IamContextFacade;
import org.springframework.stereotype.Service;

import java.time.ZoneId;

/**
 * Capa anticorrupcion hacia IAM. Planning no conoce el agregado User: solo
 * pregunta si puede generarle un plan y en que zona horaria vive, que es lo que
 * define el vencimiento de cada entrenamiento.
 */
@Service("planningExternalIamService")
public class ExternalIamService {

    /** Zona del estudio. Un fallback al reloj del servidor corromperia la adherencia en silencio. */
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("America/Lima");

    private final IamContextFacade iamContextFacade;

    public ExternalIamService(IamContextFacade iamContextFacade) {
        this.iamContextFacade = iamContextFacade;
    }

    public boolean isEligibleForPlanGeneration(Long userId) {
        return iamContextFacade.isEligibleForPlanGeneration(userId);
    }

    public ZoneId timezoneOf(Long userId) {
        return iamContextFacade.fetchTimezone(userId).orElse(FALLBACK_ZONE);
    }
}
