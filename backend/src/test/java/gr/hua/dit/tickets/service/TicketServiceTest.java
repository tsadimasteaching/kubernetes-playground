package gr.hua.dit.tickets.service;

import gr.hua.dit.tickets.model.Ticket;
import gr.hua.dit.tickets.model.TicketPriority;
import gr.hua.dit.tickets.model.TicketStatus;
import gr.hua.dit.tickets.repository.TicketRepository;
import gr.hua.dit.tickets.web.TicketRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain unit test: no Spring context, the repository is a Mockito mock. */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    TicketRepository repository;

    @InjectMocks
    TicketService service;

    static Authentication user(String name) {
        return new UsernamePasswordAuthenticationToken(name, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
    }

    static Authentication agent(String name) {
        return new UsernamePasswordAuthenticationToken(name, null, AuthorityUtils.createAuthorityList("ROLE_AGENT"));
    }

    static Ticket ticket(String createdBy, TicketStatus status) {
        Ticket t = new Ticket();
        t.setTitle("t");
        t.setCreatedBy(createdBy);
        t.setStatus(status);
        return t;
    }

    @Test
    void createSetsOwnerAndDefaultPriority() {
        when(repository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket created = service.create(new TicketRequest("Laptop", "slow", null), user("alice"));

        assertThat(created.getCreatedBy()).isEqualTo("alice");
        assertThat(created.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(created.getPriority()).isEqualTo(TicketPriority.MEDIUM);
    }

    @Test
    void userListsOnlyOwnTickets() {
        when(repository.findByCreatedByOrderByIdAsc("alice")).thenReturn(List.of(ticket("alice", TicketStatus.OPEN)));

        assertThat(service.list(user("alice"), null)).hasSize(1);
        verify(repository, never()).findAll();
    }

    @Test
    void agentListsAllTickets() {
        when(repository.findAll()).thenReturn(List.of(ticket("alice", TicketStatus.OPEN), ticket("bob", TicketStatus.OPEN)));

        assertThat(service.list(agent("agent"), null)).hasSize(2);
    }

    @Test
    void userCannotReadSomeoneElsesTicket() {
        when(repository.findById(1L)).thenReturn(Optional.of(ticket("bob", TicketStatus.OPEN)));

        assertThatThrownBy(() -> service.get(1L, user("alice")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void userCannotEditTicketThatIsInProgress() {
        when(repository.findById(1L)).thenReturn(Optional.of(ticket("alice", TicketStatus.IN_PROGRESS)));

        assertThatThrownBy(() -> service.update(1L, new TicketRequest("x", null, null), user("alice")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void agentAssignsAndResolves() {
        Ticket t = ticket("alice", TicketStatus.OPEN);
        when(repository.findById(1L)).thenReturn(Optional.of(t));

        service.assignToMe(1L, agent("agent"));
        assertThat(t.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(t.getAssignedTo()).isEqualTo("agent");

        service.resolve(1L, "Rebooted", agent("agent"));
        assertThat(t.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(t.getResolution()).isEqualTo("Rebooted");
    }

    @Test
    void agentCannotResolveTicketAssignedToAnotherAgent() {
        Ticket t = ticket("alice", TicketStatus.IN_PROGRESS);
        t.setAssignedTo("agent2");
        when(repository.findById(1L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.resolve(1L, "done", agent("agent")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
