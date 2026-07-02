# VDT Mini Project - Thư viện bảo mật dùng chung và hệ thống quản trị tập trung

## 1. Giới thiệu

Dự án này mô phỏng một hệ thống bảo mật dùng chung cho kiến trúc microservices. Mục tiêu chính là giúp các service nghiệp vụ tích hợp bảo mật, logging, quản lý endpoint, quản lý client và giám sát bất thường thông qua một **shared library** và một cụm **central management**.

Thay vì mỗi service tự xử lý cấu hình bảo mật, audit log, rate limit, quyền truy cập hoặc đăng ký endpoint, dự án cung cấp:

- `shared-lib`: thư viện Spring Boot dùng chung, import vào các service nghiệp vụ.
- `central/management-service`: backend quản trị tập trung, nhận registration, quản lý settings, client, quyền truy cập, runtime config và anomaly.
- `central/frontend`: giao diện quản trị tập trung.
- Hạ tầng local gồm Kafka, Redis, PostgreSQL, Keycloak, Elasticsearch, Logstash và Kibana.
- Service mẫu: `user-service`, `profile-service`.

## 2. Cấu trúc thư mục

```text
mini-project/
├── shared-lib/                  # Thư viện bảo mật dùng chung cho Spring Boot services
├── user-service/                # Service mẫu có tích hợp shared-lib
├── profile-service/             # Service mẫu nghiệp vụ
├── central/
│   ├── management-service/      # Backend quản trị tập trung
│   ├── frontend/                # React admin UI
│   ├── elasticsearch/           # Template và ILM policy cho security logs/anomalies
│   ├── logstash/                # Pipeline Kafka -> Elasticsearch
│   └── keycloak/                # Realm import cho xác thực admin UI
└── docker-compose.yml           # Hạ tầng local
```

## 3. Cách dùng `shared-lib` trong service nghiệp vụ

### 3.1. Import dependency

Trong service Spring Boot muốn dùng thư viện, thêm dependency Maven:

```xml
<dependency>
    <groupId>vdt.mini</groupId>
    <artifactId>shared-lib</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Sau khi dependency có mặt trong classpath, `SecurityAutoConfiguration` của thư viện sẽ tự kích hoạt nếu `app.security.enabled=true` hoặc không cấu hình giá trị này.

### 3.2. Import annotation và enum

Các annotation chính:

```java
import vdt.mini.shared_lib.annotation.InBoundSecurity;
import vdt.mini.shared_lib.annotation.OutBoundSecurity;
import vdt.mini.shared_lib.enums.EndpointMethod;
import vdt.mini.shared_lib.enums.EndpointProtocol;
```

Ý nghĩa:

- `@InBoundSecurity`: đánh dấu endpoint nhận request/message từ bên ngoài hoặc từ hệ thống khác.
- `@OutBoundSecurity`: đánh dấu logic gọi ra bên ngoài như HTTP client, Feign client hoặc Kafka publisher.

### 3.3. Đánh annotation cho inbound HTTP endpoint

```java
@RestController
public class OrderWebhookController {

    @PostMapping("/api/orders/webhook")
    @InBoundSecurity(
            name = "receive-order-webhook",
            path = "/api/orders/webhook",
            protocol = EndpointProtocol.HTTP,
            method = EndpointMethod.POST,
            description = "Webhook nhận đơn hàng từ đối tác"
    )
    public ResponseEntity<Void> receiveOrder(@RequestBody String payload) {
        return ResponseEntity.ok().build();
    }
}
```

Khi service khởi động, shared-lib scan method này, sinh metadata endpoint, đăng ký vào registry local và gửi registration về central qua Kafka nếu registration đang bật.

### 3.4. Đánh annotation cho inbound MQ listener

```java
@Component
public class OrderEventListener {

    @KafkaListener(topics = "order.events")
    @InBoundSecurity(
            name = "order-event-listener",
            topic = "order.events",
            protocol = EndpointProtocol.MQ,
            method = EndpointMethod.SUB,
            description = "Lắng nghe sự kiện đơn hàng"
    )
    public void onMessage(String message) {
        // xử lý message
    }
}
```

Shared-lib có `SecurityRecordInterceptor` để hỗ trợ kiểm soát inbound Kafka listener khi `app.security.mq.inbound.enabled=true`.

### 3.5. Đánh annotation cho outbound HTTP/Feign/Kafka

Ví dụ outbound HTTP:

```java
@Service
public class PaymentClient {

