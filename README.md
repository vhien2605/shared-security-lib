VDT mini-project


### Mô tả bài toán

Trong các dự án Microservices hiện nay, việc các dịch vụ chia sẻ và đồng bộ dữ liệu với nhau (hoặc với bên thứ ba) thông qua Webhook và Message Queue rất phổ biến. Khi hệ thống phình to, doanh nghiệp sẽ khó quản lý các client của mình và đối mặt với rủi ro bảo mật dữ liệu.

Giải pháp: Xây dựng một Thư viện dùng chung (Shared Library) kết hợp Hệ thống giám sát tập trung đóng vai trò cầu nối giữa hệ thống nội bộ và hệ thống client.



## 1. Giới thiệu

Shared-lib cung cấp 2 annotation `@InBoundSecurity` và `@OutBoundSecurity` để tự động scan endpoint của secureService,
đồng bộ cấu hình runtime từ Redis và, với instance registrar, đăng ký endpoint lên management-secureService qua Kafka khi
secureService khởi động.

**Luồng dữ liệu:**

```
Service startup
    │
    ├── ApplicationReadyEvent
    ├── SecurityEndpointScanner quét bean
    │     ├── Tìm method có @InBoundSecurity
    │     └── Tìm method có @OutBoundSecurity
    ├── SecurityIdGenerator
    │     ├── serviceId = SHA-256(namespace + ":" + serviceName), lấy 32 ký tự đầu
    │     └── endpointId = SHA-256(serviceId + endpoint metadata), lấy 32 ký tự đầu
    ├── EndpointRegistry cập nhật endpoint local
    ├── Nếu app.security.registration.enabled=true
    │     ├── Build ServiceRegistrationEvent từ config + annotation đã scan
    │     │     ├── serviceId, serviceName, baseUrl
    │     │     ├── List<InboundEndpointDTO>
    │     │     └── List<OutboundEndpointDTO>
    │     └── KafkaPublisher → topic security.endpoint.registration
    ├── Nếu app.security.registration.enabled=false
    │     └── Không publish Kafka registration
    └── Redis sync
          ├── Poll config từ Redis nếu app.security.settings.sync.enabled=true
          └── Subscribe Redis channel theo deterministic serviceId
```

## 2. Thêm dependency

```xml

<dependency>
    <groupId>vdt.mini</groupId>
    <artifactId>shared-lib</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## 3. Cấu hình application.properties

### 3.1. Bắt buộc

```properties
spring.application.name=user-secureService
app.security.namespace=mini-project
app.security.service.name=user-secureService
app.security.service.base-url=http://user-secureService:8081
app.security.service.description=Quản lý account người dùng
```

`app.security.namespace` và `app.security.service.name` là input để sinh deterministic `serviceId`:

```text
serviceId = first32Hex(SHA-256(trim(namespace) + ":" + trim(serviceName)))
```

Các instance của cùng một logical service phải cấu hình cùng `namespace` và `service.name` để cùng poll/sub Redis config.

### 3.2. Tuỳ chọn (có giá trị mặc định)

```properties
# Kafka topic (mặc định: security.endpoint.registration)
app.security.registration.topic=security.endpoint.registration
# Chỉ registrar/master bật true để publish Kafka registration (mặc định: true)
app.security.registration.enabled=true
# Bật/tắt (mặc định: true)
app.security.enabled=true
app.security.redis.host=localhost
app.security.redis.port=6379
app.security.redis.password=redis123
app.security.settings.sync.enabled=true
```

### 3.3. Registrar và follower trong môi trường nhiều instance

Mọi instance đều cần bật security và Redis sync để scan endpoint, derive ID, poll Redis và subscribe Redis channel:

```properties
app.security.enabled=true
app.security.settings.sync.enabled=true
app.security.namespace=mini-project
app.security.service.name=user-secureService
```

Chỉ một instance registrar/master của mỗi logical service bật registration:

```properties
# Registrar/master
app.security.registration.enabled=true
```

Các instance follower/slave tắt registration:

```properties
# Follower/slave
app.security.registration.enabled=false
```

Khác biệt duy nhất:

| Mode | Publish Kafka registration | Poll/sub Redis |
|------|----------------------------|----------------|
| Registrar/master | Có | Có |
| Follower/slave | Không | Có |

### 3.4. Kafka connection

Không cần config Kafka. Shared-lib tự tạo `KafkaTemplate` riêng với:

| Config            | Giá trị mặc định   |
|-------------------|--------------------|
| bootstrap-servers | `localhost:9094`   |
| key serializer    | `StringSerializer` |
| value serializer  | `StringSerializer` |

Nếu muốn override Kafka cluster:

```properties
app.security.kafka.bootstrap-servers=my-cluster:9092
```

Nếu secureService có Kafka riêng (cho mục đích khác), config `spring.kafka.bootstrap-servers` như bình thường — không
ảnh hưởng gì, shared-lib dùng producer riêng tên `securityKafkaTemplate`.

## 4. Annotation InBoundSecurity

Dùng cho các endpoint **nhận dữ liệu từ bên ngoài** (webhook, message listener).

### 4.1. HTTP Webhook

```java

@RestController
public class OrderController {

    @PostMapping("/api/orders/webhook")
    @InBoundSecurity(
            name = "receive-order-webhook",
            path = "/api/orders/webhook",
            protocol = EndpointProtocol.HTTP,
            method = EndpointMethod.POST,
            description = "Webhook nhận đơn hàng từ đối tác"
    )
    public ResponseEntity<?> handleWebhook(@RequestBody WebhookPayload payload) {
        // logic xử lý
    }
}
```

### 4.2. MQ Listener

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
    public void onOrderEvent(String message) {
        // logic xử lý
    }
}
```

