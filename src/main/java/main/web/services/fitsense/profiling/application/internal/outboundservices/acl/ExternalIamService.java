package main.web.services.fitsense.profiling.application.internal.outboundservices.acl;

import main.web.services.fitsense.iam.interfaces.acl.IamContextFacade;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Optional;

/**
 * Capa anticorrupcion hacia IAM. El contexto de perfil no conoce el agregado
 * User ni su repositorio: solo pregunta lo que necesita.
 */
@Service("profilingExternalIamService")
public class ExternalIamService {

    private final IamContextFacade iamContextFacade;

    public ExternalIamService(IamContextFacade iamContextFacade) {
        this.iamContextFacade = iamContextFacade;
    }

    public boolean userExists(Long userId) {
        return iamContextFacade.existsUser(userId);
    }

    public Optional<ZoneId> fetchTimezone(Long userId) {
        return iamContextFacade.fetchTimezone(userId);
    }
}
