package org.smalltech.hashtaglocal_backend.job;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Keeps one run of a job at a time across all instances, using a PostgreSQL advisory lock.
 *
 * <p>The lock lives on its own connection for the whole run and is released when the run ends, and
 * by the database if the instance dies mid-run. Two Cloud Scheduler calls (or a scheduler call and
 * a manual one) therefore never overlap; the second is told the job is already running.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobLock {

  /** Any constant works; it only has to be the same everywhere and unused by anything else. */
  private static final long KEY_BASE = 7_340_000_000L;

  private final DataSource dataSource;

  /**
   * Runs the job if no other run holds the lock.
   *
   * @return false when another run is in progress (the job was not run)
   * @throws SQLException when the database cannot be reached to take the lock
   */
  public boolean runExclusive(String jobName, Runnable job) throws SQLException {
    long key = KEY_BASE + (jobName.hashCode() & 0xFFFFF);
    try (Connection connection = dataSource.getConnection()) {
      if (!query(connection, "SELECT pg_try_advisory_lock(?)", key)) {
        return false;
      }
      try {
        job.run();
        return true;
      } finally {
        try {
          query(connection, "SELECT pg_advisory_unlock(?)", key);
        } catch (SQLException e) {
          // Drop the connection instead of returning it to the pool with the lock still held.
          log.warn("Could not release the lock of job {}: {}", jobName, e.getMessage());
          connection.abort(Runnable::run);
        }
      }
    }
  }

  private static boolean query(Connection connection, String sql, long key) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, key);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next() && rs.getBoolean(1);
      }
    }
  }
}
