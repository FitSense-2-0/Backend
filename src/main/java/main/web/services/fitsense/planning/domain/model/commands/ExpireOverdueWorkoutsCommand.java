package main.web.services.fitsense.planning.domain.model.commands;

import java.time.OffsetDateTime;

/**
 * Tarea diaria de 00:30 (seccion 22): marca SKIPPED los entrenamientos vencidos
 * que no tuvieron sesion.
 * <p>
 * Sin esto, un entrenamiento no hecho se quedaria SCHEDULED para siempre y el
 * cierre semanal no sabria distinguir "no lo hizo" de "todavia puede hacerlo".
 * El skip_reason queda NULL: se le pregunta al usuario despues, y OTHER seria
 * una respuesta inventada que contaminaria el modificador de 18.3.
 */
public record ExpireOverdueWorkoutsCommand(OffsetDateTime now) {}
