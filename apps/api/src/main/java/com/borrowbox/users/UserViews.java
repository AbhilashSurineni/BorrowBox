package com.borrowbox.users;

import java.time.Instant;

/** Response shapes for users. Public views never include email or phone. */
public final class UserViews {
  private UserViews() {}

  public record PrivateUser(
      String id,
      String name,
      String email,
      Role role,
      UserStatus status,
      String department,
      String phone,
      String avatarUrl,
      ContactPreference contactPreference,
      boolean emailVerified,
      double ratingAvg,
      int ratingCount,
      Instant createdAt) {}

  public record PublicUser(String id, String name, String department, String avatarUrl, double ratingAvg, int ratingCount) {}

  /** Contact details, revealed only to the counterparty of an approved (or later) loan. */
  public record Contact(String email, String phone, ContactPreference contactPreference) {}

  public static PrivateUser toPrivate(User u) {
    return new PrivateUser(
        u.id, u.name, u.email, u.role, u.status, u.department, u.phone, u.avatarUrl, u.contactPreference,
        u.emailVerified, u.ratingAvg.doubleValue(), u.ratingCount, u.createdAt);
  }

  public static PublicUser toPublic(User u) {
    return new PublicUser(u.id, u.name, u.department, u.avatarUrl, u.ratingAvg.doubleValue(), u.ratingCount);
  }

  public static Contact toContact(User u) {
    return new Contact(u.email, u.contactPreference == ContactPreference.EMAIL ? null : u.phone, u.contactPreference);
  }
}
