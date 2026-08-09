package de.tudarmstadt.campus.admin.user.web.dto;

import jakarta.validation.constraints.NotNull;

/** Locks or unlocks an account (spec section 5.2, docs/DECISIONS.md D-28). */
public record UpdateStatusRequest(
        @NotNull(message = "Status muss angegeben werden") Boolean active) {
}
