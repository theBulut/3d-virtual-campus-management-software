/**
 * Published or not — the whole state a building or a consultation offer has.
 * <p>
 * Deliberately not {@link StatusBadge}: that one shows the four-state workflow of a POI, and reusing it
 * here would suggest a review queue for records that have none (spec section 4.5).
 */
export default function PublishedBadge({ published }) {
  return published ? (
    <span className="badge badge--published">Veröffentlicht</span>
  ) : (
    <span className="badge badge--draft">Unveröffentlicht</span>
  );
}
