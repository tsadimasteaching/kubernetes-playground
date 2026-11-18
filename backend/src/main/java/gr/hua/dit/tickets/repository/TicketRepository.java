package gr.hua.dit.tickets.repository;

import gr.hua.dit.tickets.model.Ticket;
import gr.hua.dit.tickets.model.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByCreatedByOrderByIdAsc(String createdBy);

    List<Ticket> findByStatusOrderByIdAsc(TicketStatus status);
}
