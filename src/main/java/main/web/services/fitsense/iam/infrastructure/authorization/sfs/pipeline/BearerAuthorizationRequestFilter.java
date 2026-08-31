package main.web.services.fitsense.iam.infrastructure.authorization.sfs.pipeline;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import main.web.services.fitsense.iam.application.internal.outboundservices.tokens.TokenService;
import main.web.services.fitsense.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import main.web.services.fitsense.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro que extrae el token Bearer de la peticion, lo valida y pobla el
 * SecurityContext con el usuario autenticado.
 *
 * La extraccion del header se delega a TokenService.getBearerTokenFrom para
 * no duplicar el formato del header en dos lugares.
 *
 * NOTA: no lleva @Component a proposito. Se instancia manualmente en
 * WebSecurityConfiguration para evitar el registro automatico de Spring Boot
 * en la cadena de filtros del servlet, que provocaria una doble ejecucion.
 */
public class BearerAuthorizationRequestFilter extends OncePerRequestFilter {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(BearerAuthorizationRequestFilter.class);

    private final TokenService tokenService;
    private final UserRepository userRepository;

    public BearerAuthorizationRequestFilter(TokenService tokenService,
                                            UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            var token = tokenService.getBearerTokenFrom(request);

            if (token != null && tokenService.validateToken(token)) {
                var userId = tokenService.extractUserId(token);

                userRepository.findById(userId).ifPresent(user -> {
                    // El principal debe ser UserDetailsImpl, no el User del
                    // dominio: es el tipo que los controladores reciben con
                    // @AuthenticationPrincipal. Con el agregado directo, Spring
                    // da la peticion por autenticada y la deja pasar, pero
                    // inyecta null en el controlador, que revienta con NPE en
                    // vez de devolver un 401 limpio.
                    var principal = UserDetailsImpl.build(user);

                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
        } catch (Exception exception) {
            // No propagamos: un token invalido debe terminar en 401 via el
            // AuthenticationEntryPoint, no en un 500. Limpiamos el contexto
            // para que ninguna autenticacion parcial sobreviva.
            LOGGER.warn("No se pudo establecer la autenticacion: {}",
                    exception.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}