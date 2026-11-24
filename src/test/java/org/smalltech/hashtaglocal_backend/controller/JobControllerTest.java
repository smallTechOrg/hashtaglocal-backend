package org.smalltech.hashtaglocal_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.job.BulletinWeatherJob;
import org.smalltech.hashtaglocal_backend.job.EventIngestionCronJob;
import org.smalltech.hashtaglocal_backend.job.JobLock;
import org.smalltech.hashtaglocal_backend.job.SessionCleanupJob;
import org.smalltech.hashtaglocal_backend.security.JobInvokerVerifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

class JobControllerTest {

  private JobInvokerVerifier verifier;
  private JobLock lock;
  private SessionCleanupJob sessionCleanup;
  private ObjectProvider<BulletinWeatherJob> bulletin;
  private JobController controller;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() throws Exception {
    verifier = mock(JobInvokerVerifier.class);
    lock = mock(JobLock.class);
    sessionCleanup = mock(SessionCleanupJob.class);
    bulletin = mock(ObjectProvider.class);
    when(verifier.verify("Bearer ok")).thenReturn("scheduler@example.com");
    when(verifier.verify(null)).thenThrow(new SecurityException("missing bearer token"));
    when(lock.runExclusive(any(), any(Runnable.class)))
        .thenAnswer(
            inv -> {
              ((Runnable) inv.getArgument(1)).run();
              return true;
            });
    controller =
        new JobController(
            verifier,
            lock,
            mock(EventIngestionCronJob.class),
            bulletin,
            mock(ObjectProvider.class),
            sessionCleanup,
            mock(ObjectProvider.class));
  }

  @Test
  void refusesACallWithoutAValidToken() throws Exception {
    assertEquals(HttpStatus.UNAUTHORIZED, controller.run("session-cleanup", null).getStatusCode());
    verify(lock, never()).runExclusive(any(), any());
    verify(sessionCleanup, never()).run();
  }

  @Test
  void runsTheJobUnderTheLock() throws Exception {
    var response = controller.run("session-cleanup", "Bearer ok");
    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(lock).runExclusive(eq("session-cleanup"), any(Runnable.class));
    verify(sessionCleanup).run();
  }

  @Test
  void unknownJobIsNotFound() {
    assertEquals(HttpStatus.NOT_FOUND, controller.run("nope", "Bearer ok").getStatusCode());
  }

  @Test
  void aSwitchedOffJobIsSkippedWithoutTakingTheLock() throws Exception {
    when(bulletin.getIfAvailable()).thenReturn(null);
    var response = controller.run("bulletin-weather", "Bearer ok");
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("disabled", response.getBody().get("status"));
    verify(lock, never()).runExclusive(any(), any());
  }

  @Test
  void aRunInProgressAnswersConflict() throws Exception {
    when(lock.runExclusive(any(), any(Runnable.class))).thenReturn(false);
    assertEquals(
        HttpStatus.CONFLICT, controller.run("session-cleanup", "Bearer ok").getStatusCode());
  }

  @Test
  void anUnreachableDatabaseAnswersServiceUnavailable() throws Exception {
    when(lock.runExclusive(any(), any(Runnable.class))).thenThrow(new SQLException("down"));
    assertEquals(
        HttpStatus.SERVICE_UNAVAILABLE,
        controller.run("session-cleanup", "Bearer ok").getStatusCode());
  }

  @Test
  void aFailingJobAnswersServerError() throws Exception {
    when(lock.runExclusive(any(), any(Runnable.class))).thenThrow(new IllegalStateException("x"));
    assertEquals(
        HttpStatus.INTERNAL_SERVER_ERROR,
        controller.run("session-cleanup", "Bearer ok").getStatusCode());
  }
}
