package com.borrowbox.items;

import static com.borrowbox.common.web.Sanitizer.clean;
import static com.borrowbox.common.web.Sanitizer.cleanOptional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ItemRequests {
  private ItemRequests() {}

  static final String CONDITIONS = "NEW|GOOD|FAIR|WORN";

  static final String UUID = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CreateItemRequest(
      @NotNull(message = "Required") @Size(min = 2, max = 150) String name,
      @NotNull(message = "Required") @Pattern(regexp = UUID, message = "Invalid uuid") String categoryId,
      @NotNull(message = "Required") @Size(min = 1, max = 5000) String description,
      @NotNull(message = "Required") @Pattern(regexp = CONDITIONS, message = "Invalid enum value") String condition,
      @Min(1) @Max(365) Integer maxLoanDays,
      @NotNull(message = "Required") @Size(min = 2, max = 200) String pickupLocation,
      @Size(max = 500) String depositNote) {
    public CreateItemRequest {
      name = clean(name);
      description = clean(description);
      pickupLocation = clean(pickupLocation);
      depositNote = cleanOptional(depositNote);
    }
  }

  /** All fields optional; removeImageIds is parsed separately (JSON array, JSON string or comma list). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record UpdateItemRequest(
      @Size(min = 2, max = 150) String name,
      @Pattern(regexp = UUID, message = "Invalid uuid") String categoryId,
      @Size(min = 1, max = 5000) String description,
      @Pattern(regexp = CONDITIONS, message = "Invalid enum value") String condition,
      @Min(1) @Max(365) Integer maxLoanDays,
      @Size(min = 2, max = 200) String pickupLocation,
      @Size(max = 500) String depositNote) {
    public UpdateItemRequest {
      name = clean(name);
      description = clean(description);
      pickupLocation = clean(pickupLocation);
      depositNote = cleanOptional(depositNote);
    }
  }
}
