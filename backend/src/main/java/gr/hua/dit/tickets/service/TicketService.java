package gr.hua.dit.tickets.service;

import gr.hua.dit.tickets.model.Ticket;
import gr.hua.dit.tickets.model.TicketPriority;
import gr.hua.dit.tickets.model.TicketStatus;
import gr.hua.dit.tickets.repository.TicketRepository;
import gr.hua.dit.tickets.web.TicketRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Business rules:
 * <ul>
 *   <li>USER: creates tickets, sees/edits/deletes only their own tickets, and only while they are OPEN.</li>
 *   <li>AGENT: sees all tickets, assigns a ticket to themselves and resolves it.</li>
 * </ul>
 */
@Service
@Transactional
public class TicketService {

    private final TicketRepository repository;

    public TicketService(TicketRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Ticket> list(Authentication auth, TicketStatus status) {
        if (isAgent(auth)) {
            return status == null ? repository.findAll() : repository.findByStatusOrderByIdAsc(status);
        }
        return repository.findByCreatedByOrderByIdAsc(auth.getName()).stream()
                .filter(t -> status == null || t.getStatus() == status)
                .toList();
    }

    @Transactional(readOnly = true)
    public Ticket get(Long id, Authentication auth) {
        Ticket ticket = find(id);
        if (!isAgent(auth) && !ticket.getCreatedBy().equals(auth.getName())) {
            // Do not reveal that someone else's ticket exists.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket " + id + " not found");
        }
        return ticket;
    }

    public Ticket create(TicketRequest request, Authentication auth) {
        Ticket ticket = new Ticket();
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setPriority(request.priority() == null ? TicketPriority.MEDIUM : request.priority());
        ticket.setCreatedBy(auth.getName());
        return repository.save(ticket);
    }

    public Ticket update(Long id, TicketRequest request, Authentication auth) {
        Ticket ticket = ownOpenTicket(id, auth);
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        if (request.priority() != null) {
            ticket.setPriority(request.priority());
        }
        return ticket;
    }

    public void delete(Long id, Authentication auth) {
        repository.delete(ownOpenTicket(id, auth));
    }

    public Ticket assignToMe(Long id, Authentication auth) {
        Ticket ticket = find(id);
        if (ticket.getStatus() == TicketStatus.RESOLVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket is already resolved");
        }
        ticket.setAssignedTo(auth.getName());
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        return ticket;
    }

    public Ticket resolve(Long id, String resolution, Authentication auth) {
        Ticket ticket = find(id);
        if (ticket.getStatus() == TicketStatus.RESOLVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket is already resolved");
        }
        if (ticket.getAssignedTo() != null && !ticket.getAssignedTo().equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ticket is assigned to " + ticket.getAssignedTo());
        }
        ticket.setAssignedTo(auth.getName());
        ticket.setResolution(resolution);
        ticket.setStatus(TicketStatus.RESOLVED);
        return ticket;
    }

    private Ticket ownOpenTicket(Long id, Authentication auth) {
        Ticket ticket = get(id, auth);
        if (!ticket.getCreatedBy().equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the creator can modify a ticket");
        }
        if (ticket.getStatus() != TicketStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only OPEN tickets can be modified");
        }
        return ticket;
    }

    private Ticket find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket " + id + " not found"));
    }

    private static boolean isAgent(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_AGENT".equals(a.getAuthority()));
    }
}
