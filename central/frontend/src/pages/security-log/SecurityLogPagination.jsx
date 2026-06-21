import { PAGE_SIZE_OPTIONS } from './securityLogConstants'

export default function SecurityLogPagination({ pageInfo, onPageChange, onSizeChange, disabled }) {
  const currentPage = pageInfo.number + 1
  const totalPages = Math.max(pageInfo.totalPages, 1)
  return (
    <div className="security-log-pagination">
      <span className="security-log-pagination__summary">Trang {currentPage} / {totalPages} · {pageInfo.totalElements.toLocaleString('vi-VN')} bản ghi</span>
      <label className="security-log-pagination__size">
        <span>Size</span>
        <select value={pageInfo.size} onChange={(event) => onSizeChange(Number(event.target.value))} disabled={disabled}>
          {PAGE_SIZE_OPTIONS.map((size) => <option key={size} value={size}>{size}</option>)}
        </select>
      </label>
      <div className="security-log-pagination__actions">
        <button type="button" className="security-log-button" onClick={() => onPageChange(pageInfo.number - 1)} disabled={disabled || pageInfo.first || pageInfo.number <= 0}>Trước</button>
        <button type="button" className="security-log-button" onClick={() => onPageChange(pageInfo.number + 1)} disabled={disabled || pageInfo.last || pageInfo.number + 1 >= pageInfo.totalPages}>Sau</button>
      </div>
    </div>
  )
}
