import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  applyGlobalTemplateToEndpoints,
  applyGlobalTemplateToServices,
  DEFAULT_GLOBAL_TEMPLATE,
  getGlobalSettings,
  normalizeService,
  numberFromValue,
  saveGlobalTemplate,
  unwrapResponse,
  updateServiceStatus,
} from '../services/settings'

export function useSettingsManagement() {
  const [services, setServices] = useState([])
  const [pageInfo, setPageInfo] = useState({ number: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true })
  const [template, setTemplate] = useState(DEFAULT_GLOBAL_TEMPLATE)
  const [applyMode, setApplyMode] = useState('new')
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [togglingServiceId, setTogglingServiceId] = useState('')
  const [message, setMessage] = useState('')

  const stats = useMemo(() => {
    const active = services.filter((service) => service.status === 'ACTIVE').length
    const maintenance = services.filter((service) => service.status === 'DEPRECATED').length
    const inactive = services.filter((service) => service.status === 'INACTIVE').length

    return {
      total: pageInfo.totalElements || services.length,
      active,
      maintenance,
      inactive,
    }
  }, [pageInfo.totalElements, services])

  const loadSettings = useCallback(async (isMounted = () => true) => {
    setIsLoading(true)
    setMessage('')
    try {
      const [servicesResponse, templateResponse] = await getGlobalSettings()
      if (!isMounted()) return

      const servicesData = unwrapResponse(servicesResponse)
      const templateData = unwrapResponse(templateResponse)

      if (servicesData) {
        setServices((servicesData.content || []).map(normalizeService))
        setPageInfo({
          number: servicesData.number || 0,
          size: servicesData.size || 20,
          totalElements: servicesData.totalElements || 0,
          totalPages: servicesData.totalPages || 0,
          first: Boolean(servicesData.first),
          last: Boolean(servicesData.last),
        })
      }

      if (templateData) setTemplate({ ...DEFAULT_GLOBAL_TEMPLATE, ...templateData })
    } catch (error) {
      if (isMounted()) setMessage(error.message || 'Không thể tải dữ liệu cấu hình.')
    } finally {
      if (isMounted()) setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    let mounted = true
    Promise.resolve()
      .then(() => loadSettings(() => mounted))
      .catch((error) => {
        if (mounted) {
          setMessage(error.message || 'Không thể tải dữ liệu cấu hình.')
          setIsLoading(false)
        }
      })
    return () => {
      mounted = false
    }
  }, [loadSettings])

  function updateTemplate(field, value) {
    setTemplate((current) => ({ ...current, [field]: value }))
  }

  function toggleChannel(channel) {
    setTemplate((current) => {
      const channels = current.alertChannels || []
      const nextChannels = channels.includes(channel) ? channels.filter((item) => item !== channel) : [...channels, channel]
      return { ...current, alertChannels: nextChannels }
    })
  }

  async function handleSave() {
    setIsSaving(true)
    setMessage('')

    const body = {
      expectedVersion: template.version || 0,
      inboundRateLimit: numberFromValue(template.inboundRateLimit),
      inboundRateLimitWindowSeconds: numberFromValue(template.inboundRateLimitWindowSeconds),
      inboundTimeoutMs: numberFromValue(template.inboundTimeoutMs),
      inboundRequestSizeLimitKb: numberFromValue(template.inboundRequestSizeLimitKb),
      inboundResponseSizeLimitKb: numberFromValue(template.inboundResponseSizeLimitKb),
      inboundResponseTimeThresholdMs: numberFromValue(template.inboundResponseTimeThresholdMs),
      inboundLogRetentionDays: numberFromValue(template.inboundLogRetentionDays),
      outboundTimeoutMs: numberFromValue(template.outboundTimeoutMs),
      outboundRetryCount: numberFromValue(template.outboundRetryCount),
      outboundRetryBackoffMs: numberFromValue(template.outboundRetryBackoffMs),
      outboundResponseTimeThresholdMs: numberFromValue(template.outboundResponseTimeThresholdMs),
      outboundLogRetentionDays: numberFromValue(template.outboundLogRetentionDays),
      outboundRollbackStrategy: template.outboundRollbackStrategy || '',
      alertSeverity: template.alertSeverity || '',
      alertThrottleMinutes: numberFromValue(template.alertThrottleMinutes),
      alertChannels: template.alertChannels || [],
    }

    try {
      const response = await saveGlobalTemplate(body)
      const savedTemplate = response?.data
      if (savedTemplate) setTemplate((current) => ({ ...current, ...savedTemplate }))

      if (applyMode === 'bulk') {
        const serviceIds = services.map((service) => service.id).filter(Boolean)
        const expectedTemplateVersion = savedTemplate?.version ?? template.version ?? 0
        if (serviceIds.length > 0) {
          await applyGlobalTemplateToServices({ serviceIds, expectedTemplateVersion })
          await applyGlobalTemplateToEndpoints({ serviceIds, endpointTypes: ['INBOUND', 'OUTBOUND'], expectedTemplateVersion })
        }
      }

      setMessage('Cập nhật cấu hình Global thành công.')
    } catch (error) {
      setMessage(error.message || 'Cập nhật cấu hình Global thất bại.')
    } finally {
      setIsSaving(false)
    }
  }

  async function handleToggleService(service) {
    if (!service?.id || togglingServiceId) return
    const nextStatus = service.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
    setTogglingServiceId(service.id)
    setMessage('')
    try {
      unwrapResponse(await updateServiceStatus(service.id, nextStatus))
      await loadSettings()
      setMessage(`Đã ${nextStatus === 'ACTIVE' ? 'bật' : 'tắt'} service ${service.name || service.id}.`)
    } catch (error) {
      setMessage(error.message || 'Cập nhật trạng thái service thất bại.')
    } finally {
      setTogglingServiceId('')
    }
  }

  return {
    services,
    pageInfo,
    template,
    applyMode,
    isLoading,
    isSaving,
    togglingServiceId,
    message,
    stats,
    setApplyMode,
    updateTemplate,
    toggleChannel,
    handleSave,
    handleToggleService,
  }
}
