package gr.hua.dit.tickets.repository;

import gr.hua.dit.tickets.model.Ticket;
import gr.hua.dit.tickets.model.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Slice test: only JPA components are loaded, against an in-memory H2 database. */
@DataJpaTest
class TicketRepositoryTest {

    @Autowired
    TicketRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.save(ticket("alice", TicketStatus.OPEN));
        repository.save(ticket("alice", TicketStatus.RESOLVED));
        repository.save(ticket("bob", TicketStatus.OPEN));
    }

    @Test
    void findsTicketsByCreator() {
        assertThat(repository.findByCreatedByOrderByIdAsc("alice"))
                .hasSize(2)
                .allMatch(t -> t.getCreatedBy().equals("alice"));
    }

    @Test
    void findsTicketsByStatus() {
        assertThat(repository.findByStatusOrderByIdAsc(TicketStatus.OPEN)).hasSize(2);
    }

    @Test
    void setsTimestampsOnSave() {
        Ticket saved = repository.save(ticket("bob", TicketStatus.OPEN));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    private static Ticket ticket(String createdBy, TicketStatus status) {
        Ticket t = new Ticket();
        t.setTitle("title");
        t.setCreatedBy(createdBy);
        t.setStatus(status);
        return t;
    }
}
