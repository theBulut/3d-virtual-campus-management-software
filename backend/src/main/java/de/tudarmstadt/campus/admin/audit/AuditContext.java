package de.tudarmstadt.campus.admin.audit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lets an audited method hand the aspect what only it knows: the state before and after the change.
 * <p>
 * A generic aspect can record who did what and whether it worked, but not that a role list went from
 * {@code [PERSONAL]} to {@code [PERSONAL, PROJEKTMITARBEITER]}. The service records that inline;
 * {@code AuditAspect} drains and clears the context around every invocation, so nothing leaks into the
 * next request on the same thread.
 */
public final class AuditContext {

    private static final ThreadLocal<Map<String, Object>> BEFORE = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> AFTER = new ThreadLocal<>();
    private static final ThreadLocal<String> ACTION = new ThreadLocal<>();
    private static final ThreadLocal<String> RESOURCE_ID = new ThreadLocal<>();

    private AuditContext() {
    }

    public static void before(String key, Object value) {
        put(BEFORE, key, value);
    }

    public static void after(String key, Object value) {
        put(AFTER, key, value);
    }

    /**
     * Refines the action of the annotation when it depends on the outcome — locking and unlocking share
     * one method but are {@code USER_DEACTIVATED} and {@code USER_ACTIVATED} in the catalogue.
     */
    public static void action(String action) {
        ACTION.set(action);
    }

    /** Sets the resource once it exists, for example the id of a freshly created account. */
    public static void resourceId(Object resourceId) {
        RESOURCE_ID.set(resourceId == null ? null : String.valueOf(resourceId));
    }

    static String drainAction() {
        String action = ACTION.get();
        ACTION.remove();
        return action;
    }

    static String drainResourceId() {
        String resourceId = RESOURCE_ID.get();
        RESOURCE_ID.remove();
        return resourceId;
    }

    static Map<String, Object> drainBefore() {
        return drain(BEFORE);
    }

    static Map<String, Object> drainAfter() {
        return drain(AFTER);
    }

    static void clear() {
        BEFORE.remove();
        AFTER.remove();
        ACTION.remove();
        RESOURCE_ID.remove();
    }

    private static void put(ThreadLocal<Map<String, Object>> holder, String key, Object value) {
        Map<String, Object> state = holder.get();
        if (state == null) {
            state = new LinkedHashMap<>();
            holder.set(state);
        }
        state.put(key, value);
    }

    private static Map<String, Object> drain(ThreadLocal<Map<String, Object>> holder) {
        Map<String, Object> state = holder.get();
        holder.remove();
        return state == null || state.isEmpty() ? null : state;
    }
}
