package main.web.services.fitsense.analytics.application.internal.outboundservices.acl;

import main.web.services.fitsense.configuration.interfaces.acl.ActiveConfiguration;
import main.web.services.fitsense.configuration.interfaces.acl.ConfigurationContextFacade;
import org.springframework.stereotype.Service;

/** Capa anticorrupcion hacia configuration: umbrales y version del calculo. */
@Service("analyticsExternalConfigurationService")
public class ExternalConfigurationService {

    private final ConfigurationContextFacade configurationContextFacade;

    public ExternalConfigurationService(ConfigurationContextFacade configurationContextFacade) {
        this.configurationContextFacade = configurationContextFacade;
    }

    public ActiveConfiguration fetchActive() {
        return configurationContextFacade.fetchActive();
    }
}
