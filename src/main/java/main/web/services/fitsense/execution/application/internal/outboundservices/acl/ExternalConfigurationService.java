package main.web.services.fitsense.execution.application.internal.outboundservices.acl;

import main.web.services.fitsense.configuration.domain.model.valueobjects.PrescriptionParams;
import main.web.services.fitsense.configuration.interfaces.acl.ConfigurationContextFacade;
import main.web.services.fitsense.execution.domain.model.valueobjects.CompletionThresholds;
import org.springframework.stereotype.Service;

/** Capa anticorrupcion hacia configuration: los umbrales de 17.1 y 17.2. */
@Service("executionExternalConfigurationService")
public class ExternalConfigurationService {

    private final ConfigurationContextFacade configurationContextFacade;

    public ExternalConfigurationService(ConfigurationContextFacade configurationContextFacade) {
        this.configurationContextFacade = configurationContextFacade;
    }
    /**
     * Rangos de prescripcion por objetivo (V12). Null si la configuracion activa
     * es anterior: en ese caso las validaciones 16 y 17 no se aplican, que es
     * preferible a inventar limites.
     */
    public PrescriptionParams prescriptionParams() {
        return configurationContextFacade.fetchActive().params().prescription();
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
