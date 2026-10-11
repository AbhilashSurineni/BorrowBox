package com.borrowbox.auth;

import com.borrowbox.auth.AuthRequests.*;
import com.borrowbox.common.config.AppProperties;
import com.borrowbox.common.errors.BusinessException;
import com.borrowbox.common.errors.ValidationErrors;
import com.borrowbox.users.UserViews.PrivateUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService auth;
  private final AppProperties props;

  public AuthController(AuthService auth, AppProperties props) {
    this.auth = auth;
    this.props = props;
  }

  public record RegisterResponse(PrivateUser user, String message) {}

  public record SessionResponse(PrivateUser user, String accessToken) {}

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public RegisterResponse register(@Valid @RequestBody RegisterRequest body) {
    return new RegisterResponse(auth.register(body), "Account created. Check your email to verify your address.");
  }

  @GetMapping("/verify-email")
  public Map<String, Boolean> verifyEmail(@RequestParam(required = false) String token) {
    if (token == null || token.length() < 10 || token.length() > 200) throw ValidationErrors.single("token", "Invalid token");
    auth.verifyEmail(token);
    return Map.of("verified", true);
  }

  @PostMapping("/resend-verification")
  public Map<String, String> resend(@Valid @RequestBody EmailRequest body) {
    auth.resendVerification(body.email());
    return Map.of("message", "If the account exists and is unverified, a new link has been sent.");
  }

  @PostMapping("/login")
  public ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest body) {
    AuthService.Session s = auth.login(body.email(), body.password());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, RefreshCookie.issue(props, s.refreshToken(), s.refreshExpiresAt()).toString())
        .body(new SessionResponse(s.user(), s.accessToken()));
  }

  @PostMapping("/refresh")
  public ResponseEntity<SessionResponse> refresh(@CookieValue(name = RefreshCookie.NAME, required = false) String token, HttpServletResponse res) {
    try {
      AuthService.Session s = auth.refresh(token);
      return ResponseEntity.ok()
          .header(HttpHeaders.SET_COOKIE, RefreshCookie.issue(props, s.refreshToken(), s.refreshExpiresAt()).toString())
          .body(new SessionResponse(s.user(), s.accessToken()));
    } catch (BusinessException e) {
      res.addHeader(HttpHeaders.SET_COOKIE, RefreshCookie.clear(props).toString());
      throw e;
    }
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@CookieValue(name = RefreshCookie.NAME, required = false) String token) {
    auth.logout(token);
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, RefreshCookie.clear(props).toString()).build();
  }

  @PostMapping("/forgot-password")
  public Map<String, String> forgotPassword(@Valid @RequestBody EmailRequest body) {
    auth.forgotPassword(body.email());
    return Map.of("message", "If an account exists for that email, a reset link has been sent.");
  }

  @PostMapping("/reset-password")
  public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
    auth.resetPassword(body.token(), body.password());
    return Map.of("message", "Password updated. Please log in.");
  }
}
