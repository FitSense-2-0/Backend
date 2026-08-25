package main.web.services.fitsense.configuration.interfaces.acl;

import main.web.services.fitsense.configuration.domain.model.valueobjects.CalculationParams;

/**
 * La version y los parametros siempre viajan juntos: toda metrica y toda
 * intervencion deben registrar con que version se calcularon, o el analisis
 * posterior no puede separar el efecto del ajuste del efecto de la calibracion.
 */
public record ActiveConfiguration(String version, CalculationParams params) {}
