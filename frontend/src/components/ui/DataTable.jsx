/**
 * A table with column definitions instead of hand-written markup per page.
 *
 * @param columns [{key, header, render?, className?}]
 * @param rows    the records
 * @param rowKey  how to identify a row
 */
export default function DataTable({ columns, rows, rowKey = (row) => row.id, empty, onRowClick }) {
  if (!rows.length) {
    return <p className="table__empty">{empty ?? 'Keine Einträge vorhanden.'}</p>;
  }

  return (
    <div className="table__wrapper">
      <table className="table">
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key} className={column.className}>
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={rowKey(row)}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              className={onRowClick ? 'table__row--clickable' : undefined}
            >
              {columns.map((column) => (
                <td key={column.key} className={column.className}>
                  {column.render ? column.render(row) : row[column.key]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
