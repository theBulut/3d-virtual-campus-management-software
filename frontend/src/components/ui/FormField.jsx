/**
 * One labelled input plus the field error the backend sent for it. The messages come from Bean
 * Validation, so the client repeats no rule it would have to keep in step (spec section 4.7).
 */
export default function FormField({
  label,
  name,
  value,
  onChange,
  type = 'text',
  error,
  required = false,
  hint,
  children,
}) {
  const id = `field-${name}`;
  return (
    <div className={error ? 'field field--invalid' : 'field'}>
      <label className="field__label" htmlFor={id}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      {children ?? (
        <input
          id={id}
          name={name}
          type={type}
          className="field__input"
          value={value ?? ''}
          onChange={(event) => onChange(event.target.value)}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${id}-error` : undefined}
        />
      )}
      {hint && !error && <p className="field__hint">{hint}</p>}
      {error && (
        <p className="field__error" id={`${id}-error`}>
          {error}
        </p>
      )}
    </div>
  );
}
