package gr.hua.dit.tickets.config;

import gr.hua.dit.tickets.model.Ticket;
import gr.hua.dit.tickets.model.TicketPriority;
import gr.hua.dit.tickets.model.TicketStatus;
import gr.hua.dit.tickets.repository.TicketRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Inserts a few demo tickets the first time the app starts against an empty database. */
@Component
public class DataSeeder implements CommandLineRunner {

    private final TicketRepository repository;

    public DataSeeder(TicketRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        repository.save(ticket("Cannot log in to VPN", "Getting \"authentication failed\" since this morning.",
                TicketPriority.HIGH, "alice", null, TicketStatus.OPEN, null));
        repository.save(ticket("Printer on 2nd floor jammed", "Paper stuck in tray 2.",
                TicketPriority.LOW, "bob", "agent", TicketStatus.IN_PROGRESS, null));
        repository.save(ticket("Request new monitor", "Current one flickers.",
                TicketPriority.MEDIUM, "alice", "agent", TicketStatus.RESOLVED, "Replaced monitor."));
    }

    private static Ticket ticket(String title, String description, TicketPriority priority, String createdBy,
                                 String assignedTo, TicketStatus status, String resolution) {
        Ticket t = new Ticket();
        t.setTitle(title);
        t.setDescription(description);
        t.setPriority(priority);
        t.setCreatedBy(createdBy);
        t.setAssignedTo(assignedTo);
        t.setStatus(status);
        t.setResolution(resolution);
        return t;
    }
}
