package vdt.mini.shared_lib.security;

public final class SecurityLogRetentionBucketMapper {
    public static final int DEFAULT_RETENTION_DAYS = 30;
    public static final String R14 = "r14";
    public static final String R30 = "r30";
    public static final String R90 = "r90";

    private SecurityLogRetentionBucketMapper() {
    }

    public static int normalizedDays(Integer retentionDays) {
        return retentionDays == null ? DEFAULT_RETENTION_DAYS : retentionDays;
    }

    public static String bucket(Integer retentionDays) {
        if (retentionDays == null) {
            return R30;
        }
        if (retentionDays <= 14) {
            return R14;
        }
        if (retentionDays <= 30) {
            return R30;
        }
        return R90;
    }
}
