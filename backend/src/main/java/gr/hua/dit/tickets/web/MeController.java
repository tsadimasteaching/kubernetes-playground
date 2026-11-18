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

    @GetMapping("/api/me")
    public Me me(Authentication auth) {
        return new Me(auth.getName(), auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replaceFirst("^ROLE_", ""))
                .toList());
    }
}