    @OutBoundSecurity(
            name = "call-payment-provider",
            targetUrl = "https://partner.example.com/api/payments",
            protocol = EndpointProtocol.HTTP,
            method = EndpointMethod.POST,
            description = "Gọi cổng thanh toán đối tác"
    )
    public String pay(String request) {
        // gọi RestTemplate/WebClient/Feign tùy service
        return "OK";
    }
}
```

Ví dụ outbound Kafka publisher:

```java
@Service
public class NotificationPublisher {

    @OutBoundSecurity(
            name = "publish-notification",
            topic = "notification.events",
            protocol = EndpointProtocol.MQ,
            method = EndpointMethod.PUB,
            description = "Publish thông báo sang Kafka"
    )
    public void publish(String message) {
        // kafkaTemplate.send("notification.events", message)
    }
}
```

### 3.6. Tham số annotation

`@InBoundSecurity`:

| Tham số | Mô tả |
|---|---|
| `name` | Tên endpoint, nên unique trong service |
| `path` | Đường dẫn HTTP inbound |
| `topic` | Topic MQ inbound |
| `protocol` | `HTTP`, `MQ` hoặc `WEBHOOK` |
| `method` | HTTP method hoặc `SUB`; mặc định `POST` |
| `description` | Mô tả nghiệp vụ |

`@OutBoundSecurity`:

| Tham số | Mô tả |
|---|---|
| `name` | Tên outbound endpoint |
| `targetUrl` | URL đích nếu gọi HTTP |
| `topic` | Topic nếu publish MQ |
| `protocol` | `HTTP`, `MQ` hoặc `WEBHOOK` |
| `method` | HTTP method hoặc `PUB`; mặc định `POST` |
| `description` | Mô tả nghiệp vụ |

## 4. Cấu hình properties khi dùng lib

### 4.1. Cấu hình tối thiểu

```properties
spring.application.name=user-service

app.security.enabled=true
app.security.namespace=mini-project
app.security.service.name=user-service
app.security.service.base-url=http://localhost:8081
app.security.service.description=Quan ly tai khoan user
```

Trong đó:

- `app.security.namespace`: namespace logic của hệ thống, dùng để sinh ID ổn định.
- `app.security.service.name`: tên logic của service.
- `app.security.service.base-url`: base URL central dùng để hiển thị/quản trị service.
- `app.security.service.description`: mô tả service trên giao diện central.

### 4.2. Kafka registration

```properties
app.security.registration.enabled=true
app.security.registration.topic=security.endpoint.registration
app.security.kafka.bootstrap-servers=localhost:9094
```

Khi `registration.enabled=true`, service sẽ publish thông tin service và endpoint lên Kafka topic `security.endpoint.registration`. `management-service` consume topic này để tạo/cập nhật service, inbound endpoint và outbound endpoint.

Trong môi trường nhiều instance, chỉ nên bật registration ở một instance đại diện:

```properties
# Instance registrar/master
app.security.registration.enabled=true

# Instance follower
app.security.registration.enabled=false
```

Các instance follower vẫn có thể bật Redis sync để nhận runtime config.

### 4.3. Redis runtime settings sync

```properties
app.security.redis.host=localhost
app.security.redis.port=6379
app.security.redis.password=redis123
app.security.settings.sync.enabled=true
```

Shared-lib dùng Redis để:

- Poll/sub cấu hình runtime theo service.
- Nhận thay đổi settings, client, auth config, blacklist/whitelist, permission từ central.
- Áp dụng cấu hình mới mà không cần restart service.

### 4.4. Audit log và security log

```properties
app.security.audit.kafka.enabled=true
app.security.kafka.bootstrap-servers=localhost:9094
```

Shared-lib ghi audit log vào logger `SECURITY_AUDIT` và publish event lên Kafka topic `security.logs`. Logstash đọc topic này và ghi vào Elasticsearch index `security-logs-*`.

### 4.5. Kafka nghiệp vụ riêng của service

Nếu service có Kafka nghiệp vụ riêng, vẫn cấu hình `spring.kafka.*` như bình thường:

```properties
spring.kafka.bootstrap-servers=localhost:9194
spring.kafka.consumer.group-id=user-group
```

Shared-lib dùng producer riêng tên `securityKafkaTemplate`, nên `app.security.kafka.bootstrap-servers` không bắt buộc trùng với Kafka nghiệp vụ của service.

## 5. Kiến trúc hoạt động mức cao

### 5.0. Sơ đồ tổng quan shared-lib và central

```text
┌──────────────────────────────────────┐
│ Service nghiệp vụ dùng shared-lib    │
│                                      │
│  Spring Boot service                 │
│    │                                 │
│    ├─ @InBoundSecurity               │
│    ├─ @OutBoundSecurity              │
│    │                                 │
│    ▼                                 │
│  shared-lib runtime                  │
│    ├─ Endpoint scanner/registry      │
│    ├─ Runtime policy enforcement     │
│    └─ Security audit logger          │
└───────────────┬───────────────▲──────┘
                │               │
                │               │ Redis runtime config
                │               │ settings/client/permission
                │               │
                ▼               │
