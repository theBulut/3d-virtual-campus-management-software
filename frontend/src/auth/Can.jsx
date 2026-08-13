import { useAuth } from './AuthContext';

/**
 * Shows its children only if the account holds the permission:
 * {@code <Can perm="POI_PUBLISH"><button>Freigeben</button></Can>}.
 * <p>
 * <b>This hides, it does not protect.</b> Enforcement happens on the server, in the
 * {@code @PreAuthorize} expression of every controller method; the same call made by hand against the
 * API is answered with 403 and lands in the audit log. Hiding the button here only keeps the interface
 * honest — nobody is offered an action that would be refused.
 */
export default function Can({ perm, anyOf, fallback = null, children }) {
  const { hasPermission, hasAnyPermission } = useAuth();
  const allowed = anyOf ? hasAnyPermission(anyOf) : hasPermission(perm);
  return allowed ? children : fallback;
}
