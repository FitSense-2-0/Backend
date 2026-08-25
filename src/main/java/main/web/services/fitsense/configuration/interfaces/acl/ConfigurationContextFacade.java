package main.web.services.fitsense.configuration.interfaces.acl;

import main.web.services.fitsense.configuration.domain.exceptions.NoActiveConfigurationException;
import main.web.services.fitsense.configuration.domain.model.queries.GetActiveCalculationConfigQuery;
import main.web.services.fitsense.configuration.domain.model.valueobjects.CalculationParams;
import main.web.services.fitsense.configuration.domain.services.CalculationConfigQueryService;
import main.web.services.fitsense.shared.infrastructure.json.JsonSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unico punto de entrada a la configuracion. Lo consumen analytics (umbrales de
 * adherencia y riesgo) y adaptation (tabla de decision y pisos).
 * <p>
 * No cachea: la fila es una y la consulta es por indice unico parcial. Cachear
 * introduciria el riesgo de que dos usuarios de la misma semana se calculen con
 * versiones distintas si alguien cambia la fila activa a mitad del cierre.
 */
@Service
public class ConfigurationContextFacade {

    private final CalculationConfigQueryService calculationConfigQueryService;
    private final JsonSupport jsonSupport;

    public ConfigurationContextFacade(CalculationConfigQueryService calculationConfigQueryService,
                                      JsonSupport jsonSupport) {
        this.calculationConfigQueryService = calculationConfigQueryService;
        this.jsonSupport = jsonSupport;
    }

    @Transactional(readOnly = true)
    public ActiveConfiguration fetchActive() {
        var config = calculationConfigQueryService.handle(new GetActiveCalculationConfigQuery())
                .orElseThrow(NoActiveConfigurationException::new);

        var params = jsonSupport.read(config.getParams(), CalculationParams.class);
        params.requireComplete();
        return new ActiveConfiguration(config.getVersion(), params);
    }
}
