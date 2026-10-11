package com.borrowbox.users;

import static com.borrowbox.common.web.Sanitizer.clean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserRequests {
  private UserRequests() {}

  public static final String PHONE = "^\\+?[0-9 ()-]{6,20}$";

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record UpdateProfileRequest(
      @Size(min = 2, max = 100) String name,
      @Size(min = 1, max = 100) String department,
      @Pattern(regexp = PHONE, message = "Invalid phone number") String phone,
      @Pattern(regexp = "EMAIL|PHONE|BOTH", message = "Invalid enum value") String contactPreference,
      Boolean removeAvatar) {
    public UpdateProfileRequest {
      name = clean(name);
      department = clean(department);
      phone = phone == null ? null : phone.trim();
    }
  }
}