### 4.3. Tham số

| Tham số       | Bắt buộc              | Mô tả                                    |
|---------------|-----------------------|------------------------------------------|
| `name`        | Có                    | Tên endpoint, unique trong secureService |
| `protocol`    | Có                    | `HTTP`, `MQ`, hoặc `WEBHOOK`             |
| `path`        | Không (nếu dùng HTTP) | URL path cho HTTP webhook                |
| `topic`       | Không (nếu dùng MQ)   | Topic name cho message listener          |
| `method`      | Không (mặc định POST) | HTTP method hoặc `SUB` cho MQ listener   |
| `description` | Không                 | Mô tả chức năng                          |

## 5. Annotation OutBoundSecurity

Dùng cho các endpoint **gửi dữ liệu ra ngoài** (HTTP client, Feign, Kafka publisher).

### 5.1. HTTP Client (RestTemplate)

```java

@Service
public class PaymentClient {

    @OutBoundSecurity(
            name = "call-partner-payment",
            targetUrl = "https://partner.com/api/pay",
            protocol = EndpointProtocol.HTTP,
            method = EndpointMethod.POST,
            description = "Gọi API thanh toán đối tác"
    )
    public PaymentResponse processPayment(PaymentRequest req) {
        return restTemplate.postForObject(
                "https://partner.com/api/pay", req, PaymentResponse.class);
    }
}
```

### 5.2. Feign Client

```java

@FeignClient(name = "payment-client", url = "https://partner.com")
public interface PaymentClient {

    @PostMapping("/api/pay")
    @OutBoundSecurity(
            name = "payment-outbound",
            targetUrl = "https://partner.com/api/pay",
            protocol = EndpointProtocol.HTTP,
            method = EndpointMethod.POST,
            description = "Gọi API thanh toán đối tác"
    )
    String pay(@RequestBody String body);
}
```

Lưu ý: phải có `@EnableFeignClients` trên `@SpringBootApplication`.

### 5.3. Kafka Publisher

```java

@Service
public class NotificationService {

    @OutBoundSecurity(
            name = "publish-notification",
            topic = "notification.events",
            protocol = EndpointProtocol.MQ,
            method = EndpointMethod.PUB,
            description = "Gửi thông báo qua Kafka"
    )
    public void sendNotification(Notification notif) {
        kafkaTemplate.send("notification.events", notif);
    }
}
```

### 5.4. Tham số

| Tham số       | Bắt buộc              | Mô tả                                    |
|---------------|-----------------------|------------------------------------------|
| `name`        | Có                    | Tên endpoint, unique trong secureService |
| `protocol`    | Có                    | `HTTP` hoặc `MQ`                         |
| `targetUrl`   | Không (nếu dùng HTTP) | URL đích cho HTTP client                 |
| `topic`       | Không (nếu dùng MQ)   | Topic name cho publisher                 |
| `method`      | Không (mặc định POST) | HTTP method hoặc `PUB` cho MQ publisher  |
| `description` | Không                 | Mô tả chức năng                          |

## 6. Deterministic identity

Shared-lib không đọc hoặc ghi identity file trong luồng startup/registration. Khi start/restart, mọi instance derive
`serviceId`, `endpointId` và metadata endpoint trực tiếp từ config và annotation đang có, rồi cập nhật registry local.
Registrar/master publish Kafka registration event; follower/slave không publish event.

Mọi instance derive ID từ cùng rule:

```text
serviceId  = first32Hex(SHA-256(namespace + ":" + serviceName))
endpointId = first32Hex(SHA-256(serviceId + "|" + direction + "|" + protocol + "|" + method + "|" + destination + "|"))
```

Trong phase hiện tại, `consumerGroup` trong endpoint identity để rỗng vì annotation/DTO chưa có field này.

## 7. Security audit log ELK sync

Shared-lib luôn ghi audit JSON vào logger `SECURITY_AUDIT` và best-effort publish cùng event vào Kafka topic
`security.logs`. Service import không cần tự tạo producer hoặc tự publish audit log.

```properties
app.security.kafka.bootstrap-servers=localhost:9094
app.security.audit.kafka.enabled=true
```

Topic audit log là giá trị cố định trong shared-lib: `security.logs`; service sử dụng lib không cần và không nên cấu hình topic này.

`retentionDays` từ inbound/outbound settings được map thành `retentionBucket`: `<=14 -> r14`, `<=30 -> r30`, còn lại
`r90`; giá trị thiếu dùng `30/r30`. Kafka publish là async, lỗi serialize/send/ack chỉ ghi warning nội bộ trong
shared-lib và không làm fail inbound/outbound business flow.

Local ELK stack:

```powershell
docker compose up -d kafka elasticsearch elasticsearch-template-loader logstash kibana kibana-setup
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic security.logs --from-beginning --max-messages 5
curl "http://localhost:9200/security-logs-*/_search?q=traceId:<trace-id>"
```

Kibana: mở `http://localhost:5601`, vào `Analytics > Discover`, chọn data view `Security Logs`
(`security-logs-*`), rồi filter theo `traceId`, `serviceName`, `endpointName`, `resultStatus`, `retentionBucket`.

Elasticsearch templates and ILM policies live under `central/elasticsearch/`; Logstash pipeline lives under
`central/logstash/pipeline/security-log.conf`. Rollback runtime by setting `app.security.audit.kafka.enabled=false` or stopping
Logstash/Elasticsearch; `SECURITY_AUDIT` logger output remains unchanged.
