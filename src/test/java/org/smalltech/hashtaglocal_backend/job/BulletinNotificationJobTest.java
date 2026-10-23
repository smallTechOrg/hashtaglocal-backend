package org.smalltech.hashtaglocal_backend.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.service.BulletinNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(classes = BulletinNotificationJob.class)
@TestPropertySource(
    properties = {"bulletin.enabled=true", "bulletin.notification.cron=0 45 9 * * *"})
@DisplayName("BulletinNotificationJob configuration")
class BulletinNotificationJobTest {

  @Autowired private BulletinNotificationJob bulletinNotificationJob;

  @MockitoBean private BulletinNotificationService bulletinNotificationService;

  @Test
  @DisplayName("Uses bulletin.notification.cron for scheduler expression")
  void usesDedicatedBulletinNotificationCronProperty() {
    Object scheduleExpression =
        ReflectionTestUtils.getField(bulletinNotificationJob, "scheduleExpression");

    assertThat(scheduleExpression).isEqualTo("0 45 9 * * *");
  }
}
