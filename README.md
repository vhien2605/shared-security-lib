VDT mini-project


### Mô tả bài toán

Trong các dự án Microservices hiện nay, việc các dịch vụ chia sẻ và đồng bộ dữ liệu với nhau (hoặc với bên thứ ba) thông qua Webhook và Message Queue rất phổ biến. Khi hệ thống phình to, doanh nghiệp sẽ khó quản lý các client của mình và đối mặt với rủi ro bảo mật dữ liệu.

Giải pháp: Xây dựng một Thư viện dùng chung (Shared Library) kết hợp Hệ thống giám sát tập trung đóng vai trò cầu nối giữa hệ thống nội bộ và hệ thống client.



## 1. Giới thiệu

Shared-lib cung cấp 2 annotation `@InBoundSecurity` và `@OutBoundSecurity` để tự động đăng ký endpoint của secureService
lên management-secureService qua Kafka khi secureService khởi động.

**Luồng dữ liệu:**

```
Service startup
    │
    ├── ApplicationReadyEvent
    ├── SecurityEndpointScanner quét bean
    │     ├── Tìm method có @InBoundSecurity
    │     └── Tìm method có @OutBoundSecurity
    ├── IdentityManager
    │     ├── Load/UUID từ identity file
    │     └── Gen UUID mới cho endpoint chưa có
    ├── Build ServiceRegistrationEvent
    │     ├── serviceId, serviceName, baseUrl
    │     ├── List<InboundEndpointDTO>
    │     └── List<OutboundEndpointDTO>
    └── KafkaPublisher → topic security.endpoint.registration
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
app.security.service.name=user-secureService
app.security.service.base-url=http://user-secureService:8081
app.security.service.description=Quản lý account người dùng
```

### 3.2. Tuỳ chọn (có giá trị mặc định)

```properties
# File lưu UUID (mặc định: config/security-identity.json)
app.security.identity-file=config/security-identity.json
# Kafka topic (mặc định: security.endpoint.registration)
app.security.registration.topic=security.endpoint.registration
# Bật/tắt (mặc định: true)
app.security.enabled=true
```

### 3.3. Kafka connection

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
            method = EndpointMethod.POST,
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
| `method`      | Không (mặc định POST) | HTTP method                              |
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
            method = EndpointMethod.POST,
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
| `method`      | Không (mặc định POST) | HTTP method                              |
| `description` | Không                 | Mô tả chức năng                          |

## 6. Identity file

### 6.1. Vị trí

Mặc định: tương đối với working directory của secureService.

Ví dụ:

- `user-secureService/` chạy ở `/app/user-secureService/` → file ở
  `/app/user-secureService/security/user-secureService-identity.json`
- Có thể override bằng `app.security.identity-file`

### 6.2. Cấu trúc

```json
{
  "serviceId": "a1b2c3d4-e5f6-...",
  "endpoints": {
    "HTTP_POST_/api/orders/webhook": "b2c3d4e5-...",
    "MQ_order-events": "c3d4e5f6-...",
    "HTTP_POST_https://partner.com/api/pay": "d4e5f6a7-..."
  }
}
```
