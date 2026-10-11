package com.borrowbox.common.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
    String appUrl,
    String apiPublicUrl,
    String corsOrigins,
    String allowedEmailDomains,
    String timezone,
    int bcryptStrength,
    boolean cronEnabled,
    boolean rateLimitEnabled,
    Jwt jwt,
    Cookie cookie,
    Mail mail,
    Storage storage,
    Cloudinary cloudinary,
    Seed seed) {

  public record Jwt(String secret, String refreshSecret, Duration accessTokenTtl, int refreshTokenTtlDays) {}

  public record Cookie(String sameSite, boolean secure) {}

  public record Mail(String mode, String from) {}

  public record Storage(String driver, String uploadDir) {}

  public record Cloudinary(String cloudName, String apiKey, String apiSecret) {}

  public record Seed(String adminEmail, String adminPassword, boolean demoData) {}

  public List<String> corsOriginList() {
    return split(corsOrigins);
  }

  public List<String> allowedEmailDomainList() {
    return split(allowedEmailDomains).stream().map(String::toLowerCase).toList();
  }

  private static List<String> split(String s) {
    if (s == null) return List.of();
    return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
  }
}
