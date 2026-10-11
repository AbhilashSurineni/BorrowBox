package com.borrowbox.common.security;

import com.borrowbox.common.errors.BusinessException;
import com.borrowbox.common.errors.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link AuthUser} handler parameters. Required by default (TS authenticate), nullable
 * with {@link OptionalAuth}, and {@link Verified} additionally requires a verified email.
 * Declare it as the first parameter so auth errors win over body validation, as in the TS API.
 */
public class AuthUserArgumentResolver implements HandlerMethodArgumentResolver {
  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return AuthUser.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request, WebDataBinderFactory binderFactory) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    AuthUser user = auth != null && auth.getPrincipal() instanceof AuthUser u ? u : null;
    if (parameter.hasParameterAnnotation(OptionalAuth.class)) return user;
    if (user == null) {
      HttpServletRequest req = request.getNativeRequest(HttpServletRequest.class);
      Object err = req == null ? null : req.getAttribute(JwtAuthFilter.AUTH_ERROR);
      if (err instanceof BusinessException be) throw be;
      throw BusinessException.unauthorized(ErrorCode.UNAUTHENTICATED, "Authentication required");
    }
    if (parameter.hasParameterAnnotation(Verified.class) && !user.emailVerified()) {
      throw BusinessException.forbidden(ErrorCode.EMAIL_NOT_VERIFIED, "Verify your email address first");
    }
    return user;
  }
}
