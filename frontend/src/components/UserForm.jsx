import { useState } from 'react';

const FIELDS = [
  { name: 'firstName', label: 'Vorname', type: 'text' },
  { name: 'lastName', label: 'Nachname', type: 'text' },
  { name: 'email', label: 'E-Mail', type: 'email' },
];

function UserForm({ user, fieldErrors, submitting, onSubmit, onCancel }) {
  const [form, setForm] = useState({
    firstName: user.firstName ?? '',
    lastName: user.lastName ?? '',
    email: user.email ?? '',
  });

  const isNew = user.id === undefined;

  const handleChange = (name) => (event) =>
    setForm((current) => ({ ...current, [name]: event.target.value }));

  const handleSubmit = (event) => {
    event.preventDefault();
    onSubmit(form);
  };

  // Validation lives in the backend, so the browser must not short-circuit submit.
  return (
    <form className="user-form" onSubmit={handleSubmit} noValidate>
      <h2>{isNew ? 'Neuen User anlegen' : `User #${user.id} bearbeiten`}</h2>

      {FIELDS.map(({ name, label, type }) => (
        <label key={name} className="field">
          <span>{label}</span>
          <input
            type={type}
            value={form[name]}
            onChange={handleChange(name)}
            aria-invalid={fieldErrors[name] ? 'true' : undefined}
          />
          {fieldErrors[name] && <small className="field-error">{fieldErrors[name]}</small>}
        </label>
      ))}

      <div className="form-actions">
        <button type="submit" className="primary" disabled={submitting}>
          {isNew ? 'Anlegen' : 'Speichern'}
        </button>
        <button type="button" onClick={onCancel} disabled={submitting}>
          Abbrechen
        </button>
      </div>
    </form>
  );
}

export default UserForm;
