package main.web.services.fitsense.planning.application.internal.outboundservices.acl;

import main.web.services.fitsense.configuration.interfaces.acl.ConfigurationContextFacade;
import org.springframework.stereotype.Service;

/**
 * Capa anticorrupcion hacia configuration. Planning solo necesita el divisor de
 * 18.1 para calcular volumen; los umbrales de adherencia son asunto de analytics.
 */
@Service
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
}
