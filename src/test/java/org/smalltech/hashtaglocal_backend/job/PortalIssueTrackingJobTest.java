package org.smalltech.hashtaglocal_backend.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.service.PortalIssueTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(classes = PortalIssueTrackingJob.class)
@TestPropertySource(properties = {"portalissue.enabled=true", "portalissue.cron=0 */7 * * * *"})
@DisplayName("PortalIssueTrackingJob configuration")
class PortalIssueTrackingJobTest {

  @Autowired private PortalIssueTrackingJob portalIssueTrackingJob;

  @MockitoBean private PortalIssueTrackingService portalIssueTrackingService;

  @Test
  @DisplayName("Uses portalissue.cron for scheduler expression")
  void usesDedicatedPortalIssueCronProperty() {
    Object scheduleExpression =
        ReflectionTestUtils.getField(portalIssueTrackingJob, "scheduleExpression");

    assertThat(scheduleExpression).isEqualTo("0 */7 * * * *");
  }
}
