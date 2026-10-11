package com.borrowbox.common.config;

import com.borrowbox.common.security.AuthUserArgumentResolver;
import com.borrowbox.common.security.JwtAuthFilter;
import com.borrowbox.common.security.RateLimitInterceptor;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final AppProperties props;
  private final RateLimitInterceptor rateLimit;

  public WebConfig(AppProperties props, RateLimitInterceptor rateLimit) {
    this.props = props;
    this.rateLimit = rateLimit;
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new AuthUserArgumentResolver());
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(rateLimit).addPathPatterns("/api/v1/**");
  }

  /** Local-disk uploads served at /uploads/** (7-day cache). */
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String location = Path.of(props.storage().uploadDir()).toAbsolutePath().normalize().toUri().toString();
    registry.addResourceHandler("/uploads/**")
        .addResourceLocations(location.endsWith("/") ? location : location + "/")
        .setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS));
  }

  /** The JWT filter runs inside the Spring Security chain only, not as a second servlet filter. */
  @Bean
  FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter filter) {
    FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>(filter);
    reg.setEnabled(false);
    return reg;
  }

  @Bean
  FilterRegistrationBean<JsonBodyLimitFilter> jsonBodyLimitFilter() {
    FilterRegistrationBean<JsonBodyLimitFilter> reg = new FilterRegistrationBean<>(new JsonBodyLimitFilter());
    reg.addUrlPatterns("/api/*");
    return reg;
  }
}
