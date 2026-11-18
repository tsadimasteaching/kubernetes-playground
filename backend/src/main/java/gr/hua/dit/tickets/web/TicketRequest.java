package gr.hua.dit.tickets.web;

import gr.hua.dit.tickets.model.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for creating / updating a ticket (USER role). */
public record TicketRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        TicketPriority priority) {
}
