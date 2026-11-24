package gr.hua.dit.tickets.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 resource server: every /api request must carry a JWT access token issued by Keycloak
 * ({@code Authorization: Bearer <token>}).
 *
 * <p>The signature, issuer, expiry and audience of the token are checked by Spring Security, using the
 * {@code spring.security.oauth2.resourceserver.jwt.*} settings in application.properties.
 * This class only decides which URLs need a token and how the token's claims become a user name and roles.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless REST API: the token is sent explicitly, not as a cookie
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Turns the claims of a Keycloak access token into a Spring Security {@code Authentication}.
     *
     * <pre>
     * {
     *   "sub": "86b7d421-...",                  // Keycloak's internal user id
     *   "preferred_username": "alice",          // -> auth.getName()  (stored as Ticket.createdBy)
     *   "roles": ["USER"],                      // -> ROLE_USER       (used by @PreAuthorize("hasRole('USER')"))
     *   "aud": "tickets-backend", ...
     * }
     * </pre>
     *
     * The "roles" claim is added by a protocol mapper of the Keycloak clients (keycloak/realm/tickets-realm.json):
     * it contains the client roles of the client "tickets-backend".
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        // Default is "sub" (a UUID). The username keeps ticket ownership readable ("alice", not a UUID).
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }
}
