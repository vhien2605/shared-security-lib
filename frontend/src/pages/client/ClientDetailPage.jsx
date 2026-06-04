import { useCallback, useEffect, useRef, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import {
  getClient,
  searchServices,
  updateClient,
} from "../../services/clients";
import ClientCredentialBox from "./ClientCredentialBox";
import "./ClientDetailPage.css";

const CLIENT_STATUSES = ["ACTIVE", "INACTIVE", "REVOKED"];
const AUTH_TYPES = ["API_KEY", "HMAC_SIGNATURE"];
const AUTH_ALGORITHMS = ["HmacSHA256"];

const emptyAuthConfig = () => ({
  serviceName: "",
  serviceId: "",
  type: "API_KEY",
  algorithm: "HmacSHA256",
  expiresAt: "",
});

const emptyAuthForm = () => ({
  authConfigs: [emptyAuthConfig()],
});

function unwrapResponse(response) {
  if (!response) throw new Error("Không nhận được phản hồi từ máy chủ.");
  if (
    typeof response === "object" &&
    response.status !== undefined &&
    response.status !== 200
  ) {
    throw new Error(response.message || "API request failed");
  }
  return response?.data ?? response;
}

function normalizeClient(client) {
  const rawAuthConfigs = Array.isArray(client?.authConfigs)
    ? client.authConfigs
    : [];

  return {
    id: client?.id ?? client?.clientId ?? "",
    clientCode: client?.clientCode ?? client?.code ?? client?.clientKey ?? "",
    name: client?.name ?? "",
    description: client?.description ?? "",
    contactEmail: client?.contactEmail ?? "",
    status: client?.status || "UNKNOWN",
    createdAt: client?.createdAt ?? "",
    updatedAt: client?.updatedAt ?? "",
    authConfigs: rawAuthConfigs.map(normalizeAuthConfig),
  };
}

function normalizeAuthConfig(config) {
  return {
    id: config?.id ?? config?.authConfigId ?? "",
    serviceId: config?.serviceId ?? "",
    serviceName: config?.serviceName ?? "",
    type: config?.type || "API_KEY",
    algorithm: config?.algorithm ?? "",
    enabled: Boolean(config?.enabled),
    expiresAt: config?.expiresAt ?? "",
    createdAt: config?.createdAt ?? "",
    updatedAt: config?.updatedAt ?? "",
    disabledAt: config?.disabledAt ?? "",
  };
}

function draftFromClient(client) {
  return {
    name: client?.name || "",
    description: client?.description || "",
    contactEmail: client?.contactEmail || "",
    status: CLIENT_STATUSES.includes(client?.status) ? client.status : "ACTIVE",
  };
}

function formatDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString("vi-VN");
}

function dateInputValue(value) {
  if (!value) return "";
  const text = String(value);
  if (/^\d{4}-\d{2}-\d{2}/.test(text)) return text.slice(0, 10);
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return date.toISOString().slice(0, 10);
}

function toEndOfDay(value) {
  return value ? `${value}T23:59:59` : undefined;
}

