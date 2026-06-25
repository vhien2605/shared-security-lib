package vdt.mini.management_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import vdt.mini.management_service.service.anomaly.alert.NotificationWebSocketHandler;

@Configuration
@EnableWebSocket
public class NotificationWebSocketConfig implements WebSocketConfigurer {
    private final NotificationWebSocketHandler handler;
    private final NotificationHandshakeInterceptor interceptor;

    public NotificationWebSocketConfig(NotificationWebSocketHandler handler, NotificationHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/notifications")
                .addInterceptors(interceptor)
                .setAllowedOrigins("http://localhost:5173");
    }
}
