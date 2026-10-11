package com.borrowbox.common.security;

import com.borrowbox.users.Role;
import com.borrowbox.users.UserStatus;

/** The authenticated caller, loaded from the database on every request. */
public record AuthUser(String id, Role role, UserStatus status, boolean emailVerified) {
  public boolean isAdmin() {
    return role == Role.ADMIN;
  }
}
