import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { apiGet, apiPatch, apiPost, apiPut } from "../../../services/api";
import "./SettingDetailServicePage.css";

const ALERT_SEVERITIES = ["INFO", "WARNING", "CRITICAL"];
const ALERT_CHANNELS = ["SLACK", "EMAIL", "WEBHOOK"];
const ROLLBACK_STRATEGIES = ["IGNORE", "COMPENSATE"];
const ENDPOINT_TYPES = ["INBOUND", "OUTBOUND"];

const INBOUND_FIELDS = [
  "rateLimit",
  "rateLimitWindowSeconds",
  "timeoutMs",
  "requestSizeLimitKb",
  "responseSizeLimitKb",
  "responseTimeThresholdMs",
  "logRetentionDays",
];

const OUTBOUND_FIELDS = [
  "timeoutMs",
  "retryCount",
  "retryBackoffMs",
  "responseTimeThresholdMs",
  "logRetentionDays",
];

const TEMPLATE_NUMERIC_FIELDS = [
  "inboundRateLimit",
  "inboundRateLimitWindowSeconds",
  "inboundTimeoutMs",
  "inboundRequestSizeLimitKb",
  "inboundResponseSizeLimitKb",
  "inboundResponseTimeThresholdMs",
  "inboundLogRetentionDays",
  "outboundTimeoutMs",
  "outboundRetryCount",
  "outboundRetryBackoffMs",
  "outboundResponseTimeThresholdMs",
  "outboundLogRetentionDays",
  "alertThrottleMinutes",
];

function unwrapResponse(response) {
  if (
    response &&
    typeof response === "object" &&
    response.status !== undefined &&
    response.status !== 200
  ) {
    throw new Error(response.message || "API request failed");
  }
  return response?.data ?? response;
}

function extractList(response) {
  const data = unwrapResponse(response);
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.content)) return data.content;
  return [];
}

function toInputValue(value) {
  if (value === undefined || value === null) return "";
  return String(value);
}

function endpointId(endpoint) {
  return endpoint?.id ?? endpoint?.endpointId;
}

function normalizeService(service) {
  if (!service) return service;
  return {
    ...service,
    status: service.status || "ACTIVE",
  };
}

function normalizeEndpoint(endpoint) {
  if (!endpoint) return endpoint;
  return {
    ...endpoint,
    status: endpoint.status || "ACTIVE",
  };
}

function normalizeChannels(channels) {
  if (Array.isArray(channels)) {
    return channels
      .map((channel) => String(channel).trim().toUpperCase())
      .filter(Boolean);
  }

  if (typeof channels === "string") {
    const normalized = channels.trim();
    if (!normalized) return [];

    try {
      const parsed = JSON.parse(normalized);
      if (Array.isArray(parsed)) return normalizeChannels(parsed);
    } catch {
      // Fall back to comma/space separated values from older API shapes.
    }

    return normalized
      .split(/[\s,;|]+/)
      .map((channel) => channel.trim().toUpperCase())
      .filter(Boolean);
  }

  return [];
}

function buildEndpointDraft(endpoint, numericFields) {
  const draft = numericFields.reduce(
    (current, field) => ({
      ...current,
      [field]: toInputValue(endpoint?.[field]),
    }),
    {},
  );
  return {
    ...draft,
    rollbackStrategy: endpoint?.rollbackStrategy || "IGNORE",
    alertSeverity: endpoint?.alertSeverity || "CRITICAL",
    alertThrottleMinutes: toInputValue(endpoint?.alertThrottleMinutes),
    alertChannels: normalizeChannels(endpoint?.alertChannels),
  };
}

function buildTemplateDraft(template) {
  if (!template) return null;
  return {
    inboundRateLimit: toInputValue(template.inboundRateLimit),
    inboundRateLimitWindowSeconds: toInputValue(
      template.inboundRateLimitWindowSeconds,
    ),
    inboundTimeoutMs: toInputValue(template.inboundTimeoutMs),
    inboundRequestSizeLimitKb: toInputValue(template.inboundRequestSizeLimitKb),
    inboundResponseSizeLimitKb: toInputValue(
      template.inboundResponseSizeLimitKb,
    ),
    inboundResponseTimeThresholdMs: toInputValue(
      template.inboundResponseTimeThresholdMs,
    ),
    inboundLogRetentionDays: toInputValue(template.inboundLogRetentionDays),
    outboundTimeoutMs: toInputValue(template.outboundTimeoutMs),
    outboundRetryCount: toInputValue(template.outboundRetryCount),
    outboundRetryBackoffMs: toInputValue(template.outboundRetryBackoffMs),
    outboundResponseTimeThresholdMs: toInputValue(
      template.outboundResponseTimeThresholdMs,
    ),
    outboundLogRetentionDays: toInputValue(template.outboundLogRetentionDays),
    outboundRollbackStrategy:
      template.outboundRollbackStrategy || "IGNORE",
    alertSeverity: template.alertSeverity || "CRITICAL",
    alertThrottleMinutes: toInputValue(template.alertThrottleMinutes),
    alertChannels: normalizeChannels(template.alertChannels),
  };
}

