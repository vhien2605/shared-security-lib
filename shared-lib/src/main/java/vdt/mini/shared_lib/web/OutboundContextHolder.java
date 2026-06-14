package vdt.mini.shared_lib.web;

import org.springframework.stereotype.Component;

@Component
public class OutboundContextHolder {
    private static final ThreadLocal<OutboundContext> CURRENT = new ThreadLocal<>();

    public void set(OutboundContext context) {
        CURRENT.set(context);
    }

    public OutboundContext get() {
        return CURRENT.get();
    }

    public void clear() {
        CURRENT.remove();
    }
}
