package gr.hua.dit.tickets.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test: full Spring context (security, JPA on H2) driven through MockMvc.
 *
 * <p>No Keycloak is needed: {@link #token} builds the claims of a Keycloak access token in memory and passes
 * them through the application's own {@link JwtAuthenticationConverter}, so the claim → user/role mapping of
 * SecurityConfig is tested too. Only the signature check (done by Spring Security itself) is skipped.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TicketControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtAuthenticationConverter converter;

    /** A request authenticated with a token like the ones Keycloak issues for the given user. */
    RequestPostProcessor token(String username, String role) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .claim("preferred_username", username)
                .claim("roles", List.of(role))
                .claim("aud", List.of("tickets-backend"))
                .build();
        return authentication(converter.convert(jwt));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mvc.perform(get("/api/tickets").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void swaggerIsPublic() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void meReturnsRoles() throws Exception {
        mvc.perform(get("/api/me").with(token("agent", "AGENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("agent"))
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.contains("AGENT")));
    }

    @Test
    void userSeesOnlyOwnTickets() throws Exception {
        mvc.perform(get("/api/tickets").with(token("bob", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].createdBy").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("bob"))));
    }

    @Test
    void userCannotSeeSomeoneElsesTicket() throws Exception {
        mvc.perform(get("/api/tickets/1").with(token("bob", "USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void agentCannotCreateTickets() throws Exception {
        mvc.perform(post("/api/tickets").with(token("agent", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCreatesAndAgentResolves() throws Exception {
        String body = mvc.perform(post("/api/tickets").with(token("alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Laptop is slow\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(post("/api/tickets/" + id + "/resolve").with(token("alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"self-fixed\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/tickets/" + id + "/assign").with(token("agent", "AGENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignedTo").value("agent"));

        mvc.perform(post("/api/tickets/" + id + "/resolve").with(token("agent", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"Cleaned up startup apps\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }
}
