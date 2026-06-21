/* eslint-disable react-hooks/set-state-in-effect */
import { useCallback, useEffect, useRef, useState } from 'react'
import { getSecurityLogs } from '../../services/securityLogs'
import SecurityLogDetail from './SecurityLogDetail'
import SecurityLogFilters from './SecurityLogFilters'
import SecurityLogPagination from './SecurityLogPagination'
import SecurityLogTable from './SecurityLogTable'
import SecurityLogToolbar from './SecurityLogToolbar'
import { DEFAULT_FILTERS, DEFAULT_PAGE_SIZE } from './securityLogConstants'
import { normalizeSecurityLogPage, toIsoInstantFromInput } from './securityLogMappers'
import './SecurityLogPage.css'

const initialPageInfo = {
  number: 0,
  size: DEFAULT_PAGE_SIZE,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
}

function buildRequestParams(filters, page, size) {
  return {
    ...filters,
    from: toIsoInstantFromInput(filters.from),
    to: toIsoInstantFromInput(filters.to),
    page,
    size,
    sortDirection: 'DESC',
  }
}

export default function SecurityLogPage() {
  const [draftFilters, setDraftFilters] = useState(DEFAULT_FILTERS)
  const [appliedFilters, setAppliedFilters] = useState(DEFAULT_FILTERS)
  const [logs, setLogs] = useState([])
  const [pageInfo, setPageInfo] = useState(initialPageInfo)
  const [selectedLog, setSelectedLog] = useState(null)
  const [isInitialLoading, setIsInitialLoading] = useState(true)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [error, setError] = useState('')
  const [lastUpdatedAt, setLastUpdatedAt] = useState('')
  const [autoRefreshMs, setAutoRefreshMs] = useState(0)
  const [copyMessage, setCopyMessage] = useState('')
  const requestIdRef = useRef(0)
  const abortRef = useRef(null)
  const inFlightRef = useRef(false)

  const loadLogs = useCallback(async ({ page = pageInfo.number, size = pageInfo.size, filters = appliedFilters, refresh = false } = {}) => {
    if (inFlightRef.current && refresh) return
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    const requestId = requestIdRef.current + 1
    requestIdRef.current = requestId
    inFlightRef.current = true
    setError('')
    if (logs.length === 0 && !refresh) setIsInitialLoading(true)
    if (refresh) setIsRefreshing(true)
    try {
      const response = await getSecurityLogs(buildRequestParams(filters, page, size), { signal: controller.signal })
      if (requestId !== requestIdRef.current) return
      const payload = normalizeSecurityLogPage(response)
      setLogs(payload.items)
      setPageInfo(payload.pageInfo)
      setLastUpdatedAt(new Date().toISOString())
      setSelectedLog((current) => current && payload.items.find((item) => (item.id || item.traceId) === (current.id || current.traceId)) ? current : null)
    } catch (requestError) {
      if (requestError.name === 'AbortError') return
      if (requestId !== requestIdRef.current) return
      setError(requestError.message || 'Backend hoặc Elasticsearch không khả dụng.')
    } finally {
      if (requestId === requestIdRef.current) {
        setIsInitialLoading(false)
        setIsRefreshing(false)
        inFlightRef.current = false
      }
    }
  }, [appliedFilters, logs.length, pageInfo.number, pageInfo.size])

  useEffect(() => {
    loadLogs({ page: 0, size: DEFAULT_PAGE_SIZE })
    return () => abortRef.current?.abort()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!autoRefreshMs) return undefined
    const intervalId = window.setInterval(() => {
      if (!inFlightRef.current) loadLogs({ refresh: true })
    }, autoRefreshMs)
    return () => window.clearInterval(intervalId)
  }, [autoRefreshMs, loadLogs])

  useEffect(() => {
    if (!copyMessage) return undefined
    const timeoutId = window.setTimeout(() => setCopyMessage(''), 1800)
    return () => window.clearTimeout(timeoutId)
  }, [copyMessage])

  const applyFilters = () => {
    setAppliedFilters(draftFilters)
    setSelectedLog(null)
    loadLogs({ page: 0, size: pageInfo.size, filters: draftFilters })
  }

  const resetFilters = () => {
    setDraftFilters(DEFAULT_FILTERS)
    setAppliedFilters(DEFAULT_FILTERS)
    setSelectedLog(null)
    loadLogs({ page: 0, size: pageInfo.size, filters: DEFAULT_FILTERS })
  }

  const copyValue = async (value) => {
    try {
      if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(value)
      setCopyMessage('Đã copy vào clipboard')
    } catch {
      setCopyMessage('Không thể copy tự động')
    }
  }

  const busy = isInitialLoading || isRefreshing

  return (
    <main className="security-log-page">
      <div className="security-log-container">
        <header className="security-log-page__header">
          <div>
            <h1>Nhật ký hệ thống</h1>
            <p>Tra cứu security logs từ Elasticsearch theo thời gian, service, endpoint và trace.</p>
          </div>
          {isRefreshing ? <span className="security-log-page__refreshing">Đang refresh...</span> : null}
        </header>
        <div className="security-log-page__layout">
          <SecurityLogFilters filters={draftFilters} onChange={setDraftFilters} onApply={applyFilters} onReset={resetFilters} disabled={busy} />
          <section className="security-log-page__main">
            <SecurityLogToolbar
              totalElements={pageInfo.totalElements}
              lastUpdatedAt={lastUpdatedAt}
              autoRefreshMs={autoRefreshMs}
              onAutoRefreshChange={setAutoRefreshMs}
              onRefresh={() => loadLogs({ refresh: true })}
              isBusy={busy}
            />
            <SecurityLogTable logs={logs} selectedLog={selectedLog} onSelect={setSelectedLog} isLoading={isInitialLoading} error={error} onRetry={() => loadLogs({ refresh: logs.length > 0 })} />
            <SecurityLogPagination pageInfo={pageInfo} onPageChange={(page) => loadLogs({ page })} onSizeChange={(size) => loadLogs({ page: 0, size })} disabled={busy} />
          </section>
          <SecurityLogDetail log={selectedLog} onClose={() => setSelectedLog(null)} onCopy={copyValue} copyMessage={copyMessage} />
        </div>
      </div>
    </main>
  )
}
