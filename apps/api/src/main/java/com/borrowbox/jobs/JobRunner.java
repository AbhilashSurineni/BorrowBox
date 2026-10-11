package com.borrowbox.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a job so that only one API instance executes it at a time (MySQL GET_LOCK held on a
 * dedicated connection for the duration) and, with a runKey, at most once per key (recorded in
 * job_runs) — e.g. once per day for reminders. The job body runs in one transaction; emails go
 * out after commit.
 */
@Component
public class JobRunner {
  private final DataSource dataSource;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final ObjectMapper mapper;

  public JobRunner(DataSource dataSource, JdbcTemplate jdbc, TransactionTemplate tx, ObjectMapper mapper) {
    this.dataSource = dataSource;
    this.jdbc = jdbc;
    this.tx = tx;
    this.mapper = mapper;
  }

  public JobResult run(String job, String runKey, Supplier<Object> body) {
    String lockName = "borrowbox:job:" + job;
    try (Connection lockConn = dataSource.getConnection()) {
      if (!acquire(lockConn, lockName)) return JobResult.skipped(job, "locked");
      try {
        return tx.execute(status -> {
          if (runKey != null) {
            int inserted = jdbc.update("INSERT IGNORE INTO job_runs (job, run_key, created_at) VALUES (?, ?, ?)",
                job, runKey, Timestamp.from(Instant.now()));
            if (inserted == 0) return JobResult.skipped(job, "already-ran");
          }
          Object result = body.get();
          if (runKey != null) {
            jdbc.update("UPDATE job_runs SET result = ? WHERE job = ? AND run_key = ?", json(result), job, runKey);
          }
          return new JobResult(job, null, result);
        });
      } finally {
        release(lockConn, lockName);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Job " + job + " could not acquire its lock", e);
    }
  }

  private static boolean acquire(Connection c, String name) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement("SELECT GET_LOCK(?, 0)")) {
      ps.setString(1, name);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() && rs.getInt(1) == 1;
      }
    }
  }

  private static void release(Connection c, String name) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement("SELECT RELEASE_LOCK(?)")) {
      ps.setString(1, name);
      ps.executeQuery().close();
    }
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      return "null";
    }
  }
}
