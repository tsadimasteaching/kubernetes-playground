package gr.hua.dit.tickets.web;

import gr.hua.dit.tickets.model.Ticket;
import gr.hua.dit.tickets.model.TicketStatus;
import gr.hua.dit.tickets.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    /** USER: own tickets. AGENT: all tickets. Optional ?status=OPEN|IN_PROGRESS|RESOLVED */
    @GetMapping
    public List<Ticket> list(@RequestParam(required = false) TicketStatus status, Authentication auth) {
        return service.list(auth, status);
    }

    @GetMapping("/{id}")
    public Ticket get(@PathVariable Long id, Authentication auth) {
        return service.get(id, auth);
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @ResponseStatus(HttpStatus.CREATED)
    public Ticket create(@Valid @RequestBody TicketRequest request, Authentication auth) {
        return service.create(request, auth);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public Ticket update(@PathVariable Long id, @Valid @RequestBody TicketRequest request, Authentication auth) {
        return service.update(id, request, auth);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        service.delete(id, auth);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('AGENT')")
    public Ticket assign(@PathVariable Long id, Authentication auth) {
        return service.assignToMe(id, auth);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('AGENT')")
    public Ticket resolve(@PathVariable Long id, @Valid @RequestBody ResolveRequest request, Authentication auth) {
        return service.resolve(id, request.resolution(), auth);
    }
}
