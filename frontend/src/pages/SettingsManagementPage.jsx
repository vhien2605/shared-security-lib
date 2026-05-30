export default function SettingsManagementPage() {
  const rows = [
    { id: 'CFG-1001', module: 'Rate Limit Inbound', owner: 'Gateway Team', updatedAt: '29/05/2026 08:45', status: 'ok' },
    { id: 'CFG-1002', module: 'Outbound Retry Policy', owner: 'Core Service', updatedAt: '29/05/2026 09:10', status: 'warn' },
    { id: 'CFG-1003', module: 'Alert Channels', owner: 'SOC Team', updatedAt: '28/05/2026 16:21', status: 'ok' },
    { id: 'CFG-1004', module: 'Anomaly Threshold', owner: 'Security Team', updatedAt: '28/05/2026 11:03', status: 'off' },
    { id: 'CFG-1005', module: 'Log Retention', owner: 'Platform Team', updatedAt: '27/05/2026 18:56', status: 'ok' },
  ]

  const statusLabel = {
    ok: 'Dang hoat dong',
    warn: 'Can xem xet',
    off: 'Tam dung',
  }

  return (
    <section className="dashboard-page">
      <header className="dashboard-page__header">
        <h1 className="dashboard-page__title">Quan ly setting</h1>
        <p className="dashboard-page__subtitle">Theo doi cau hinh he thong va trang thai ap dung chinh sach mac dinh.</p>
      </header>

      <div className="dashboard-page__toolbar">
        <button className="dashboard-page__btn" type="button">
          Bo loc
        </button>
        <button className="dashboard-page__btn dashboard-page__btn--primary" type="button">
          Them moi
        </button>
      </div>

      <div className="dashboard-page__table-wrap">
        <table className="dashboard-table" aria-label="Bang quan ly setting">
          <thead>
            <tr>
              <th>Ma cau hinh</th>
              <th>Hang muc</th>
              <th>Nguoi quan ly</th>
              <th>Cap nhat gan nhat</th>
              <th>Trang thai</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id}>
                <td>{row.id}</td>
                <td>{row.module}</td>
                <td>{row.owner}</td>
                <td>{row.updatedAt}</td>
                <td>
                  <span className={`status-chip status-chip--${row.status}`}>{statusLabel[row.status]}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="dashboard-page__pagination" role="navigation" aria-label="Phan trang">
          <button className="dashboard-page__page-btn" type="button" aria-label="Trang truoc">
            {'<'}
          </button>
          <button className="dashboard-page__page-btn dashboard-page__page-btn--active" type="button">
            1
          </button>
          <button className="dashboard-page__page-btn" type="button">
            2
          </button>
          <button className="dashboard-page__page-btn" type="button">
            3
          </button>
          <button className="dashboard-page__page-btn" type="button" aria-label="Trang sau">
            {'>'}
          </button>
        </div>
      </div>
    </section>
  )
}