function parseNonNegativeInteger(value, label) {
  if (value === "" || value === undefined || value === null) {
    throw new Error(`${label} là bắt buộc.`);
  }
  if (!/^\d+$/.test(String(value))) {
    throw new Error(`${label} phải là số nguyên không âm.`);
  }
  return Number(value);
}

function ensureOption(value, options, label) {
  if (!options.includes(value)) {
    throw new Error(`${label} không hợp lệ.`);
  }
  return value;
}

function validateChannels(channels) {
  if (!Array.isArray(channels)) return [];
  const normalized = normalizeChannels(channels);
  const unsupported = normalized.find(
    (channel) => !ALERT_CHANNELS.includes(channel),
  );
  if (unsupported)
    throw new Error(`Kênh cảnh báo ${unsupported} không hợp lệ.`);
  return normalized;
}

function formatDateTime(value) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString("vi-VN");
}

function hasRejectedResult(results) {
  return results.some((result) => result.status === "rejected");
}

function statusClass(status) {
  if (status === "ACTIVE")
    return "setting-detail-status setting-detail-status--active";
  if (status === "DEPRECATED")
    return "setting-detail-status setting-detail-status--maintenance";
  return "setting-detail-status setting-detail-status--inactive";
}

function methodClass(method) {
  if (method === "GET") return "setting-detail-pill setting-detail-pill--green";
  if (method === "POST") return "setting-detail-pill setting-detail-pill--blue";
  return "setting-detail-pill setting-detail-pill--gray";
}

function ErrorBox({ message, onRetry }) {
  if (!message) return null;
  return (
    <div className="setting-detail-error">
      <span>{message}</span>
      {onRetry ? (
        <button type="button" onClick={onRetry}>
          Thử lại
        </button>
      ) : null}
    </div>
  );
}

function NumericInput({ value, onChange }) {
  return (
    <input
      type="text"
      className="setting-detail-table-input"
      value={value}
      onChange={(event) => onChange(event.target.value.replace(/[^0-9]/g, ""))}
    />
  );
}