┌──────────────────────────────────────┐
│ Hạ tầng trao đổi                     │
│                                      │
│  Kafka: security.endpoint.registration
│  Kafka: security.logs                │
│  Kafka: security.anomalies           │
│  Redis: runtime config channels      │
└───────────────┬───────────────▲──────┘
                │               │
                │               │ publish runtime config
                ▼               │
┌──────────────────────────────────────┐
│ Central Management                   │
│                                      │
│  central frontend                    │
│    │                                 │
│    ▼                                 │
│  management-service                  │
│    ├─ PostgreSQL                     │
│    ├─ Elasticsearch: security-logs-* │
│    ├─ Elasticsearch: security-anomalies-*
│    └─ Kibana dashboard               │
└──────────────────────────────────────┘

Luồng service -> central:
  1. shared-lib scan annotation trong service.
  2. shared-lib publish metadata service/endpoint vào Kafka registration.
  3. management-service consume registration và lưu PostgreSQL.
  4. shared-lib publish SecurityLogEvent vào Kafka security.logs.
  5. Logstash ghi security.logs vào Elasticsearch security-logs-*.
  6. management-service đọc logs để phát hiện bất thường, publish security.anomalies.
  7. Logstash ghi anomalies vào Elasticsearch security-anomalies-*.

Luồng central -> service:
  1. Admin chỉnh setting/client/quyền truy cập trên central frontend.
  2. management-service lưu cấu hình quản trị vào PostgreSQL.
  3. management-service publish runtime config sang Redis channel theo serviceId.
  4. shared-lib subscribe Redis, nhận config mới và áp dụng vào service.
```

Luồng chính có hai chiều:

- Từ service về central: `shared-lib` scan annotation, publish registration, ghi security log và gửi dữ liệu cho anomaly detection.
- Từ central về service: admin thay đổi cấu hình trên UI, `management-service` ghi PostgreSQL và đẩy runtime config qua Redis để `shared-lib` áp dụng ngay trong service.

### 5.1. Startup và registration

```text
Service nghiệp vụ khởi động
    -> shared-lib auto configuration được nạp
    -> scan method có @InBoundSecurity / @OutBoundSecurity
    -> sinh serviceId, endpointId ổn định từ namespace + serviceName + endpoint metadata
    -> lưu endpoint vào registry local
    -> publish ServiceRegistrationEvent lên Kafka security.endpoint.registration
    -> management-service consume và lưu metadata vào PostgreSQL
```

`serviceId` và `endpointId` được sinh theo hướng deterministic, nên restart service không làm đổi ID nếu `namespace`, `service.name` và metadata endpoint không đổi.

### 5.2. Runtime configuration

```text
Admin thay đổi setting/client/permission trên central UI
    -> management-service lưu vào PostgreSQL
    -> management-service sync projection/runtime config sang Redis
    -> shared-lib trong service subscribe Redis channel theo serviceId
    -> service áp dụng cấu hình mới cho inbound/outbound runtime
```

Cấu hình runtime gồm ngưỡng timeout, rate limit, retention, rollback strategy, alert setting, client auth config, access rule, access permission.

### 5.3. Security logs

```text
Request/message đi qua service đã tích hợp shared-lib
    -> shared-lib đánh giá bảo mật và runtime policy
    -> tạo SecurityLogEvent
    -> publish Kafka topic security.logs
    -> Logstash ghi vào Elasticsearch security-logs-*
    -> Central UI tra cứu tại trang Nhật ký hệ thống
```

### 5.4. Anomaly detection

```text
management-service consume security.logs
    -> tính deviation, rule match, risk score
    -> tạo AnomalyEvent
    -> publish Kafka topic security.anomalies
    -> Logstash ghi vào Elasticsearch security-anomalies-*
    -> Central UI hiển thị tại trang Quản lý bất thường
