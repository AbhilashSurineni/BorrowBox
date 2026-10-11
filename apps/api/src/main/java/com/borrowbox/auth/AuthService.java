package com.borrowbox.auth;

import com.borrowbox.common.config.AfterCommit;
import com.borrowbox.common.config.AppProperties;
import com.borrowbox.common.errors.BusinessException;
import com.borrowbox.common.errors.ErrorCode;
import com.borrowbox.common.mail.MailService;
import com.borrowbox.common.security.JwtService;
import com.borrowbox.common.security.TokenHashing;
import com.borrowbox.common.web.Ids;
import com.borrowbox.users.User;
import com.borrowbox.users.UserRepository;
import com.borrowbox.users.UserStatus;
import com.borrowbox.users.UserViews;
import com.borrowbox.users.UserViews.PrivateUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  static final int EMAIL_VERIFICATION_TTL_HOURS = 48;
  static final int PASSWORD_RESET_TTL_MINUTES = 60;

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final AuthTokenRepository authTokens;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final MailService mail;
  private final AppProperties props;
  /** Compared against for unknown emails so login timing does not reveal accounts. */
  private final String dummyHash;

  public AuthService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      AuthTokenRepository authTokens,
      PasswordEncoder encoder,
      JwtService jwt,
      MailService mail,
      AppProperties props) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.authTokens = authTokens;
    this.encoder = encoder;
    this.jwt = jwt;
    this.mail = mail;
    this.props = props;
    this.dummyHash = encoder.encode("dummy-password-for-timing");
  }

  public record Session(PrivateUser user, String accessToken, String refreshToken, Instant refreshExpiresAt) {}

  // ---------------------------------------------------------------- registration & verification

  @Transactional
  public PrivateUser register(AuthRequests.RegisterRequest in) {
    assertCampusEmail(in.email());
    if (users.existsByEmail(in.email())) throw BusinessException.conflict(ErrorCode.EMAIL_TAKEN, "An account with this email already exists");
    User u = new User();
    u.name = in.name();
    u.email = in.email();
    u.passwordHash = encoder.encode(in.password());
    u.department = in.department();
    u.phone = in.phone();
    users.saveAndFlush(u);
    sendVerificationEmail(u);
    return UserViews.toPrivate(u);
  }

  @Transactional
  public void resendVerification(String email) {
    users.findByEmail(email).filter(u -> !u.emailVerified && !u.isDeleted()).ifPresent(this::sendVerificationEmail);
  }

  @Transactional
  public void verifyEmail(String token) {
    AuthToken record = consumeAuthToken(token, AuthTokenType.EMAIL_VERIFICATION);
    User u = users.findById(record.userId).orElseThrow();
    u.emailVerified = true;
    users.save(u);
  }

  private void assertCampusEmail(String email) {
    String domain = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
    List<String> allowed = props.allowedEmailDomainList();
    boolean ok = allowed.stream().anyMatch(d -> domain.equals(d) || domain.endsWith("." + d));
    if (!ok) {
      throw BusinessException.unprocessable(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED,
          "Use your campus email (" + allowed.stream().map(d -> "@" + d).collect(Collectors.joining(", ")) + ")");
    }
  }

  private void sendVerificationEmail(User u) {
    String token = createAuthToken(u.id, AuthTokenType.EMAIL_VERIFICATION, Duration.ofHours(EMAIL_VERIFICATION_TTL_HOURS));
    String to = u.email;
    String text = "Welcome to BorrowBox!\n\nConfirm your email address: " + props.appUrl() + "/verify-email?token=" + token
        + "\n\nThe link expires in " + EMAIL_VERIFICATION_TTL_HOURS + " hours.";
    AfterCommit.run(() -> mail.send(to, "Verify your BorrowBox account", text));
  }

  /** Single-use token; only the newest of each type stays valid. Stored as SHA-256. */
  private String createAuthToken(String userId, AuthTokenType type, Duration ttl) {
    String raw = TokenHashing.randomToken();
    Instant now = Instant.now();
    authTokens.invalidateOutstanding(userId, type, now);
    AuthToken t = new AuthToken();
    t.userId = userId;
    t.type = type;
    t.tokenHash = TokenHashing.sha256Hex(raw);
    t.expiresAt = now.plus(ttl);
    authTokens.save(t);
    return raw;
  }

  private AuthToken consumeAuthToken(String raw, AuthTokenType type) {
    AuthToken record = authTokens.findByTokenHash(TokenHashing.sha256Hex(raw)).orElse(null);
    if (record == null || record.type != type || record.usedAt != null || record.expiresAt.isBefore(Instant.now())) {
      throw invalidLink();
    }
    if (authTokens.consume(record.id, Instant.now()) == 0) throw invalidLink();
    return record;
  }

  private static BusinessException invalidLink() {
    return BusinessException.badRequest(ErrorCode.INVALID_TOKEN, "This link is invalid or has expired");
  }

  // ---------------------------------------------------------------- sessions

  @Transactional
  public Session login(String email, String password) {
    User u = users.findByEmail(email).orElse(null);
    boolean ok = encoder.matches(password, u != null ? u.passwordHash : dummyHash);
    if (u == null || !ok || u.isDeleted()) throw BusinessException.unauthorized(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
    if (u.status == UserStatus.SUSPENDED) throw BusinessException.forbidden(ErrorCode.ACCOUNT_SUSPENDED, "Your account is suspended");
    if (u.status == UserStatus.BANNED) throw BusinessException.forbidden(ErrorCode.ACCOUNT_BANNED, "Your account is banned");
    return newSession(u, Ids.newId());
  }

  private Session newSession(User u, String familyId) {
    String jti = Ids.newId();
    Instant expiresAt = Instant.now().plus(Duration.ofDays(props.jwt().refreshTokenTtlDays()));
    String token = jwt.signRefreshToken(u.id, jti, familyId, expiresAt);
    RefreshToken rt = new RefreshToken();
    rt.id = jti;
    rt.userId = u.id;
    rt.familyId = familyId;
    rt.tokenHash = TokenHashing.sha256Hex(token);
    rt.expiresAt = expiresAt;
    rt.createdAt = Instant.now();
    refreshTokens.save(rt);
    return new Session(UserViews.toPrivate(u), jwt.signAccessToken(u.id, u.role.name()), token, expiresAt);
  }

  /**
   * Rotates the refresh token. Re-use of an already-rotated token revokes the whole family.
   * Failures are returned (not thrown inside the transaction) so revocations still commit.
   */
  @Transactional(noRollbackFor = BusinessException.class)
  public Session refresh(String rawToken) {
    var claims = rawToken == null ? null : jwt.verifyRefreshToken(rawToken).orElse(null);
    if (claims == null) throw invalidRefresh();
    RefreshToken stored = refreshTokens.findByTokenHash(TokenHashing.sha256Hex(rawToken)).orElse(null);
    if (stored == null || !stored.userId.equals(claims.userId()) || !stored.id.equals(claims.jti())) throw invalidRefresh();
    Instant now = Instant.now();
    if (stored.revokedAt != null) {
      refreshTokens.revokeFamily(stored.familyId, now);
      throw BusinessException.unauthorized(ErrorCode.INVALID_TOKEN, "Refresh token reuse detected; please log in again");
    }
    if (stored.expiresAt.isBefore(now)) throw BusinessException.unauthorized(ErrorCode.INVALID_TOKEN, "Refresh token expired");
    User u = users.findById(stored.userId).orElse(null);
    if (u == null || u.isDeleted()) throw invalidRefresh();
    if (u.status != UserStatus.ACTIVE) {
      refreshTokens.revokeAllForUser(u.id, now);
      throw new BusinessException(HttpStatus.FORBIDDEN,
          u.status == UserStatus.BANNED ? ErrorCode.ACCOUNT_BANNED : ErrorCode.ACCOUNT_SUSPENDED,
          "Your account is " + u.status.name().toLowerCase(Locale.ROOT));
    }
    // Guarded revoke: of two concurrent refreshes with the same token, only one rotates.
    if (refreshTokens.revokeIfActive(stored.id, now) == 0) throw invalidRefresh();
    Session next = newSession(u, stored.familyId);
    RefreshToken old = refreshTokens.findById(stored.id).orElseThrow();
    old.replacedBy = refreshTokens.findByTokenHash(TokenHashing.sha256Hex(next.refreshToken())).map(t -> t.id).orElse(null);
    refreshTokens.save(old);
    return next;
  }

  private static BusinessException invalidRefresh() {
    return BusinessException.unauthorized(ErrorCode.INVALID_TOKEN, "Invalid refresh token");
  }

  @Transactional
  public void logout(String rawToken) {
    if (rawToken == null || jwt.verifyRefreshToken(rawToken).isEmpty()) return;
    refreshTokens.findByTokenHash(TokenHashing.sha256Hex(rawToken)).ifPresent(t -> refreshTokens.revokeIfActive(t.id, Instant.now()));
  }

  // ---------------------------------------------------------------- password reset

  @Transactional
  public void forgotPassword(String email) {
    User u = users.findByEmail(email).orElse(null);
    // Same response whether or not the account exists (no user enumeration).
    if (u == null || u.isDeleted() || u.status == UserStatus.BANNED) return;
    String token = createAuthToken(u.id, AuthTokenType.PASSWORD_RESET, Duration.ofMinutes(PASSWORD_RESET_TTL_MINUTES));
    String to = u.email;
    String text = "Reset your password: " + props.appUrl() + "/reset-password?token=" + token + "\n\nThe link expires in "
        + PASSWORD_RESET_TTL_MINUTES + " minutes. If you did not ask for this, ignore this email.";
    AfterCommit.run(() -> mail.send(to, "Reset your BorrowBox password", text));
  }

  @Transactional
  public void resetPassword(String token, String password) {
    AuthToken record = consumeAuthToken(token, AuthTokenType.PASSWORD_RESET);
    User u = users.findById(record.userId).orElseThrow();
    u.passwordHash = encoder.encode(password);
    users.save(u);
    refreshTokens.revokeAllForUser(u.id, Instant.now());
  }
}
