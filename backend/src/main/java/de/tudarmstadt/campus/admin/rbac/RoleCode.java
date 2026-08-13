package de.tudarmstadt.campus.admin.rbac;

/**
 * The six fixed roles of spec section 1.1. The names are documented in the thesis and must never change.
 * <p>
 * Roles are hard-coded on purpose (decision E-1): they are seeded from this catalogue at startup and
 * there is no interface for creating custom roles. Since permissions are a separate entity and
 * authorisation runs on authorities rather than role names, adding that interface later needs no
 * redesign.
 */
public enum RoleCode {

    ADMIN("Systemadministration",
            "Vollzugriff. Legt Konten an, vergibt und entzieht alle Rollen, sieht das Audit-Log und "
                    + "die Systemkonfiguration.",
            true, 1),

    PROJEKTLEITER("Projektleitung",
            "Richtet Konten für Mitarbeitende und Verwaltungspersonal ein, vergibt eingeschränkte "
                    + "Rollen, gibt Inhalte frei, pflegt und löscht Inhalte und exportiert Daten.",
            true, 2),

    PROJEKTMITARBEITER("Projektmitarbeit",
            "Speist Inhalte ein: erstellt und bearbeitet eigene beziehungsweise zugewiesene POIs, lädt "
                    + "Medien hoch und reicht Inhalte zur Prüfung ein. Kann nicht selbst veröffentlichen.",
            true, 3),

    PERSONAL("Verwaltungspersonal",
            "Pflegt die Beratungszeiten der eigenen Einrichtung und das eigene Profil. Keine Berührung "
                    + "mit der Rechteverwaltung.",
            true, 4),

    MAINTENANCE_DEV("Betrieb / Entwicklung",
            "Liest Health-Status, Systeminformationen und Audit-Log. Kein Zugriff auf Nutzerverwaltung "
                    + "und Inhalte.",
            true, 5),

    /**
     * The role every self-registered account carries. It used to exist for the completeness of the
     * matrix only; since students register themselves and play the campus with their progress bound to
     * their account, it is a role like any other (docs/DECISIONS.md D-40).
     * <p>
     * It stays in place when an account is promoted, so a project lead is a player as well — the
     * permissions of an account are the union of its roles.
     */
    EXTERNE_PERSON("Öffentlicher Zugriff",
            "Spielt den 3D-Campus und liest veröffentlichte Inhalte. Wird bei der Selbstregistrierung "
                    + "vergeben und bleibt beim Hochstufen erhalten.",
            true, 6);

    private final String displayName;
    private final String description;
    private final boolean assignable;
    private final int sortOrder;

    RoleCode(String displayName, String description, boolean assignable, int sortOrder) {
        this.displayName = displayName;
        this.description = description;
        this.assignable = assignable;
        this.sortOrder = sortOrder;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /** False for {@link #EXTERNE_PERSON}; assigning it must fail with {@code ROLE_NOT_ASSIGNABLE}. */
    public boolean assignable() {
        return assignable;
    }

    public int sortOrder() {
        return sortOrder;
    }
}
