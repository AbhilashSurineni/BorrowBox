package com.borrowbox.common.security;

import com.borrowbox.users.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads the caller's current role/status so suspensions and bans take effect immediately. */
@Service
public class AuthUserService {
  private final UserRepository users;

  public AuthUserService(UserRepository users) {
    this.users = users;
  }

  public record Loaded(AuthUser user, boolean deleted) {}

  @Transactional(readOnly = true)
  public Optional<Loaded> load(String userId) {
    return users.findById(userId).map(u -> new Loaded(new AuthUser(u.id, u.role, u.status, u.emailVerified), u.isDeleted()));
  }
}
