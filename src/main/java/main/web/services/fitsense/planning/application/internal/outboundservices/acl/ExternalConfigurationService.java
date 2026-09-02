package main.web.services.fitsense.planning.application.internal.outboundservices.acl;

import main.web.services.fitsense.configuration.domain.model.valueobjects.PrescriptionParams;
import main.web.services.fitsense.configuration.interfaces.acl.ConfigurationContextFacade;
import org.springframework.stereotype.Service;

/**
 * Capa anticorrupcion hacia configuration. Planning solo necesita el divisor de
 * 18.1 para calcular volumen; los umbrales de adherencia son asunto de analytics.
 */
@Service("planningExternalConfigurationService")
public class ExternalConfigurationService {

    private static final int DEFAULT_DIVISOR = 30;

    private final ConfigurationContextFacade configurationContextFacade;

    public ExternalConfigurationService(ConfigurationContextFacade configurationContextFacade) {
        this.configurationContextFacade = configurationContextFacade;
    }

    public int durationToRepsDivisor() {
        var divisor = configurationContextFacade.fetchActive().params()
                .adjustment().durationToRepsDivisor();
        return divisor == null || divisor <= 0 ? DEFAULT_DIVISOR : divisor;
    }

    /**
     * Rangos de prescripcion por objetivo (V12). Null si la configuracion activa
     * es anterior y no trae el bloque: en ese caso las validaciones 16 y 17 se
     * saltan, que es preferible a inventar limites.
     */
    public PrescriptionParams prescriptionParams() {
        return configurationContextFacade.fetchActive().params().prescription();
    }
}
