package com.borrowbox.common.security;

import com.borrowbox.common.errors.BusinessException;
import com.borrowbox.common.errors.ErrorCode;
import com.borrowbox.users.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates "Authorization: Bearer <access token>". It never rejects the request itself: a
 * bad/expired token or a suspended account is recorded as a request attribute and turned into
 * the TS API's 401/403 only when the matched handler actually requires a user (see
 * {@link AuthUserArgumentResolver}). Public routes therefore behave like the TS optionalAuth.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
  public static final String AUTH_ERROR = "borrowbox.authError";

  private final JwtService jwt;
  private final AuthUserService authUsers;

  public JwtAuthFilter(JwtService jwt, AuthUserService authUsers) {
    this.jwt = jwt;
    this.authUsers = authUsers;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String header = req.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      authenticate(req, header.substring(7));
    }
    chain.doFilter(req, res);
  }

  private void authenticate(HttpServletRequest req, String token) {
    var claims = jwt.verifyAccessToken(token);
    if (claims.isEmpty()) {
      req.setAttribute(AUTH_ERROR, BusinessException.unauthorized(ErrorCode.INVALID_TOKEN, "Invalid or expired access token"));
      return;
    }
    var loaded = authUsers.load(claims.get().userId());
    if (loaded.isEmpty() || loaded.get().deleted()) {
      req.setAttribute(AUTH_ERROR, BusinessException.unauthorized(ErrorCode.INVALID_TOKEN, "Account no longer exists"));
      return;
    }
    AuthUser user = loaded.get().user();
    if (user.status() == UserStatus.SUSPENDED) {
      req.setAttribute(AUTH_ERROR, new BusinessException(HttpStatus.FORBIDDEN, ErrorCode.ACCOUNT_SUSPENDED, "Your account is suspended"));
      return;
    }
    if (user.status() == UserStatus.BANNED) {
      req.setAttribute(AUTH_ERROR, new BusinessException(HttpStatus.FORBIDDEN, ErrorCode.ACCOUNT_BANNED, "Your account is banned"));
      return;
    }
    var auth = new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }
}
