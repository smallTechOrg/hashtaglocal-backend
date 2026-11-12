package org.smalltech.hashtaglocal_backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the in-process {@code @Scheduled} jobs.
 *
 * <p>On Cloud Run an instance has no reliable background CPU and several instances may run, so the
 * deploy sets {@code SCHEDULER_ENABLED=false} and Cloud Scheduler calls {@code POST /jobs/{name}}
 * instead (see {@link org.smalltech.hashtaglocal_backend.controller.JobController}). Locally and on
 * a VM the default (on) keeps the jobs running on their cron expressions as before.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "jobs.in-process-scheduler",
    havingValue = "true",
    matchIfMissing = true)
public class SchedulingConfig {}
