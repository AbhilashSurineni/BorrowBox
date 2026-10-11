package com.borrowbox.common.time;

import com.borrowbox.common.config.AppProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** "Today" and the current hour in the configured campus timezone (app.timezone). */
@Component
public class CampusClock {
  private final ZoneId zone;

  public CampusClock(AppProperties props) {
    this.zone = ZoneId.of(props.timezone());
  }

  public ZoneId zone() {
    return zone;
  }

  public LocalDate today() {
    return today(Instant.now());
  }

  public LocalDate today(Instant now) {
    return LocalDate.ofInstant(now, zone);
  }

  public int currentHour(Instant now) {
    return now.atZone(zone).getHour();
  }
}
