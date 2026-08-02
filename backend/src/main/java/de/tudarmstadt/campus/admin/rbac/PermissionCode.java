package de.tudarmstadt.campus.admin.rbac;

/**
 * The complete permission catalogue of spec section 1.2. Permissions follow the scheme
 * {@code RESOURCE_ACTION} and are enforced as Spring Security authorities — never as {@code ROLE_}
 * prefixed roles.
 * <p>
 * This enum and {@code V4__seed_rbac.sql} must stay identical; {@code RoleCatalogConsistencyIT}
 * compares them.
 * <p>
 * Descriptions are German because they are shown in the permission matrix of the admin interface.
 */
public enum PermissionCode {

    // Users and rights management
    USER_READ(Resource.USER, "READ", "Nutzerliste und -details lesen"),
    USER_CREATE(Resource.USER, "CREATE", "Neues Nutzerkonto anlegen"),
    USER_UPDATE(Resource.USER, "UPDATE", "Stammdaten eines fremden Kontos ändern"),
    USER_DELETE(Resource.USER, "DELETE", "Konto löschen"),
    USER_ACTIVATE(Resource.USER, "ACTIVATE", "Konto sperren oder entsperren"),
    USER_PASSWORD_RESET(Resource.USER, "PASSWORD_RESET", "Passwort eines fremden Kontos zurücksetzen"),
    ROLE_READ(Resource.ROLE, "READ", "Rollen und deren Berechtigungen lesen"),
    ROLE_ASSIGN(Resource.ROLE, "ASSIGN", "Rollen zuweisen und entziehen"),
    ROLE_MANAGE(Resource.ROLE, "MANAGE", "Rollen anlegen, ändern und löschen"),
    PROFILE_UPDATE_OWN(Resource.PROFILE, "UPDATE_OWN", "Eigenes Profil und Passwort ändern"),

    // Points of interest
    POI_READ_PUBLISHED(Resource.POI, "READ_PUBLISHED", "Nur veröffentlichte POIs lesen"),
    POI_READ_ALL(Resource.POI, "READ_ALL", "Alle POIs inklusive Entwürfe lesen"),
    POI_CREATE(Resource.POI, "CREATE", "POI anlegen; entsteht immer im Status Entwurf"),
    POI_UPDATE_OWN(Resource.POI, "UPDATE_OWN", "Nur selbst erstellte oder zugewiesene POIs ändern"),
    POI_UPDATE_ANY(Resource.POI, "UPDATE_ANY", "Beliebige POIs ändern"),
    POI_DELETE(Resource.POI, "DELETE", "POI löschen"),
    POI_SUBMIT_REVIEW(Resource.POI, "SUBMIT_REVIEW", "Eigenen POI zur Prüfung einreichen"),
    POI_PUBLISH(Resource.POI, "PUBLISH", "Freigeben, zurückweisen und archivieren"),
    POI_ASSIGN(Resource.POI, "ASSIGN", "Bearbeiter eines POI setzen"),

    // Buildings
    BUILDING_READ_PUBLIC(Resource.BUILDING, "READ_PUBLIC", "Öffentliche Gebäudedaten lesen"),
    BUILDING_READ_ALL(Resource.BUILDING, "READ_ALL", "Alle Gebäudedaten lesen"),
    BUILDING_CREATE(Resource.BUILDING, "CREATE", "Gebäude anlegen"),
    BUILDING_UPDATE(Resource.BUILDING, "UPDATE", "Gebäude ändern"),
    BUILDING_DELETE(Resource.BUILDING, "DELETE", "Gebäude löschen"),

    // Consultation hours
    CONSULTATION_READ_PUBLIC(Resource.CONSULTATION, "READ_PUBLIC", "Öffentliche Beratungszeiten lesen"),
    CONSULTATION_READ_ALL(Resource.CONSULTATION, "READ_ALL", "Alle Beratungszeiten lesen"),
    CONSULTATION_CREATE(Resource.CONSULTATION, "CREATE", "Beratungsangebot anlegen"),
    CONSULTATION_UPDATE_OWN(Resource.CONSULTATION, "UPDATE_OWN", "Nur eigene Beratungsangebote ändern"),
    CONSULTATION_UPDATE_ANY(Resource.CONSULTATION, "UPDATE_ANY", "Beliebige Beratungsangebote ändern"),
    CONSULTATION_DELETE(Resource.CONSULTATION, "DELETE", "Beratungsangebot löschen"),

    // Media and system
    MEDIA_UPLOAD(Resource.MEDIA, "UPLOAD", "Medien hochladen"),
    MEDIA_DELETE(Resource.MEDIA, "DELETE", "Medien löschen"),
    AUDIT_READ(Resource.AUDIT, "READ", "Vollständiges Audit-Log lesen"),
    AUDIT_READ_CONTENT(Resource.AUDIT, "READ_CONTENT",
            "Audit-Log gefiltert auf Inhaltsressourcen lesen"),
    SYSTEM_HEALTH_READ(Resource.SYSTEM, "HEALTH_READ", "Health- und Systeminformationen lesen"),
    SYSTEM_CONFIG(Resource.SYSTEM, "CONFIG", "Systemkonfiguration ändern"),
    DATA_EXPORT(Resource.DATA, "EXPORT", "Datenexport als CSV oder JSON");

    /**
     * Resource groups of the catalogue. Mirrored by the {@code permission_resource_known} check
     * constraint in V1; {@code PROFILE} and {@code DATA} extend the list of the specification
     * (docs/DECISIONS.md D-14).
     */
    public enum Resource {
        USER, ROLE, PROFILE, POI, BUILDING, CONSULTATION, MEDIA, AUDIT, SYSTEM, DATA
    }

    private final Resource resource;
    private final String action;
    private final String description;

    PermissionCode(Resource resource, String action, String description) {
        this.resource = resource;
        this.action = action;
        this.description = description;
    }

    public Resource resource() {
        return resource;
    }

    public String action() {
        return action;
    }

    public String description() {
        return description;
    }
}
