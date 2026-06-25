package vdt.mini.management_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class NotificationHandshakeInterceptor implements HandshakeInterceptor {
    private static final Logger log = LoggerFactory.getLogger(NotificationHandshakeInterceptor.class);
    private final JwtDecoder jwtDecoder;

    public NotificationHandshakeInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = token(request.getURI());
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            attributes.put("jwtSubject", jwtDecoder.decode(token).getSubject());
            return true;
        } catch (JwtException exception) {
            log.warn("Rejected notification WebSocket handshake with invalid token");
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    }

    private String token(URI uri) {
        String query = uri.getRawQuery();
        if (query == null) return null;
        for (String part : query.split("&")) {
            int index = part.indexOf('=');
            if (index > 0 && "token".equals(part.substring(0, index))) {
                return URLDecoder.decode(part.substring(index + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
