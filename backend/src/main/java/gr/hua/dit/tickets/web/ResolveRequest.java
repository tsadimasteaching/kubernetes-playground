package gr.hua.dit.tickets.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for resolving a ticket (AGENT role). */
public record ResolveRequest(@NotBlank @Size(max = 2000) String resolution) {
}
