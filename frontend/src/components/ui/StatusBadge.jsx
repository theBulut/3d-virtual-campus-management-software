const LABELS = {
  DRAFT: 'Entwurf',
  IN_REVIEW: 'In Prüfung',
  PUBLISHED: 'Veröffentlicht',
  ARCHIVED: 'Archiviert',
};

/** The four states of spec section 4.5, in German and colour-coded. */
export default function StatusBadge({ status }) {
  return (
    <span className={`badge badge--${status.toLowerCase().replace('_', '-')}`}>
      {LABELS[status] ?? status}
    </span>
  );
}

export { LABELS as STATUS_LABELS };
