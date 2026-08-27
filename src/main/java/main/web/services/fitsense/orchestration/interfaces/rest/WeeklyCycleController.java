package main.web.services.fitsense.orchestration.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import main.web.services.fitsense.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import main.web.services.fitsense.orchestration.application.internal.WeeklyCycleService;
import main.web.services.fitsense.shared.domain.model.valueobjects.TrainingWeek;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ejecuta el ciclo semanal del usuario autenticado sin esperar al lunes.
 * <p>
 * Existe para poder probar el flujo completo durante el desarrollo y la
 * demostracion. Actua solo sobre quien llama, nunca sobre la cohorte: un disparo
 * global desde la API podria alterar los datos del estudio.
 */
@RestController
@RequestMapping(value = "/api/v1/cycle", produces = "application/json")
@Tag(name = "Ciclo semanal", description = "Disparo manual del cierre y la regeneracion")
public class WeeklyCycleController {

    private final WeeklyCycleService weeklyCycleService;

    public WeeklyCycleController(WeeklyCycleService weeklyCycleService) {
        this.weeklyCycleService = weeklyCycleService;
    }

    @PostMapping("/run-for-me")
    @Operation(summary = "Cierra la semana anterior, calcula metricas, decide el ajuste y genera el plan")
    public ResponseEntity<Map<String, Object>> runForMe(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        var newWeek = TrainingWeek.containing(LocalDate.now());
        var measuredWeek = newWeek.previous();

        var planId = weeklyCycleService.runFor(principal.getUserId(),
                measuredWeek.startDate(), newWeek.startDate());

        var response = new LinkedHashMap<String, Object>();
        response.put("measuredWeekStart", measuredWeek.startDate().toString());
        response.put("newWeekStart", newWeek.startDate().toString());
        response.put("generatedPlanId", planId.orElse(null));
        return ResponseEntity.ok(response);
    }
}
