/* eslint-disable react-hooks/set-state-in-effect */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { getAnomalies, getAnomalyDetail, getAnomalyStatistics } from '../services/anomalies'
import { DEFAULT_ANOMALY_FILTERS, DEFAULT_ANOMALY_PAGE_SIZE } from '../pages/anomaly-management/anomalyConstants'
import { normalizeAnomalyDetail, normalizeAnomalyPage, normalizeAnomalyStatistics, toIsoInstantFromInput } from '../pages/anomaly-management/anomalyMappers'

const initialPageInfo = {
  number: 0,
  size: DEFAULT_ANOMALY_PAGE_SIZE,
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
    sort: 'timestamp',
    direction: 'DESC',
  }
}

function validateFilters(filters) {
  const minRiskScore = filters.minRiskScore === '' ? null : Number(filters.minRiskScore)
  const maxRiskScore = filters.maxRiskScore === '' ? null : Number(filters.maxRiskScore)
  if (minRiskScore !== null && (!Number.isFinite(minRiskScore) || minRiskScore < 0 || minRiskScore > 100)) return 'Risk score nhỏ nhất phải nằm trong khoảng 0..100.'
  if (maxRiskScore !== null && (!Number.isFinite(maxRiskScore) || maxRiskScore < 0 || maxRiskScore > 100)) return 'Risk score lớn nhất phải nằm trong khoảng 0..100.'
  if (minRiskScore !== null && maxRiskScore !== null && minRiskScore > maxRiskScore) return 'Risk score nhỏ nhất không được lớn hơn risk score lớn nhất.'
  return ''
}

export default function useAnomalyManagement() {
  const [draftFilters, setDraftFilters] = useState(DEFAULT_ANOMALY_FILTERS)
  const [appliedFilters, setAppliedFilters] = useState(DEFAULT_ANOMALY_FILTERS)
  const [items, setItems] = useState([])
  const [pageInfo, setPageInfo] = useState(initialPageInfo)
  const [statistics, setStatistics] = useState(() => normalizeAnomalyStatistics(null))
  const [selectedAnomaly, setSelectedAnomaly] = useState(null)
  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [isInitialLoading, setIsInitialLoading] = useState(true)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [statisticsLoading, setStatisticsLoading] = useState(true)
  const [error, setError] = useState('')
  const [statisticsError, setStatisticsError] = useState('')
  const [detailError, setDetailError] = useState('')
  const [lastUpdatedAt, setLastUpdatedAt] = useState('')
  const [autoRefreshMs, setAutoRefreshMs] = useState(0)

  const requestIdRef = useRef(0)
  const abortRef = useRef(null)
  const detailAbortRef = useRef(null)
  const inFlightRef = useRef(false)

  const filterValidationError = useMemo(() => validateFilters(draftFilters), [draftFilters])

  const loadData = useCallback(async ({ page = pageInfo.number, size = pageInfo.size, filters = appliedFilters, refresh = false } = {}) => {
    if (inFlightRef.current && refresh) return
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    const requestId = requestIdRef.current + 1
    requestIdRef.current = requestId
    inFlightRef.current = true
    setError('')
    setStatisticsError('')
    if (items.length === 0 && !refresh) setIsInitialLoading(true)
    if (refresh) setIsRefreshing(true)
    setStatisticsLoading(true)

    try {
      const params = buildRequestParams(filters, page, size)
      const [listResponse, statisticsResponse] = await Promise.all([
        getAnomalies(params, { signal: controller.signal }),
        getAnomalyStatistics(params, { signal: controller.signal }),
      ])
      if (requestId !== requestIdRef.current) return
      const payload = normalizeAnomalyPage(listResponse)
      setItems(payload.items)
      setPageInfo(payload.pageInfo)
      setStatistics(normalizeAnomalyStatistics(statisticsResponse))
      setLastUpdatedAt(new Date().toISOString())
      setSelectedAnomaly((current) => {
        if (!current) return null
        const currentKey = current.anomalyId || current.incidentId
        return payload.items.find((item) => (item.anomalyId || item.incidentId) === currentKey) ?? null
      })
    } catch (requestError) {
      if (requestError.name === 'AbortError') return
      if (requestId !== requestIdRef.current) return
      const message = requestError.message || 'Backend hoặc Elasticsearch không khả dụng.'
      setError(message)
      setStatisticsError(message)
    } finally {
      if (requestId === requestIdRef.current) {
        setIsInitialLoading(false)
        setIsRefreshing(false)
        setStatisticsLoading(false)
        inFlightRef.current = false
      }
    }
  }, [appliedFilters, items.length, pageInfo.number, pageInfo.size])

  const selectAnomaly = useCallback(async (anomaly) => {
    const anomalyId = anomaly?.anomalyId || anomaly?.incidentId
    setSelectedAnomaly(anomaly ?? null)
    setDetail(null)
    setDetailError('')
    detailAbortRef.current?.abort()
    if (!anomalyId) return
    const controller = new AbortController()
    detailAbortRef.current = controller
    setDetailLoading(true)
    try {
      const response = await getAnomalyDetail(anomalyId, { signal: controller.signal })
      setDetail(normalizeAnomalyDetail(response))
    } catch (requestError) {
      if (requestError.name === 'AbortError') return
      setDetailError(requestError.message || 'Không thể tải chi tiết bất thường.')
    } finally {
      if (!controller.signal.aborted) setDetailLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData({ page: 0, size: DEFAULT_ANOMALY_PAGE_SIZE })
    return () => {
      abortRef.current?.abort()
      detailAbortRef.current?.abort()
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!autoRefreshMs) return undefined
    const intervalId = window.setInterval(() => {
      if (!inFlightRef.current) loadData({ refresh: true })
    }, autoRefreshMs)
    return () => window.clearInterval(intervalId)
  }, [autoRefreshMs, loadData])

  const applyFilters = () => {
    const validationMessage = validateFilters(draftFilters)
    if (validationMessage) {
      setError(validationMessage)
      return
    }
    setAppliedFilters(draftFilters)
    setSelectedAnomaly(null)
    setDetail(null)
    loadData({ page: 0, size: pageInfo.size, filters: draftFilters })
  }

  const resetFilters = () => {
    setDraftFilters(DEFAULT_ANOMALY_FILTERS)
    setAppliedFilters(DEFAULT_ANOMALY_FILTERS)
    setSelectedAnomaly(null)
    setDetail(null)
    loadData({ page: 0, size: pageInfo.size, filters: DEFAULT_ANOMALY_FILTERS })
  }

  return {
    draftFilters,
    setDraftFilters,
    appliedFilters,
    items,
    pageInfo,
    statistics,
    selectedAnomaly,
    detail,
    detailLoading,
    isInitialLoading,
    isRefreshing,
    statisticsLoading,
    error,
    statisticsError,
    detailError,
    lastUpdatedAt,
    autoRefreshMs,
    filterValidationError,
    applyFilters,
    resetFilters,
    changePage: (page) => loadData({ page }),
    changePageSize: (size) => loadData({ page: 0, size }),
    refresh: () => loadData({ refresh: true }),
    selectAnomaly,
    closeDetail: () => {
      detailAbortRef.current?.abort()
      setSelectedAnomaly(null)
      setDetail(null)
      setDetailError('')
      setDetailLoading(false)
    },
    setAutoRefreshMs,
  }
}
