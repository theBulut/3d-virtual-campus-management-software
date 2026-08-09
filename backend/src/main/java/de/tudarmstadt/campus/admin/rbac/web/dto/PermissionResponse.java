package de.tudarmstadt.campus.admin.rbac.web.dto;

/**
 * Plain data. The translation from the entity lives in {@code RoleService}, so no class in the web layer
 * ever sees an entity (spec section 3).
 */
public record PermissionResponse(String code, String resource, String action, String description) {
}
