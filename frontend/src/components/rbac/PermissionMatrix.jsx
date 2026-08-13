import { Fragment, useMemo } from 'react';

/**
 * The permission matrix of spec section 1.3 as a table: rows are permissions grouped by resource,
 * columns are the six roles.
 * <p>
 * Everything comes from {@code GET /api/roles/matrix} — the same catalogue the authorisation runs on.
 * The picture cannot drift from the enforcement, because there is no second copy of it here.
 */
export default function PermissionMatrix({ matrix, highlightRole }) {
  const groups = useMemo(() => {
    const byResource = new Map();
    matrix.permissions.forEach((permission) => {
      const list = byResource.get(permission.resource) ?? [];
      list.push(permission);
      byResource.set(permission.resource, list);
    });
    return [...byResource.entries()];
  }, [matrix.permissions]);

  return (
    <div className="table__wrapper">
      <table className="table table--matrix">
        <thead>
          <tr>
            <th scope="col">Berechtigung</th>
            {matrix.roles.map((role) => (
              <th
                key={role.name}
                scope="col"
                className={role.name === highlightRole ? 'matrix__role matrix__role--own' : 'matrix__role'}
                title={role.displayName}
              >
                {role.name}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {groups.map(([resource, permissions]) => (
            <Fragment key={resource}>
              <tr className="matrix__group">
                <th scope="rowgroup" colSpan={matrix.roles.length + 1}>
                  {resource}
                </th>
              </tr>
              {permissions.map((permission) => (
                <tr key={permission.code}>
                  <th scope="row" className="matrix__permission">
                    <code>{permission.code}</code>
                    <span className="matrix__description">{permission.description}</span>
                  </th>
                  {matrix.roles.map((role) => {
                    const granted = matrix.assignments[role.name]?.includes(permission.code);
                    return (
                      <td
                        key={role.name}
                        className={granted ? 'matrix__cell matrix__cell--granted' : 'matrix__cell'}
                      >
                        <span aria-label={granted ? 'ja' : 'nein'}>{granted ? '✓' : '·'}</span>
                      </td>
                    );
                  })}
                </tr>
              ))}
            </Fragment>
          ))}
        </tbody>
      </table>
    </div>
  );
}
