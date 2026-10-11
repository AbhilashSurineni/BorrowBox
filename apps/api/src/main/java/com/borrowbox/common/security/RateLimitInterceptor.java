package com.borrowbox.common.security;

import com.borrowbox.common.config.AppProperties;
import com.borrowbox.common.errors.ErrorBody;
import com.borrowbox.common.errors.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Bucket4j rate limits with the same windows as the TS API (in-memory, per instance):
 * login 10/15 min per IP; register + resend-verification 10/h per IP; forgot + reset password
 * 5/h per IP; loan requests 30/h per user. Disabled in the test profile.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
  private record Rule(String method, String pattern, String group, int capacity, Duration window, boolean perUser) {}

  private static final List<Rule> RULES = List.of(
      new Rule("POST", "/api/v1/auth/login", "login", 10, Duration.ofMinutes(15), false),
      new Rule("POST", "/api/v1/auth/register", "register", 10, Duration.ofHours(1), false),
      new Rule("POST", "/api/v1/auth/resend-verification", "register", 10, Duration.ofHours(1), false),
      new Rule("POST", "/api/v1/auth/forgot-password", "password", 5, Duration.ofHours(1), false),
      new Rule("POST", "/api/v1/auth/reset-password", "password", 5, Duration.ofHours(1), false),
      new Rule("POST", "/api/v1/items/*/loans", "loan-request", 30, Duration.ofHours(1), true));

  private final AntPathMatcher matcher = new AntPathMatcher();
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final AppProperties props;
  private final ObjectMapper mapper;

  public RateLimitInterceptor(AppProperties props, ObjectMapper mapper) {
    this.props = props;
    this.mapper = mapper;
  }

  @Override
  public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
    if (!props.rateLimitEnabled()) return true;
    for (Rule rule : RULES) {
      if (!rule.method().equals(req.getMethod()) || !matcher.match(rule.pattern(), req.getRequestURI())) continue;
      String key = rule.group() + ":" + (rule.perUser() ? userKey(req) : req.getRemoteAddr());
      Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder()
          .addLimit(Bandwidth.builder().capacity(rule.capacity()).refillIntervally(rule.capacity(), rule.window()).build())
          .build());
      if (!bucket.tryConsume(1)) {
        res.setStatus(429);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), ErrorBody.of(ErrorCode.RATE_LIMITED, "Too many requests, try again later"));
        return false;
      }
    }
    return true;
  }

  private static String userKey(HttpServletRequest req) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getPrincipal() instanceof AuthUser u ? u.id() : req.getRemoteAddr();
  }
}