export default function SettingDetailServicePage() {
  const { serviceId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const fallbackService = location.state?.service;

  const [service, setService] = useState(normalizeService(fallbackService) || null);
  const [inbounds, setInbounds] = useState([]);
  const [outbounds, setOutbounds] = useState([]);
  const [template, setTemplate] = useState(null);
  const [inboundDrafts, setInboundDrafts] = useState({});
  const [outboundDrafts, setOutboundDrafts] = useState({});
  const [templateDraft, setTemplateDraft] = useState(null);
  const [activeTab, setActiveTab] = useState("inbound");
  const [applyMode, setApplyMode] = useState("new");
  const [applyTypes, setApplyTypes] = useState(ENDPOINT_TYPES);
  const [isLoading, setIsLoading] = useState(true);
  const [isTemplateSubmitting, setIsTemplateSubmitting] = useState(false);
  const [savingInboundId, setSavingInboundId] = useState("");
  const [savingOutboundId, setSavingOutboundId] = useState("");
  const [togglingService, setTogglingService] = useState(false);
  const [togglingEndpointId, setTogglingEndpointId] = useState("");
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState("");

  const serviceTitle =
    service?.name || fallbackService?.name || serviceId || "—";

  const endpointIdsForApply = useMemo(() => {
    if (applyMode !== "bulk") return [];
    const selectedInbounds = applyTypes.includes("INBOUND")
      ? inbounds.map(endpointId)
      : [];
    const selectedOutbounds = applyTypes.includes("OUTBOUND")
      ? outbounds.map(endpointId)
      : [];
    return [...selectedInbounds, ...selectedOutbounds].filter(Boolean);
  }, [applyMode, applyTypes, inbounds, outbounds]);

  const getService = useCallback(
    () => apiGet(`/central/api/configs/services/${serviceId}`),
    [serviceId],
  );
  const getInbounds = useCallback(
    () => apiGet(`/central/api/configs/services/${serviceId}/inbounds`),
    [serviceId],
  );
  const getOutbounds = useCallback(
    () => apiGet(`/central/api/configs/services/${serviceId}/outbounds`),
    [serviceId],
  );
  const getTemplate = useCallback(
    () => apiGet(`/central/api/configs/services/${serviceId}/setting-template`),
    [serviceId],
  );

  const applyInboundRows = useCallback((rows) => {
    const normalizedRows = rows.map(normalizeEndpoint);
    setInbounds(normalizedRows);
    setInboundDrafts(
      Object.fromEntries(
        normalizedRows
          .map((row) => [
            endpointId(row),
            buildEndpointDraft(row, INBOUND_FIELDS),
          ])
          .filter(([id]) => id),
      ),
    );
  }, []);

  const applyOutboundRows = useCallback((rows) => {
    const normalizedRows = rows.map(normalizeEndpoint);
    setOutbounds(normalizedRows);
    setOutboundDrafts(
      Object.fromEntries(
        normalizedRows
          .map((row) => [
            endpointId(row),
            buildEndpointDraft(row, OUTBOUND_FIELDS),
          ])
          .filter(([id]) => id),
      ),
    );
  }, []);

  const applyTemplateState = useCallback((data) => {
    setTemplate(data);
    setTemplateDraft(buildTemplateDraft(data));
  }, []);

  const refreshInbounds = useCallback(async () => {
    const rows = extractList(await getInbounds());
    applyInboundRows(rows);
    setErrors((current) => ({ ...current, inbounds: "" }));
  }, [applyInboundRows, getInbounds]);

  const refreshOutbounds = useCallback(async () => {
    const rows = extractList(await getOutbounds());
    applyOutboundRows(rows);
    setErrors((current) => ({ ...current, outbounds: "" }));
  }, [applyOutboundRows, getOutbounds]);

  const refreshTemplate = useCallback(async () => {
    const data = unwrapResponse(await getTemplate());
    applyTemplateState(data);
    setErrors((current) => ({ ...current, template: "" }));
  }, [applyTemplateState, getTemplate]);

  const loadAll = useCallback(async () => {
    if (!serviceId) {
      setErrors({ service: "Thiếu serviceId trên URL." });
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setMessage("");
    const results = await Promise.allSettled([
      getService(),
      getInbounds(),
      getOutbounds(),
      getTemplate(),
    ]);
    const nextErrors = {};

    if (results[0].status === "fulfilled") {
      try {
        setService(normalizeService(unwrapResponse(results[0].value)));
      } catch (error) {
        nextErrors.service =
          error.message || "Không thể tải thông tin service.";
      }
    } else {
      nextErrors.service = "Không thể tải thông tin service.";
    }

    if (results[1].status === "fulfilled") {
      try {
        applyInboundRows(extractList(results[1].value));
      } catch (error) {
        nextErrors.inbounds =
          error.message || "Không thể tải inbound endpoints.";
      }
    } else {
      nextErrors.inbounds = "Không thể tải inbound endpoints.";
    }

    if (results[2].status === "fulfilled") {
      try {
        applyOutboundRows(extractList(results[2].value));
      } catch (error) {
        nextErrors.outbounds =
          error.message || "Không thể tải outbound endpoints.";
      }
    } else {
      nextErrors.outbounds = "Không thể tải outbound endpoints.";
    }

    if (results[3].status === "fulfilled") {
      try {
        applyTemplateState(unwrapResponse(results[3].value));
      } catch (error) {
        nextErrors.template =
          error.message || "Không thể tải setting template.";
      }
    } else {
      nextErrors.template = "Không thể tải setting template.";
    }

    setErrors(nextErrors);
    setIsLoading(false);
  }, [
    applyInboundRows,
    applyOutboundRows,
    applyTemplateState,
    getInbounds,
    getOutbounds,
    getService,
    getTemplate,
    serviceId,
  ]);

  useEffect(() => {
    let isCurrent = true;

    Promise.resolve()
      .then(() => {
        if (isCurrent) return loadAll();
        return undefined;
      })
      .catch((error) => {
        if (isCurrent) {
          setErrors({
            page: error.message || "Không thể tải dữ liệu cấu hình.",
          });
          setIsLoading(false);
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [loadAll]);

  function buildInboundBody(draft) {
    const body = Object.fromEntries(
      INBOUND_FIELDS.map((field) => [
        field,
        parseNonNegativeInteger(draft[field], field),
      ]),
    );
    return {
      ...body,
      alertSeverity: ensureOption(
        draft.alertSeverity,
        ALERT_SEVERITIES,
        "Severity",
      ),
      alertThrottleMinutes: parseNonNegativeInteger(
        draft.alertThrottleMinutes,
        "alertThrottleMinutes",
      ),
      alertChannels: validateChannels(draft.alertChannels),
    };
  }

  function buildOutboundBody(draft) {
    const body = Object.fromEntries(
      OUTBOUND_FIELDS.map((field) => [
        field,
        parseNonNegativeInteger(draft[field], field),
      ]),
    );
    return {
      ...body,
      rollbackStrategy: ensureOption(
        draft.rollbackStrategy,
        ROLLBACK_STRATEGIES,
        "Rollback strategy",
      ),
      alertSeverity: ensureOption(
        draft.alertSeverity,
        ALERT_SEVERITIES,
        "Severity",
      ),
      alertThrottleMinutes: parseNonNegativeInteger(
        draft.alertThrottleMinutes,
        "alertThrottleMinutes",
      ),
      alertChannels: validateChannels(draft.alertChannels),
    };
  }

  function buildTemplateBody() {
    if (!template || !templateDraft) throw new Error("Template chưa được tải.");
    const numericValues = Object.fromEntries(
      TEMPLATE_NUMERIC_FIELDS.map((field) => [
        field,
        parseNonNegativeInteger(templateDraft[field], field),
      ]),
    );
    return {
      ...numericValues,
      expectedVersion: template.version,
      outboundRollbackStrategy: ensureOption(
        templateDraft.outboundRollbackStrategy,
        ROLLBACK_STRATEGIES,
        "Rollback strategy",
      ),
      alertSeverity: ensureOption(
        templateDraft.alertSeverity,
        ALERT_SEVERITIES,
        "Severity",
      ),
      alertChannels: validateChannels(templateDraft.alertChannels),
    };
  }

  async function saveInbound(row) {
    const id = endpointId(row);
    setSavingInboundId(id);
    setMessage("");
    try {
      const body = buildInboundBody(inboundDrafts[id]);
      unwrapResponse(
        await apiPatch(`/central/api/configs/inbounds/${id}/settings`, body),
      );
      await refreshInbounds();
      setMessage("Lưu inbound endpoint thành công.");
    } catch (error) {
      setMessage(error.message || "Lưu inbound endpoint thất bại.");
    } finally {
      setSavingInboundId("");
    }
  }

  async function saveOutbound(row) {
    const id = endpointId(row);
    setSavingOutboundId(id);
    setMessage("");
    try {
      const body = buildOutboundBody(outboundDrafts[id]);
      unwrapResponse(
        await apiPatch(`/central/api/configs/outbounds/${id}/settings`, body),
      );
      await refreshOutbounds();
      setMessage("Lưu outbound endpoint thành công.");
    } catch (error) {
      setMessage(error.message || "Lưu outbound endpoint thất bại.");
    } finally {
      setSavingOutboundId("");
    }
  }

  async function submitTemplate() {
    setIsTemplateSubmitting(true);
    setMessage("");
    try {
      if (applyMode === "bulk") {
        const templateBody = buildTemplateBody();
        const savedTemplate = unwrapResponse(
          await apiPut(
            `/central/api/configs/services/${serviceId}/setting-template`,
            templateBody,
          ),
        );
        const applyResponse = unwrapResponse(
          await apiPost(
            `/central/api/configs/services/${serviceId}/setting-template/apply-to-endpoints`,
            {
              endpointTypes: applyTypes,
              endpointIds: endpointIdsForApply,
              expectedTemplateVersion: savedTemplate?.version ?? template.version,
            },
          ),
        );
        const refreshResults = await Promise.allSettled([
          refreshTemplate(),
          refreshInbounds(),
          refreshOutbounds(),
        ]);
        const refreshWarning = hasRejectedResult(refreshResults)
          ? " Một số dữ liệu chưa tải lại được, vui lòng thử lại."
          : "";
        setMessage(
          `${applyResponse?.message || "Đã lưu và áp dụng template cho endpoint."}${refreshWarning}`,
        );
        return;
      }

      const response = await apiPut(
        `/central/api/configs/services/${serviceId}/setting-template`,
        buildTemplateBody(),
      );
      applyTemplateState(unwrapResponse(response));
      setMessage("Lưu cấu hình mẫu cho service mới thành công.");
    } catch (error) {
      if (applyMode === "bulk") {
        const refreshResults = await Promise.allSettled([
          refreshTemplate(),
          refreshInbounds(),
          refreshOutbounds(),
        ]);
        const refreshWarning = hasRejectedResult(refreshResults)
          ? " Đồng thời không thể tải lại toàn bộ dữ liệu."
          : "";
        setMessage(
          `${error.message || "Áp dụng template thất bại."}${refreshWarning}`,
        );
      } else {
        setMessage(
          error.message ||
            "Lưu cấu hình mẫu thất bại. Vui lòng tải lại template và thử lại.",
        );
      }
    } finally {
      setIsTemplateSubmitting(false);
    }
  }

  async function toggleServiceStatus() {
    if (!service?.id || togglingService) return;
    const nextStatus = service.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    setTogglingService(true);
    setMessage("");
    try {
      const updatedService = normalizeService(unwrapResponse(
        await apiPatch(`/central/api/configs/services/${service.id}/status`, {
          status: nextStatus,
        }),
      ));
      setService(updatedService);
      await Promise.allSettled([refreshInbounds(), refreshOutbounds()]);
      setMessage(`Đã ${nextStatus === "ACTIVE" ? "bật" : "tắt"} service.`);
    } catch (error) {
      setMessage(error.message || "Cập nhật trạng thái service thất bại.");
    } finally {
      setTogglingService(false);
    }
  }

  async function toggleEndpointStatus(type, row) {
    const id = endpointId(row);
    if (!id || togglingEndpointId) return;
    const nextStatus = row.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    const currentServiceStatus = row.serviceStatus || service?.status || "ACTIVE";
    if (nextStatus === "ACTIVE" && currentServiceStatus !== "ACTIVE") {
      setMessage("Không thể bật endpoint khi service đang tắt.");
      return;
    }
    setTogglingEndpointId(id);
    setMessage("");
    try {
      unwrapResponse(
        await apiPatch(`/central/api/configs/${type}s/${id}/status`, {
          status: nextStatus,
        }),
      );
      if (type === "inbound") {
        await refreshInbounds();
      } else {
        await refreshOutbounds();
      }
      setMessage(`Đã ${nextStatus === "ACTIVE" ? "bật" : "tắt"} endpoint.`);
    } catch (error) {
      setMessage(error.message || "Cập nhật trạng thái endpoint thất bại.");
    } finally {
      setTogglingEndpointId("");
    }
  }

  function updateInboundDraft(id, field, value) {
    setInboundDrafts((current) => ({
      ...current,
      [id]: { ...current[id], [field]: value },
    }));
  }

  function updateOutboundDraft(id, field, value) {
    setOutboundDrafts((current) => ({
      ...current,
      [id]: { ...current[id], [field]: value },
    }));
  }

  function updateTemplateDraft(field, value) {
    setTemplateDraft((current) => ({ ...current, [field]: value }));
  }

  function toggleChannels(draft, setter, channel) {
    const channels = draft.alertChannels || [];
    const nextChannels = channels.includes(channel)
      ? channels.filter((item) => item !== channel)
      : [...channels, channel];
    setter(nextChannels);
  }

  function toggleApplyType(type) {
    setApplyTypes((current) => {
      const next = current.includes(type)
        ? current.filter((item) => item !== type)
        : [...current, type];
      return next.length > 0 ? next : current;
    });
  }

  return (
    <section className="setting-detail-page">
      <div className="setting-detail-container">
        <ErrorBox message={errors.page} onRetry={loadAll} />

        <header className="setting-detail-header">
          <button
            type="button"
            className="setting-detail-back"
            onClick={() => navigate("/settings-management")}
          >
            <span className="material-symbols-outlined">arrow_back</span>
            Quay lại
          </button>
          <div className="setting-detail-heading-row">
            <div>
              <h2>Chi tiết Service: {serviceTitle}</h2>
              <p>
                {service?.description ||
                  fallbackService?.description ||
                  "Quản lý và giám sát các điểm cuối inbound và outbound của service."}
              </p>
            </div>
            <div className="setting-detail-actions">
              <button
                type="button"
                className={service?.status === "ACTIVE" ? "setting-detail-toggle setting-detail-toggle--inactive" : "setting-detail-toggle setting-detail-toggle--active"}
                onClick={toggleServiceStatus}
                disabled={!service?.id || service?.status === "DEPRECATED" || togglingService}
              >
                {togglingService ? "Đang cập nhật..." : service?.status === "ACTIVE" ? "Tắt service" : "Bật service"}
              </button>
              <button type="button" className="setting-detail-action-secondary" disabled>
                Export JSON
              </button>
            </div>
          </div>
        </header>

        {isLoading ? (
          <div className="setting-detail-loading">
            Đang tải dữ liệu cấu hình...
          </div>
        ) : null}

        <ErrorBox message={errors.service} onRetry={loadAll} />
        <section className="setting-detail-card setting-detail-metadata">
          {[
            ["ID", service?.id || serviceId],
            ["Name", service?.name || "—"],
            ["Description", service?.description || "—"],
            ["Base URL", service?.baseUrl || "—"],
            ["Created At", formatDateTime(service?.createdAt)],
            ["Updated At", formatDateTime(service?.updatedAt)],
            ["Inbound Count", service?.inboundCount ?? inbounds.length],
            ["Outbound Count", service?.outboundCount ?? outbounds.length],
          ].map(([label, value]) => (
            <div
              key={label}
              className={
                label === "Description"
                  ? "setting-detail-meta-item setting-detail-meta-item--wide"
                  : "setting-detail-meta-item"
              }
            >
              <span>{label}</span>
              <strong>{value}</strong>
            </div>
          ))}
          <div className="setting-detail-meta-item">
            <span>Status</span>
            <strong className={statusClass(service?.status)}>
              {service?.status || "UNKNOWN"}
            </strong>
          </div>
        </section>

        <nav className="setting-detail-tabs">
          <button
            type="button"
            className={activeTab === "inbound" ? "active" : ""}
            onClick={() => setActiveTab("inbound")}
          >
            Danh sách Inbound Endpoints
          </button>
          <button
            type="button"
            className={activeTab === "outbound" ? "active" : ""}
            onClick={() => setActiveTab("outbound")}
          >
            Danh sách Outbound Endpoints
          </button>
        </nav>

        {activeTab === "inbound" ? (
          <EndpointTable
            type="inbound"
            rows={inbounds}
            drafts={inboundDrafts}
            error={errors.inbounds}
            onRetry={refreshInbounds}
            onDraftChange={updateInboundDraft}
            onSave={saveInbound}
            savingId={savingInboundId}
            toggleChannels={toggleChannels}
            onToggleStatus={toggleEndpointStatus}
            togglingStatusId={togglingEndpointId}
            serviceStatus={service?.status}
          />
        ) : (
          <EndpointTable
            type="outbound"
            rows={outbounds}
            drafts={outboundDrafts}
            error={errors.outbounds}
            onRetry={refreshOutbounds}
            onDraftChange={updateOutboundDraft}
            onSave={saveOutbound}
            savingId={savingOutboundId}
            toggleChannels={toggleChannels}
            onToggleStatus={toggleEndpointStatus}
            togglingStatusId={togglingEndpointId}
            serviceStatus={service?.status}
          />
        )}

        <TemplateForm
          template={template}
          draft={templateDraft}
          error={errors.template}
          applyMode={applyMode}
          applyTypes={applyTypes}
          isSubmitting={isTemplateSubmitting}
          message={message}
          endpointCount={endpointIdsForApply.length}
          onRetry={refreshTemplate}
          onChange={updateTemplateDraft}
          onSubmit={submitTemplate}
          onApplyModeChange={setApplyMode}
          onToggleApplyType={toggleApplyType}
          toggleChannels={toggleChannels}
        />
      </div>
    </section>
  );
}

function EndpointTable({
  type,
  rows,
  drafts,
  error,
  onRetry,
  onDraftChange,
  onSave,
  savingId,
  toggleChannels,
  onToggleStatus,
  togglingStatusId,
  serviceStatus,
}) {
  const isInbound = type === "inbound";
  const numericFields = isInbound ? INBOUND_FIELDS : OUTBOUND_FIELDS;
  const colSpan = isInbound ? 17 : 16;

  return (
    <section className="setting-detail-card setting-detail-table-card">
      <ErrorBox message={error} onRetry={onRetry} />
      <div className="setting-detail-table-wrap">
        <table className="setting-detail-table">
          <thead>
            <tr>
              <th className="setting-detail-endpoint-id">ID</th>
              <th>Tên Endpoint</th>
              <th>
                {isInbound ? "Đường dẫn / Chủ đề" : "Đường dẫn đích / Chủ đề"}
              </th>
              <th>Phương thức</th>
              <th>Loại</th>
              <th>Status</th>
              {numericFields.map((field) => (
                <th key={field}>{field}</th>
              ))}
              {!isInbound ? <th>rollbackStrategy</th> : null}
              <th>Severity</th>
              <th>Throttle</th>
              <th>Channels</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => {
              const id = endpointId(row);
              const draft =
                drafts[id] || buildEndpointDraft(row, numericFields);
              return (
                <tr key={id}>
                  <td className="setting-detail-endpoint-id">
                    <code>{id || "—"}</code>
                  </td>
                  <td>
                    <strong>{row.name || "—"}</strong>
                  </td>
                  <td>
                    <code>{row.path || row.targetUrl || row.topic || "—"}</code>
                  </td>
                  <td>
                    <span className={methodClass(row.method)}>
                      {row.method || "—"}
                    </span>
                  </td>
                  <td>{row.protocol || row.type || "—"}</td>
                  <td>
                    <span className={statusClass(row.status)}>{row.status || "ACTIVE"}</span>
                  </td>
                  {numericFields.map((field) => (
                    <td key={field}>
                      <NumericInput
                        value={draft[field] || ""}
                        onChange={(value) => onDraftChange(id, field, value)}
                      />
                    </td>
                  ))}
                  {!isInbound ? (
                    <td>
                      <select
                        value={draft.rollbackStrategy || ""}
                        onChange={(event) =>
                          onDraftChange(
                            id,
                            "rollbackStrategy",
                            event.target.value,
                          )
                        }
                      >
                        {ROLLBACK_STRATEGIES.map((option) => (
                          <option key={option}>{option}</option>
                        ))}
                      </select>
                    </td>
                  ) : null}
                  <td>
                    <select
                      value={draft.alertSeverity || ""}
                      onChange={(event) =>
                        onDraftChange(id, "alertSeverity", event.target.value)
                      }
                    >
                      {ALERT_SEVERITIES.map((option) => (
                        <option key={option}>{option}</option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <NumericInput
                      value={draft.alertThrottleMinutes || ""}
                      onChange={(value) =>
                        onDraftChange(id, "alertThrottleMinutes", value)
                      }
                    />
                  </td>
                  <td>
                    <div className="setting-detail-channel-group">
                      {ALERT_CHANNELS.map((channel) => (
                        <button
                          key={channel}
                          type="button"
                          className={
                            (draft.alertChannels || []).includes(channel)
                              ? "setting-detail-channel active"
                              : "setting-detail-channel"
                          }
                          onClick={() =>
                            toggleChannels(
                              draft,
                              (value) =>
                                onDraftChange(id, "alertChannels", value),
                              channel,
                            )
                          }
                        >
                          {channel.toLowerCase()}
                        </button>
                      ))}
                    </div>
                  </td>
                  <td>
                    <div className="setting-detail-row-actions">
                      {(() => {
                        const currentServiceStatus = row.serviceStatus || serviceStatus || "ACTIVE";
                        const cannotActivateBecauseServiceInactive =
                          row.status !== "ACTIVE" && currentServiceStatus !== "ACTIVE";
                        const cannotActivateBecauseEndpointRemoved =
                          row.status !== "ACTIVE" && row.enabled !== true;
                        const disabled =
                          togglingStatusId === id ||
                          !id ||
                          cannotActivateBecauseServiceInactive ||
                          cannotActivateBecauseEndpointRemoved;
                        const title = cannotActivateBecauseServiceInactive
                          ? "Service đang tắt nên không thể bật endpoint"
                          : cannotActivateBecauseEndpointRemoved
                            ? "Endpoint không còn trong code nên không thể bật"
                            : undefined;

                        return (
                      <button
                        type="button"
                        className={row.status === "ACTIVE" ? "setting-detail-toggle-row setting-detail-toggle-row--danger" : "setting-detail-toggle-row setting-detail-toggle-row--success"}
                        onClick={() => onToggleStatus(type, row)}
                        disabled={disabled}
                        title={title}
                      >
                        {togglingStatusId === id ? "Đang cập nhật..." : row.status === "ACTIVE" ? "Tắt" : "Bật"}
                      </button>
                        );
                      })()}
                    <button
                      type="button"
                      className="setting-detail-save-row"
                      onClick={() => onSave(row)}
                      disabled={savingId === id || !id}
                    >
                      {savingId === id ? "Đang lưu..." : "Lưu"}
                    </button>
                    </div>
                  </td>
                </tr>
              );
            })}
            {!error && rows.length === 0 ? (
              <tr>
                <td colSpan={colSpan} className="setting-detail-empty">
                  Không có {isInbound ? "inbound" : "outbound"} endpoint.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function TemplateForm({
  template,
  draft,
  error,
  applyMode,
  applyTypes,
  isSubmitting,
  message,
  endpointCount,
  onRetry,
  onChange,
  onSubmit,
  onApplyModeChange,
  onToggleApplyType,
  toggleChannels,
}) {
  return (
    <section className="setting-detail-card setting-detail-template">
      <div className="setting-detail-template-header">
        <h3>Cài đặt ngưỡng chung (Service Level)</h3>
        <span>Version: {template?.version ?? "—"}</span>
      </div>
      <ErrorBox message={error} onRetry={onRetry} />
      {!draft ? (
        <p className="setting-detail-empty">Chưa có dữ liệu template.</p>
      ) : (
        <>
          <div className="setting-detail-template-grid">
            <TemplateSection
              title="Inbound Configuration"
              fields={[
                ["inboundRateLimit", "Rate Limit"],
                ["inboundRateLimitWindowSeconds", "Window (s)"],
                ["inboundTimeoutMs", "Timeout (ms)"],
                ["inboundRequestSizeLimitKb", "Request Size Limit (KB)"],
                ["inboundResponseSizeLimitKb", "Response Size Limit (KB)"],
                [
                  "inboundResponseTimeThresholdMs",
                  "Response Time Threshold (ms)",
                ],
                ["inboundLogRetentionDays", "Log Retention (days)"],
              ]}
              draft={draft}
              onChange={onChange}
            />
            <TemplateSection
              title="Outbound Configuration"
              fields={[
                ["outboundTimeoutMs", "Timeout (ms)"],
                ["outboundRetryCount", "Retry Count"],
                ["outboundRetryBackoffMs", "Retry Backoff (ms)"],
                [
                  "outboundResponseTimeThresholdMs",
                  "Response Time Threshold (ms)",
                ],
                ["outboundLogRetentionDays", "Log Retention (days)"],
              ]}
              draft={draft}
              onChange={onChange}
            >
              <label className="setting-detail-template-row">
                <span>Rollback Strategy</span>
                <select
                  value={draft.outboundRollbackStrategy || ""}
                  onChange={(event) =>
                    onChange("outboundRollbackStrategy", event.target.value)
                  }
                >
                  {ROLLBACK_STRATEGIES.map((option) => (
                    <option key={option}>{option}</option>
                  ))}
                </select>
              </label>
            </TemplateSection>
            <div className="setting-detail-template-box">
              <h4>Alert Configuration</h4>
              <label className="setting-detail-template-row">
                <span>Severity</span>
                <select
                  value={draft.alertSeverity || ""}
                  onChange={(event) =>
                    onChange("alertSeverity", event.target.value)
                  }
                >
                  {ALERT_SEVERITIES.map((option) => (
                    <option key={option}>{option}</option>
                  ))}
                </select>
              </label>
              <label className="setting-detail-template-row">
                <span>Throttle (minutes)</span>
                <NumericInput
                  value={draft.alertThrottleMinutes || ""}
                  onChange={(value) => onChange("alertThrottleMinutes", value)}
                />
              </label>
              <div className="setting-detail-template-row setting-detail-template-row--stacked">
                <span>Channels</span>
                <div className="setting-detail-channel-group">
                  {ALERT_CHANNELS.map((channel) => (
                    <button
                      key={channel}
                      type="button"
                      className={
                        (draft.alertChannels || []).includes(channel)
                          ? "setting-detail-channel active"
                          : "setting-detail-channel"
                      }
                      onClick={() =>
                        toggleChannels(
                          draft,
                          (value) => onChange("alertChannels", value),
                          channel,
                        )
                      }
                    >
                      {channel.toLowerCase()}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>
          <div className="setting-detail-template-footer">
            <div className="setting-detail-apply-options">
              <label>
                <input
                  type="radio"
                  checked={applyMode === "new"}
                  onChange={() => onApplyModeChange("new")}
                />{" "}
                Áp dụng cho các endpoint mới của service này
              </label>
              <label>
                <input
                  type="radio"
                  checked={applyMode === "bulk"}
                  onChange={() => onApplyModeChange("bulk")}
                />{" "}
                Cập nhật hàng loạt cho endpoint hiện tại ({endpointCount})
              </label>
              {applyMode === "bulk" ? (
                <div className="setting-detail-type-options">
                  {ENDPOINT_TYPES.map((type) => (
                    <label key={type}>
                      <input
                        type="checkbox"
                        checked={applyTypes.includes(type)}
                        onChange={() => onToggleApplyType(type)}
                      />{" "}
                      {type}
                    </label>
                  ))}
                </div>
              ) : null}
            </div>
            <div className="setting-detail-template-actions">
              <button
                type="button"
                className="setting-detail-save-config-button"
                onClick={onSubmit}
                disabled={isSubmitting}
              >
                <span className="material-symbols-outlined">save</span>
                {isSubmitting ? "Đang cập nhật..." : "Lưu cấu hình"}
              </button>
            </div>
          </div>
          {message ? <p className="setting-detail-message">{message}</p> : null}
        </>
      )}
    </section>
  );
}

function TemplateSection({ title, fields, draft, onChange, children }) {
  return (
    <div className="setting-detail-template-box">
      <h4>{title}</h4>
      {fields.map(([field, label]) => (
        <label key={field} className="setting-detail-template-row">
          <span>{label}</span>
          <NumericInput
            value={draft[field] || ""}
            onChange={(value) => onChange(field, value)}
          />
        </label>
      ))}
      {children}
    </div>
  );
}
