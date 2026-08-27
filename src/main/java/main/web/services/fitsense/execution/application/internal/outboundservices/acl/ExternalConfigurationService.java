package main.web.services.fitsense.execution.application.internal.outboundservices.acl;

import main.web.services.fitsense.configuration.interfaces.acl.ConfigurationContextFacade;
import main.web.services.fitsense.execution.domain.model.valueobjects.CompletionThresholds;
import org.springframework.stereotype.Service;

/** Capa anticorrupcion hacia configuration: los umbrales de 17.1 y 17.2. */
@Service
public class ExternalConfigurationService {

    private final ConfigurationContextFacade configurationContextFacade;

    public ExternalConfigurationService(ConfigurationContextFacade configurationContextFacade) {
        this.configurationContextFacade = configurationContextFacade;
    }

    public CompletionThresholds thresholds() {
        var adherence = configurationContextFacade.fetchActive().params().adherence();
        return new CompletionThresholds(
                adherence.sessionCompletedThresholdPct(),
                adherence.sessionValidThresholdPct(),
                adherence.exerciseCompletedThresholdPct(),
                adherence.completionCapPct());
    }
}
