package vdt.mini.shared_lib.security;

public final class SecurityRequestContextHolder {
    private static final ThreadLocal<SecurityRequestContext> CURRENT = new ThreadLocal<>();

    private SecurityRequestContextHolder() {
    }

    public static void set(SecurityRequestContext context) {
        CURRENT.set(context);
    }

    public static SecurityRequestContext get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
