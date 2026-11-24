package gr.hua.dit.tickets.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Returns the logged-in user; the frontend calls it to "log in" and to decide which UI to show. */
@RestController
public class MeController {

    public record Me(String username, List<String> roles) {
    }

    /**
     * Only the ROLE_* authorities are roles. Spring Security also adds other authorities,
     * e.g. FACTOR_BEARER (how the user authenticated), which the frontend must not see as a role.
     */
    @GetMapping("/api/me")
    public Me me(Authentication auth) {
        return new Me(auth.getName(), auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .toList());
    }
}
