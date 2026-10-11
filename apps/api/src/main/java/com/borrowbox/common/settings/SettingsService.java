package com.borrowbox.common.settings;

import com.borrowbox.common.errors.ValidationErrors;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {
  private final SettingRepository repo;
  private final ObjectMapper mapper;

  public SettingsService(SettingRepository repo, ObjectMapper mapper) {
    this.repo = repo;
    this.mapper = mapper;
  }

  /** Defaults merged with stored values; an invalid stored value falls back to the default set. */
  @Transactional(readOnly = true)
  public Settings get() {
    Map<String, Object> merged = new LinkedHashMap<>(mapper.convertValue(Settings.DEFAULTS, Map.class));
    for (Setting s : repo.findAll()) {
      if (!merged.containsKey(s.key)) continue;
      try {
        merged.put(s.key, mapper.readTree(s.value).asInt());
      } catch (Exception ignored) {
        // keep default
      }
    }
    try {
      Settings settings = mapper.convertValue(merged, Settings.class);
      return validate(mapper.convertValue(settings, Map.class)).isEmpty() ? settings : Settings.DEFAULTS;
    } catch (IllegalArgumentException e) {
      return Settings.DEFAULTS;
    }
  }

  /** Strict partial update: unknown keys or out-of-range values -> 400 VALIDATION_ERROR. */
  @Transactional
  public Settings update(Map<String, Object> patch) {
    ValidationErrors errors = validate(patch);
    errors.throwIfAny();
    patch.forEach((key, value) -> {
      Setting s = repo.findById(key).orElseGet(() -> {
        Setting n = new Setting();
        n.key = key;
        return n;
      });
      s.value = String.valueOf(((Number) value).intValue());
      repo.save(s);
    });
    repo.flush();
    return get();
  }

  @Transactional
  public void seedDefaults() {
    Map<String, Object> defaults = mapper.convertValue(Settings.DEFAULTS, Map.class);
    defaults.forEach((key, value) -> {
      if (repo.existsById(key)) return;
      Setting s = new Setting();
      s.key = key;
      s.value = String.valueOf(value);
      repo.save(s);
    });
  }

  private static ValidationErrors validate(Map<String, ?> values) {
    ValidationErrors errors = new ValidationErrors();
    values.forEach((key, value) -> {
      int[] range = Settings.RANGES.get(key);
      if (range == null) {
        errors.form("Unrecognized key(s) in object: '" + key + "'");
      } else if (!(value instanceof Number n) || n.doubleValue() != Math.floor(n.doubleValue())) {
        errors.field(key, "Expected integer");
      } else if (n.intValue() < range[0] || n.intValue() > range[1]) {
        errors.field(key, "Number must be between " + range[0] + " and " + range[1]);
      }
    });
    return errors;
  }
}
