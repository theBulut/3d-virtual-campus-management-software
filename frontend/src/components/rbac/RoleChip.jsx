/** Role names are immutable and documented in the thesis; they are shown, never translated. */
export default function RoleChip({ role, onRemove, disabled = false }) {
  return (
    <span className={`chip chip--${role.toLowerCase()}`}>
      {role}
      {onRemove && (
        <button
          type="button"
          className="chip__remove"
          onClick={() => onRemove(role)}
          disabled={disabled}
          aria-label={`Rolle ${role} entziehen`}
          title={`Rolle ${role} entziehen`}
        >
          ×
        </button>
      )}
    </span>
  );
}
