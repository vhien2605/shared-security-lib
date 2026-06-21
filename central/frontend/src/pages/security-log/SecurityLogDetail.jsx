import { COMMON_FIELDS, FLOW_FIELD_GROUPS } from './securityLogFieldGroups'
import { formatValue } from './securityLogMappers'

function DetailGroup({ title, fields, log, onCopy }) {
  return (
    <section className="security-log-detail__group">
      <h3>{title}</h3>
      <dl>
        {fields.map(([key, label, formatter]) => {
          const value = formatter ? formatter(log[key]) : formatValue(log[key])
          return (
            <div key={key} className="security-log-detail__row">
              <dt>{label}</dt>
              <dd>
                <span>{value}</span>
                {['traceId', 'correlationId'].includes(key) && log[key] ? (
                  <button type="button" className="security-log-copy" onClick={() => onCopy(log[key])}>Copy</button>
                ) : null}
              </dd>
            </div>
          )
        })}
      </dl>
    </section>
  )
}

export default function SecurityLogDetail({ log, onClose, onCopy, copyMessage }) {
  if (!log) {
    return (
      <aside className="security-log-detail security-log-detail--empty">
        <p>Chọn một dòng log để xem chi tiết.</p>
      </aside>
    )
  }
  const flowGroup = FLOW_FIELD_GROUPS[log.flowType]
  return (
    <aside className="security-log-detail" aria-label="Chi tiết nhật ký">
      <div className="security-log-detail__header">
        <div>
          <h2>Chi tiết log</h2>
          <p>{formatValue(log.flowType)}</p>
        </div>
        <button type="button" onClick={onClose} aria-label="Đóng chi tiết">×</button>
      </div>
      {copyMessage ? <div className="security-log-copy-toast" role="status">{copyMessage}</div> : null}
      <div className="security-log-detail__body">
        <DetailGroup title="Thông tin chung" fields={COMMON_FIELDS} log={log} onCopy={onCopy} />
        {flowGroup ? <DetailGroup title={flowGroup.title} fields={flowGroup.fields} log={log} onCopy={onCopy} /> : null}
        <details className="security-log-raw">
          <summary>Raw JSON</summary>
          <pre>{JSON.stringify(log, null, 2)}</pre>
        </details>
      </div>
    </aside>
  )
}
