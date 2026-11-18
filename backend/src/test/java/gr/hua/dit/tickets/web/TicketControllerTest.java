package gr.hua.dit.tickets.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test: full Spring context (security, JPA on H2) driven through MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TicketControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
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
        mvc.perform(get("/api/me").with(httpBasic("agent", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("agent"))
                .andExpect(jsonPath("$.roles[0]").value("AGENT"));
    }

    @Test
    void userSeesOnlyOwnTickets() throws Exception {
        mvc.perform(get("/api/tickets").with(httpBasic("bob", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].createdBy").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("bob"))));
    }

    @Test
    void userCannotSeeSomeoneElsesTicket() throws Exception {
        mvc.perform(get("/api/tickets/1").with(httpBasic("bob", "password")))
                .andExpect(status().isNotFound());
    }

    @Test
    void agentCannotCreateTickets() throws Exception {
        mvc.perform(post("/api/tickets").with(httpBasic("agent", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCreatesAndAgentResolves() throws Exception {
        String body = mvc.perform(post("/api/tickets").with(httpBasic("alice", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Laptop is slow\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(post("/api/tickets/" + id + "/resolve").with(httpBasic("alice", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"self-fixed\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/tickets/" + id + "/assign").with(httpBasic("agent", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignedTo").value("agent"));

        mvc.perform(post("/api/tickets/" + id + "/resolve").with(httpBasic("agent", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"Cleaned up startup apps\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }
}