```

Risk score được tạo từ nhiều nguồn: rule points, historical/behavior deviation, static context và source alert severity. Incident dedup giúp gom các anomaly trùng nhóm trong một khoảng thời gian.

## 6. Central Management

### 6.1. Backend `management-service`

`management-service` là backend quản trị trung tâm, chịu trách nhiệm:

- Nhận registration event từ Kafka.
- Lưu service, endpoint, setting template, client, auth config, permission vào PostgreSQL.
- Đồng bộ runtime config sang Redis cho các service đã tích hợp lib.
- Cung cấp API cho central frontend.
- Consume `security.logs` để xử lý anomaly detection.
- Truy vấn Elasticsearch cho security logs và anomaly.
- Gửi alert qua WebSocket/email/in-app notification tùy cấu hình.

### 6.2. Frontend `central/frontend`

Frontend là React admin UI, dùng Keycloak để xác thực. Các route chính được bọc bởi `ProtectedRoute` và layout chung `HeaderSidebarLayout`.

Các trang chính:

| Trang | Route | Vai trò | Cách dùng ở mức tổng quan |
|---|---|---|---|
| Đăng nhập | `/` | Trang vào hệ thống, chuyển hướng xác thực qua Keycloak | Người dùng đăng nhập bằng tài khoản có quyền quản trị |
| Tổng quan | `/overview` | Nhúng Kibana dashboard để xem nhanh tình trạng security logs và chỉ số vận hành | Dùng để quan sát toàn cảnh, filter thời gian trực tiếp trên dashboard Kibana |
| Quản lý setting | `/settings-management` | Danh sách service đã đăng ký và cấu hình global template | Xem service, bật/tắt service, điều chỉnh cấu hình global như timeout, rate limit, retention, alert |
| Chi tiết setting service | `/settings-management/services/:serviceId` | Quản lý endpoint và ngưỡng cấu hình ở cấp service | Vào từ danh sách setting để xem inbound/outbound endpoint, chỉnh threshold, channel alert, apply config |
| Quản lý client | `/clients` | Quản lý client sử dụng hệ thống | Tìm kiếm client, tạo client mới, đổi trạng thái, xem chi tiết client |
| Chi tiết client | `/clients/:clientId` | Quản lý metadata và auth config của một client | Sửa thông tin client, thêm/xóa/bật/tắt API key hoặc HMAC auth config |
| Quản lý quyền hạn | `/permissions` | Quản lý blacklist, whitelist và quyền truy cập thường | Lọc theo endpoint, thêm rule chặn/cho phép, bật/tắt hoặc xóa quyền truy cập |
| Nhật ký hệ thống | `/security-logs` | Tra cứu security logs trong Elasticsearch | Lọc theo thời gian, service, endpoint, trace, flow; click row để xem chi tiết log |
| Quản lý bất thường | `/anomalies` | Theo dõi anomaly và incident từ `security-anomalies-*` | Xem KPI, filter anomaly, xem risk score, timeline, top services/endpoints và detail snapshot |
| Báo cáo thống kê | `/audit-logs` | Mục sidebar dành cho báo cáo/thống kê | Route hiện có trong menu; cần đảm bảo page tương ứng tồn tại khi build production |

Lưu ý: trong route hiện tại có một số import page placeholder như `ServicesPage`, `EndpointsPage`, `SecurityPoliciesPage`, `AuditLogsPage`. Nếu các file này chưa tồn tại, production build frontend sẽ fail cho tới khi bổ sung hoặc bỏ route tương ứng.

## 7. Cách chạy local

### 7.1. Khởi động hạ tầng

```powershell
docker compose up -d postgres redis kafka kafka2 keycloak elasticsearch elasticsearch-template-loader logstash kibana kibana-setup
```

Các cổng mặc định:

| Thành phần | URL/Cổng |
|---|---|
| PostgreSQL | `localhost:5434` |
| Redis | `localhost:6379` |
| Kafka security | `localhost:9094` |
| Kafka nghiệp vụ mẫu | `localhost:9194` |
| Keycloak | `http://localhost:8000` |
| Elasticsearch | `http://localhost:9200` |
| Kibana | `http://localhost:5601` |

### 7.2. Chạy backend

Ví dụ với PowerShell:

```powershell
cd central/management-service
./mvnw.cmd spring-boot:run
```

Chạy service mẫu:

```powershell
cd user-service
./mvnw.cmd spring-boot:run
```

```powershell
cd profile-service
./mvnw.cmd spring-boot:run
```

### 7.3. Chạy frontend

```powershell
cd central/frontend
npm install
npm run dev
```

Sau đó mở URL dev server do Vite hiển thị.

## 8. Kiểm tra dữ liệu log và anomaly

Kiểm tra Kafka security logs:

```powershell
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic security.logs --from-beginning --max-messages 5
```

Tìm security logs trong Elasticsearch:

```powershell
curl "http://localhost:9200/security-logs-*/_search?q=traceId:<trace-id>"
```

Tìm anomaly:

```powershell
curl "http://localhost:9200/security-anomalies-*/_search?pretty"
```

Trên Kibana, vào `Analytics > Discover`, chọn data view `Security Logs` (`security-logs-*`) để phân tích log.

