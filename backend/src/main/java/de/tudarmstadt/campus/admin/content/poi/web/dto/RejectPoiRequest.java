package de.tudarmstadt.campus.admin.content.poi.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Rejecting a submission needs a reason — the author has to know what to correct. */
public record RejectPoiRequest(
        @NotBlank(message = "Eine Begründung ist erforderlich") String reviewNote) {
}