function validateEmail(value) {
  if (!value) return true;
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

function validateMetadata(draft) {
  const name = draft.name.trim();
  const contactEmail = draft.contactEmail.trim();

  if (!name) throw new Error("Tên client là bắt buộc.");
  if (contactEmail && !validateEmail(contactEmail)) {
    throw new Error("Email liên hệ không hợp lệ.");
  }
  if (!CLIENT_STATUSES.includes(draft.status)) {
    throw new Error("Trạng thái client không hợp lệ.");
  }

  return {
    name,
    description: draft.description.trim(),
    contactEmail,
    status: draft.status,
  };
}

function buildAddAuthBody(form) {
  if (
    form.authConfigs.some(
      (config) => config.serviceName.trim() && !config.serviceId.trim(),
    )
  ) {
    throw new Error(
      "Vui lòng chọn service từ danh sách gợi ý để tự điền Service ID.",
    );
  }

  const authConfigs = form.authConfigs
    .filter((config) => config.serviceId.trim())
    .map((config) => {
      if (!AUTH_TYPES.includes(config.type))
        throw new Error("Loại auth config không hợp lệ.");
      if (
        config.type === "HMAC_SIGNATURE" &&
        !AUTH_ALGORITHMS.includes(config.algorithm)
      ) {
        throw new Error("Thuật toán HMAC không hợp lệ.");
      }

      const item = {
        serviceId: config.serviceId.trim(),
        type: config.type,
      };
      const expiresAt = toEndOfDay(config.expiresAt);
      if (expiresAt) item.expiresAt = expiresAt;
      if (config.type === "HMAC_SIGNATURE")
        item.algorithm = config.algorithm || "HmacSHA256";
      return item;
    });

  if (authConfigs.length === 0) {
    throw new Error("Vui lòng chọn ít nhất một service để tạo auth config.");
  }

  return { authConfigs: { add: authConfigs } };
}

function statusClass(status) {
  if (status === "ACTIVE")
    return "client-detail-status client-detail-status--active";
  if (status === "REVOKED")
    return "client-detail-status client-detail-status--revoked";
  if (status === "INACTIVE")
    return "client-detail-status client-detail-status--inactive";
  return "client-detail-status";
}

function serviceLabel(config) {
  if (config.serviceName) return config.serviceName;
  return "—";
}

export default function ClientDetailPage() {
  const { clientId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const initialClient = location.state?.client
    ? normalizeClient(location.state.client)
    : null;

  const [client, setClient] = useState(initialClient);
  const [draft, setDraft] = useState(() => draftFromClient(initialClient));
  const [isEditing, setIsEditing] = useState(false);
  const [isLoading, setIsLoading] = useState(!initialClient);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [pendingAuthId, setPendingAuthId] = useState("");
  const [error, setError] = useState("");
  const [formError, setFormError] = useState("");
  const [message, setMessage] = useState("");
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
  const [authForm, setAuthForm] = useState(emptyAuthForm);
  const [authError, setAuthError] = useState("");
  const [createdCredential, setCreatedCredential] = useState(null);
  const [serviceSuggestions, setServiceSuggestions] = useState({});
  const searchRequestRef = useRef(0);

  const loadClient = useCallback(async () => {
    if (!clientId) {
      setError("Thiếu clientId trên đường dẫn.");
      return;
    }

    setIsLoading(true);
    setError("");
    try {
      const response = await getClient(clientId);
      const data = normalizeClient(unwrapResponse(response));
      setClient(data);
      setDraft(draftFromClient(data));
    } catch (requestError) {
      setError(requestError.message || "Không thể tải chi tiết client.");
    } finally {
      setIsLoading(false);
    }
  }, [clientId]);

  useEffect(() => {
    let mounted = true;
    Promise.resolve()
      .then(async () => {
        if (mounted) await loadClient();
      })
      .catch((requestError) => {
        if (mounted) {
          setError(requestError.message || "Không thể tải chi tiết client.");
          setIsLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, [loadClient]);

  useEffect(() => {
    if (!message) return undefined;
    const timeoutId = window.setTimeout(() => setMessage(""), 3000);
    return () => window.clearTimeout(timeoutId);
  }, [message]);

  const updateDraft = useCallback((field, value) => {
    setDraft((current) => ({ ...current, [field]: value }));
  }, []);

  const startEdit = useCallback(() => {
    setDraft(draftFromClient(client));
    setFormError("");
    setIsEditing(true);
  }, [client]);

  const cancelEdit = useCallback(() => {
    setDraft(draftFromClient(client));
    setFormError("");
    setIsEditing(false);
  }, [client]);

  async function submitMetadata(event) {
    event.preventDefault();
    setFormError("");
    setMessage("");
    setIsSubmitting(true);

    try {
      unwrapResponse(await updateClient(clientId, validateMetadata(draft)));
      await loadClient();
      setIsEditing(false);
      setMessage("Đã cập nhật thông tin client thành công.");
    } catch (submitError) {
      setFormError(submitError.message || "Cập nhật client thất bại.");
    } finally {
      setIsSubmitting(false);
    }
  }

  const openAuthModal = useCallback(() => {
    setAuthForm(emptyAuthForm());
    setAuthError("");
    setCreatedCredential(null);
    setServiceSuggestions({});
    setIsAuthModalOpen(true);
  }, []);

  const closeAuthModal = useCallback(() => {
    if (isSubmitting) return;
    setIsAuthModalOpen(false);
    setCreatedCredential(null);
  }, [isSubmitting]);

  const completeAuthModal = useCallback(() => {
    setIsAuthModalOpen(false);
    setCreatedCredential(null);
    setAuthForm(emptyAuthForm());
    setServiceSuggestions({});
    setMessage("Đã thêm auth config thành công.");
  }, []);

  const updateAuthConfig = useCallback((index, field, value) => {
    setAuthForm((current) => {
      return {
        ...current,
        authConfigs: current.authConfigs.map((config, configIndex) => {
          if (configIndex !== index) return config;
          const nextConfig = { ...config, [field]: value };
          if (field === "type" && value === "API_KEY")
            nextConfig.algorithm = "HmacSHA256";
          return nextConfig;
        }),
      };
    });
  }, []);

  const updateServiceName = useCallback(async (index, value) => {
    setAuthForm((current) => ({
      ...current,
      authConfigs: current.authConfigs.map((config, configIndex) =>
        configIndex === index
          ? { ...config, serviceName: value, serviceId: "" }
          : config,
      ),
    }));

    const keyword = value.trim();
    const requestId = searchRequestRef.current + 1;
    searchRequestRef.current = requestId;

    if (!keyword) {
      setServiceSuggestions((current) => ({ ...current, [index]: [] }));
      return;
    }

    try {
      const response = await searchServices({ name: keyword, size: 10 });
      const data = unwrapResponse(response);
      if (searchRequestRef.current !== requestId) return;
      setServiceSuggestions((current) => ({
        ...current,
        [index]: Array.isArray(data) ? data : [],
      }));
    } catch (searchError) {
      if (searchRequestRef.current !== requestId) return;
      setServiceSuggestions((current) => ({ ...current, [index]: [] }));
      setAuthError(searchError.message || "Không thể tìm service.");
    }
  }, []);

  const selectService = useCallback((index, service) => {
    setAuthForm((current) => ({
      ...current,
      authConfigs: current.authConfigs.map((config, configIndex) =>
        configIndex === index
          ? {
              ...config,
              serviceName: service?.name || "",
              serviceId: service?.id || "",
            }
          : config,
      ),
    }));
    setServiceSuggestions((current) => ({ ...current, [index]: [] }));
    setAuthError("");
  }, []);

  const addAuthConfig = useCallback(() => {
    setAuthForm((current) => ({
      ...current,
      authConfigs: [...current.authConfigs, emptyAuthConfig()],
    }));
  }, []);

  const removeAuthConfigDraft = useCallback((index) => {
    setAuthForm((current) => ({
      ...current,
      authConfigs: current.authConfigs.filter(
        (_, configIndex) => configIndex !== index,
      ),
    }));
    setServiceSuggestions((current) =>
      Object.entries(current).reduce((nextSuggestions, [key, value]) => {
        const suggestionIndex = Number(key);
        if (suggestionIndex < index) nextSuggestions[suggestionIndex] = value;
        if (suggestionIndex > index)
          nextSuggestions[suggestionIndex - 1] = value;
        return nextSuggestions;
      }, {}),
    );
  }, []);

  async function submitAuthConfig(event) {
    event.preventDefault();
    setAuthError("");
    setCreatedCredential(null);
    setMessage("");
    setIsSubmitting(true);

    try {
      const response = await updateClient(clientId, buildAddAuthBody(authForm));
      const data = unwrapResponse(response);
      const credentials = data?.credential || data?.credentials || null;
      await loadClient();
      if (
        credentials &&
        (!Array.isArray(credentials) || credentials.length > 0)
      ) {
        setCreatedCredential(credentials);
        setMessage("");
      } else {
        setIsAuthModalOpen(false);
        setMessage("Đã thêm auth config thành công.");
      }
    } catch (submitError) {
      setAuthError(submitError.message || "Thêm auth config thất bại.");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function removeAuthConfig(config) {
    if (!config.id || pendingAuthId) return;
    const confirmed = window.confirm(
      "Bạn có chắc muốn xóa auth config khỏi cấu hình hoạt động?",
    );
    if (!confirmed) return;

    setPendingAuthId(config.id);
    setError("");
    setMessage("");
    try {
      await updateClient(clientId, {
        authConfigs: { removeAuthConfigIds: [config.id] },
      });
      await loadClient();
      setMessage("Đã xóa auth config khỏi cấu hình hoạt động.");
    } catch (submitError) {
      setError(submitError.message || "Xóa auth config thất bại.");
    } finally {
      setPendingAuthId("");
    }
  }

  async function toggleAuthConfig(config) {
    if (!config.id || pendingAuthId) return;
    setPendingAuthId(config.id);
    setError("");
    setMessage("");
    try {
      const item = {
        authConfigId: config.id,
        enabled: !config.enabled,
      };
      if (config.algorithm) item.algorithm = config.algorithm;
      if (config.expiresAt) item.expiresAt = config.expiresAt;
      await updateClient(clientId, { authConfigs: { update: [item] } });
      await loadClient();
      setMessage("Đã cập nhật trạng thái auth config.");
    } catch (submitError) {
      setError(submitError.message || "Cập nhật auth config thất bại.");
    } finally {
      setPendingAuthId("");
    }
  }

  return (
    <section className="client-detail-page">
      <div className="client-detail-container">
        <button
          type="button"
          className="client-detail-back"
          onClick={() => navigate("/clients")}
        >
          <span className="material-symbols-outlined">arrow_back</span>
          Quay lại danh sách
        </button>

        {isLoading && !client ? (
          <div className="client-detail-card client-detail-empty">
            Đang tải chi tiết client...
          </div>
        ) : null}

        {error ? <ErrorNotice message={error} onRetry={loadClient} /> : null}
        {message ? <p className="client-detail-message">{message}</p> : null}

        {client ? (
          <>
            <ClientDetailHeader
              client={client}
              isEditing={isEditing}
              isSubmitting={isSubmitting}
              onEdit={startEdit}
            />

            <ClientInfoCard
              client={client}
              draft={draft}
              error={formError}
              isEditing={isEditing}
              isSubmitting={isSubmitting}
              onCancel={cancelEdit}
              onChange={updateDraft}
              onSubmit={submitMetadata}
            />

            <AuthConfigSection
              authConfigs={client.authConfigs}
              pendingAuthId={pendingAuthId}
              onAdd={openAuthModal}
              onDelete={removeAuthConfig}
              onToggle={toggleAuthConfig}
            />
          </>
        ) : null}
      </div>

      {isAuthModalOpen ? (
        <AuthConfigModal
          form={authForm}
          error={authError}
          credential={createdCredential}
          isSubmitting={isSubmitting}
          serviceSuggestions={serviceSuggestions}
          onClose={closeAuthModal}
          onComplete={completeAuthModal}
          onSubmit={submitAuthConfig}
          onAuthConfigChange={updateAuthConfig}
          onServiceNameChange={updateServiceName}
          onServiceSelect={selectService}
          onAddAuthConfig={addAuthConfig}
          onRemoveAuthConfig={removeAuthConfigDraft}
        />
      ) : null}
    </section>
  );
}

function ErrorNotice({ message, onRetry }) {
  return (
    <div className="client-detail-error" role="alert">
      <span>{message}</span>
      {onRetry ? (
        <button type="button" onClick={onRetry}>
          Thử lại
        </button>
      ) : null}
    </div>
  );
}

function ClientDetailHeader({ client, isEditing, isSubmitting, onEdit }) {
  return (
    <header className="client-detail-header">
      <div>
        <h2>{client.name || "Client chưa đặt tên"}</h2>
        <div className="client-detail-chips">
          <span>
            <span className="material-symbols-outlined">id_card</span>ID:{" "}
            <strong>{client.id || "—"}</strong>
          </span>
          <span>
            <span className="material-symbols-outlined">code</span>CODE:{" "}
            <strong>{client.clientCode || "—"}</strong>
          </span>
        </div>
      </div>
      <button
        type="button"
        className="client-detail-secondary-button"
        onClick={onEdit}
        disabled={isEditing || isSubmitting}
      >
        <span className="material-symbols-outlined">edit</span>
        {isEditing ? "Đang chỉnh sửa..." : "Sửa thông tin"}
      </button>
    </header>
  );
}

function ClientInfoCard({
  client,
  draft,
  error,
  isEditing,
  isSubmitting,
  onCancel,
  onChange,
  onSubmit,
}) {
  return (
    <section className="client-detail-card client-detail-info-card">
      <div className="client-detail-card-header">
        <h3>Thông tin chi tiết Client</h3>
        <span className={statusClass(client.status)}>{client.status}</span>
      </div>
      <form className="client-detail-form" onSubmit={onSubmit}>
        <DetailField label="Tên Client">
          <input
            type="text"
            value={draft.name}
            disabled={!isEditing || isSubmitting}
            onChange={(event) => onChange("name", event.target.value)}
          />
        </DetailField>
        <DetailField label="Mã Client (Client Code)">
          <input type="text" value={client.clientCode || ""} disabled />
        </DetailField>
        <DetailField label="Email liên hệ">
          <input
            type="email"
            value={draft.contactEmail}
            disabled={!isEditing || isSubmitting}
            onChange={(event) => onChange("contactEmail", event.target.value)}
          />
        </DetailField>
        <DetailField label="Trạng thái hệ thống">
          <select
            value={draft.status}
            disabled={!isEditing || isSubmitting}
            onChange={(event) => onChange("status", event.target.value)}
          >
            {CLIENT_STATUSES.map((status) => (
              <option key={status} value={status}>
                {status}
              </option>
            ))}
          </select>
        </DetailField>
        <DetailField label="Mô tả" wide>
          <textarea
            rows="3"
            value={draft.description}
            disabled={!isEditing || isSubmitting}
            onChange={(event) => onChange("description", event.target.value)}
          />
        </DetailField>
        <div className="client-detail-meta">
          <span>Tạo: {formatDate(client.createdAt)}</span>
          <span>Cập nhật: {formatDate(client.updatedAt)}</span>
        </div>
        {error ? (
          <p className="client-detail-inline-error" role="alert">
            {error}
          </p>
        ) : null}
        {isEditing ? (
          <div className="client-detail-form-actions">
            <button
              type="button"
              className="client-detail-cancel-button"
              onClick={onCancel}
              disabled={isSubmitting}
            >
              Hủy bỏ
            </button>
            <button
              type="submit"
              className="client-detail-primary-button"
              disabled={isSubmitting}
            >
              {isSubmitting ? "Đang lưu..." : "Lưu thay đổi"}
            </button>
          </div>
        ) : null}
      </form>
    </section>
  );
}

function DetailField({ label, wide = false, children }) {
  return (
    <label
      className={
        wide
          ? "client-detail-field client-detail-field--wide"
          : "client-detail-field"
      }
    >
      <span>{label}</span>
      {children}
    </label>
  );
}

function AuthConfigSection({
  authConfigs,
  pendingAuthId,
  onAdd,
  onDelete,
  onToggle,
}) {
  return (
    <section className="client-detail-auth-section">
      <div className="client-detail-section-title">
        <div>
          <span aria-hidden="true" />
          <h3>Cấu hình xác thực (Auth Configs)</h3>
        </div>
        <button
          type="button"
          className="client-detail-primary-button"
          onClick={onAdd}
        >
          <span className="material-symbols-outlined">add_circle</span>
          Tạo Auth Config mới
        </button>
      </div>
      <div className="client-detail-card client-detail-table-wrap">
        <table className="client-detail-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Tên Service</th>
              <th>Service ID</th>
              <th>Loại</th>
              <th>Trạng thái</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {authConfigs.map((config) => (
              <tr key={config.id || `${config.serviceId}-${config.type}`}>
                <td>
                  <code>{config.id || "—"}</code>
                </td>
                <td>
                  <strong>{serviceLabel(config)}</strong>
                </td>
                <td>
                  <code>{config.serviceId || "—"}</code>
                </td>
                <td>
                  <span className="client-detail-type-badge">
                    {config.type}
                  </span>
                </td>
                <td>
                  <label className="client-detail-switch">
                    <input
                      type="checkbox"
                      checked={config.enabled}
                      disabled={pendingAuthId === config.id}
                      onChange={() => onToggle(config)}
                    />
                    <span aria-hidden="true" />
                    <strong>{config.enabled ? "Enabled" : "Disabled"}</strong>
                  </label>
                </td>
                <td>
                  <div className="client-detail-row-actions">
                    <button
                      type="button"
                      aria-label={`Xóa ${config.id}`}
                      title="Xóa"
                      disabled={pendingAuthId === config.id}
                      onClick={() => onDelete(config)}
                    >
                      <span className="material-symbols-outlined">delete</span>
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {authConfigs.length === 0 ? (
              <tr>
                <td colSpan="6" className="client-detail-empty-cell">
                  Client chưa có auth config.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
      <div className="client-detail-security-note">
        <span className="material-symbols-outlined">shield_lock</span>
        <p>
          Các khóa bảo mật và API Keys được mã hóa hoàn toàn và không bao giờ
          hiển thị ở chế độ xem chi tiết này vì lý do an toàn.
        </p>
      </div>
    </section>
  );
}

function AuthConfigModal({
  form,
  error,
  credential,
  isSubmitting,
  serviceSuggestions,
  onClose,
  onComplete,
  onSubmit,
  onAuthConfigChange,
  onServiceNameChange,
  onServiceSelect,
  onAddAuthConfig,
  onRemoveAuthConfig,
}) {
  const hasCredential = Boolean(
    credential && (!Array.isArray(credential) || credential.length > 0),
  );

  return (
    <div
      className="client-detail-modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="client-detail-auth-title"
    >
      <button
        type="button"
        className="client-detail-modal-backdrop"
        aria-label="Đóng modal"
        onClick={hasCredential ? undefined : onClose}
      />
      <form className="client-detail-modal-panel" onSubmit={onSubmit}>
        <header className="client-detail-modal-header">
          <h3 id="client-detail-auth-title">Tạo Auth Config mới</h3>
          <button
            type="button"
            aria-label="Đóng"
            onClick={onClose}
            disabled={isSubmitting}
          >
            <span className="material-symbols-outlined">close</span>
          </button>
        </header>
        <div className="client-detail-modal-body">
          {error ? (
            <p className="client-detail-inline-error" role="alert">
              {error}
            </p>
          ) : null}
          <section className="client-form-section">
            <div className="client-form-section__title-row">
              <h4>Cấu hình xác thực (Auth Configs)</h4>
              <button
                type="button"
                onClick={onAddAuthConfig}
                disabled={isSubmitting || hasCredential}
              >
                <span className="material-symbols-outlined">add_circle</span>
                Thêm cấu hình
              </button>
            </div>
            <div className="client-auth-list">
              {form.authConfigs.map((config, index) => (
                <div
                  className="client-auth-row"
                  key={`${index}-${config.type}`}
                >
                  <ServiceSearchField
                    value={config.serviceName}
                    suggestions={serviceSuggestions[index] || []}
                    disabled={isSubmitting || hasCredential}
                    onChange={(value) => onServiceNameChange(index, value)}
                    onSelect={(service) => onServiceSelect(index, service)}
                  />
                  <TextField
                    label="Service ID"
                    value={config.serviceId}
                    onChange={(value) =>
                      onAuthConfigChange(index, "serviceId", value)
                    }
                    placeholder="service-id"
                    readOnly
                    disabled={isSubmitting || hasCredential}
                  />
                  <label className="client-field">
                    <span>Type</span>
                    <select
                      value={config.type}
                      onChange={(event) =>
                        onAuthConfigChange(index, "type", event.target.value)
                      }
                      disabled={isSubmitting || hasCredential}
                    >
                      {AUTH_TYPES.map((type) => (
                        <option key={type}>{type}</option>
                      ))}
                    </select>
                  </label>
                  <label className="client-field">
                    <span>Algorithm</span>
                    <select
                      value={config.algorithm}
                      onChange={(event) =>
                        onAuthConfigChange(
                          index,
                          "algorithm",
                          event.target.value,
                        )
                      }
                      disabled={
                        isSubmitting ||
                        hasCredential ||
                        config.type === "API_KEY"
                      }
                    >
                      {AUTH_ALGORITHMS.map((algorithm) => (
                        <option key={algorithm}>{algorithm}</option>
                      ))}
                    </select>
                  </label>
                  <TextField
                    label="Expires at"
                    type="date"
                    value={dateInputValue(config.expiresAt)}
                    onChange={(value) =>
                      onAuthConfigChange(index, "expiresAt", value)
                    }
                    disabled={isSubmitting || hasCredential}
                  />
                  <button
                    type="button"
                    className="client-auth-remove"
                    onClick={() => onRemoveAuthConfig(index)}
                    disabled={
                      isSubmitting ||
                      hasCredential ||
                      form.authConfigs.length === 1
                    }
                    aria-label="Xóa auth config"
                  >
                    <span className="material-symbols-outlined">delete</span>
                  </button>
                </div>
              ))}
            </div>
            <p className="client-form-hint">
              * Auth config áp dụng cho toàn service; inbound access rules vẫn
              được quản lý riêng. Credential sẽ được backend khởi tạo tự động và
              chỉ hiển thị một lần nếu API trả về.
            </p>
          </section>
          {hasCredential ? (
            <ClientCredentialBox credential={credential} />
          ) : null}
        </div>
        <footer className="client-detail-modal-footer">
          <button
            type="button"
            className="client-detail-cancel-button"
            onClick={onClose}
            disabled={isSubmitting}
          >
            {hasCredential ? "Đóng" : "Hủy"}
          </button>
          {hasCredential ? (
            <button
              type="button"
              className="client-detail-primary-button"
              onClick={onComplete}
            >
              Hoàn tất
            </button>
          ) : (
            <button
              type="submit"
              className="client-detail-primary-button"
              disabled={isSubmitting}
            >
              {isSubmitting ? "Đang tạo..." : "Tạo auth config"}
            </button>
          )}
        </footer>
      </form>
    </div>
  );
}

function ServiceSearchField({
  value,
  suggestions,
  disabled = false,
  onChange,
  onSelect,
}) {
  return (
    <div className="client-field client-inbound-search">
      <span>Tên service</span>
      <input
        type="text"
        value={value || ""}
        onChange={(event) => onChange(event.target.value)}
        placeholder="Nhập tên service"
        autoComplete="off"
        disabled={disabled}
      />
      {suggestions.length > 0 ? (
        <div className="client-inbound-suggestions">
          {suggestions.map((service) => (
            <button
              type="button"
              key={service.id}
              onClick={() => onSelect(service)}
            >
              <strong>{service.name || service.id}</strong>
              <span>
                {[service.baseUrl, service.status, service.id]
                  .filter(Boolean)
                  .join(" · ")}
              </span>
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function TextField({
  label,
  value,
  onChange,
  type = "text",
  placeholder = "",
  readOnly = false,
  disabled = false,
}) {
  return (
    <label className="client-field">
      <span>{label}</span>
      <input
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        readOnly={readOnly}
        disabled={disabled}
      />
    </label>
  );
}
