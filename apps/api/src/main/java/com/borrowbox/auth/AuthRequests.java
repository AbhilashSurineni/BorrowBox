package com.borrowbox.auth;

import static com.borrowbox.common.web.Sanitizer.clean;

import com.borrowbox.users.UserRequests;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public final class AuthRequests {
  private AuthRequests() {}

  private static String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }

  public record RegisterRequest(
      @NotNull(message = "Required") @Size(min = 2, max = 100) String name,
      @NotNull(message = "Required") @Email(message = "Invalid email") @Size(max = 150) String email,
      @NotNull(message = "Required")
          @Size(min = 8, max = 128, message = "Password must be at least 8 characters")
          @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "Password must contain a letter and a number")
          String password,
      @NotNull(message = "Required") @Size(min = 1, max = 100) String department,
      @Pattern(regexp = UserRequests.PHONE, message = "Invalid phone number") String phone) {
    public RegisterRequest {
      name = clean(name);
      email = normalizeEmail(email);
      department = clean(department);
      phone = phone == null || phone.isBlank() ? null : phone.trim();
    }
  }

  public record LoginRequest(
      @NotNull(message = "Required") @Email(message = "Invalid email") String email,
      @NotNull(message = "Required") @Size(min = 1, max = 128) String password) {
    public LoginRequest {
      email = normalizeEmail(email);
    }
  }

  public record EmailRequest(@NotNull(message = "Required") @Email(message = "Invalid email") String email) {
    public EmailRequest {
      email = normalizeEmail(email);
    }
  }

  public record ResetPasswordRequest(
      @NotNull(message = "Required") @Size(min = 10, max = 200) String token,
      @NotNull(message = "Required")
          @Size(min = 8, max = 128, message = "Password must be at least 8 characters")
          @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "Password must contain a letter and a number")
          String password) {}
}
