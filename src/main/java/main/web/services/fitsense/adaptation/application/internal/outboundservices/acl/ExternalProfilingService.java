package main.web.services.fitsense.adaptation.application.internal.outboundservices.acl;

import main.web.services.fitsense.profiling.interfaces.acl.ProfileSnapshot;
import main.web.services.fitsense.profiling.interfaces.acl.ProfilingContextFacade;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Capa anticorrupcion hacia profiling. La adaptacion necesita la linea base del
 * participante (dias, minutos, nivel) para saber desde donde reduce.
 */
@Service("adaptationExternalProfilingService")
public class ExternalProfilingService {

    private final ProfilingContextFacade profilingContextFacade;

    public ExternalProfilingService(ProfilingContextFacade profilingContextFacade) {
        this.profilingContextFacade = profilingContextFacade;
    }

    public Optional<ProfileSnapshot> fetchProfile(Long userId) {
        return profilingContextFacade.fetchProfile(userId);
    }
}
