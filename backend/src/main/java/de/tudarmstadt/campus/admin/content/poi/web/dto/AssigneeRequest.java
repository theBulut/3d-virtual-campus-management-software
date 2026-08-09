package de.tudarmstadt.campus.admin.content.poi.web.dto;

/** Sets or clears the editor of a POI; {@code null} removes the assignment. */
public record AssigneeRequest(Long userId) {
}
