package main.web.services.fitsense.iam.infrastructure.authorization.sfs.configuration;

import main.web.services.fitsense.iam.application.internal.outboundservices.tokens.TokenService;
import main.web.services.fitsense.iam.infrastructure.authorization.sfs.pipeline.BearerAuthorizationRequestFilter;
import main.web.services.fitsense.iam.infrastructure.authorization.sfs.pipeline.UnauthorizedRequestHandlerEntryPoint;
import main.web.services.fitsense.iam.infrastructure.hashing.bcrypt.BCryptHashingService;
import main.web.services.fitsense.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class WebSecurityConfiguration {

    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health"
    };

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final BCryptHashingService hashingService;
    private final UnauthorizedRequestHandlerEntryPoint unauthorizedRequestHandlerEntryPoint;

    public WebSecurityConfiguration(UserRepository userRepository,
                                    TokenService tokenService,
                                    BCryptHashingService hashingService,
                                    UnauthorizedRequestHandlerEntryPoint entryPoint) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.hashingService = hashingService;
        this.unauthorizedRequestHandlerEntryPoint = entryPoint;
    }

    @Bean
    public BearerAuthorizationRequestFilter authorizationRequestFilter() {
        return new BearerAuthorizationRequestFilter(tokenService, userRepository);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return hashingService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(unauthorizedRequestHandlerEntryPoint))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated());

        http.addFilterBefore(
                new BearerAuthorizationRequestFilter(tokenService, userRepository),
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
