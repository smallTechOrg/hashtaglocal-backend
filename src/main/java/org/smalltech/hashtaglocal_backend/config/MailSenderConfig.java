package org.smalltech.hashtaglocal_backend.config;

import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

/**
 * Created to manually construct JavaMailSender instead of relying on Spring Boot's mail
 * autoconfiguration. Autoconfiguration fails when SMTP credentials are blank, which is the case
 * for Google Workspace IP-authenticated SMTP relay that does not require a username or password.
 */
@Configuration
public class MailSenderConfig {

  /**
   * Builds JavaMailSender manually so blank SMTP username/password are treated as no credentials.
   * This is required for Google Workspace IP-authenticated SMTP relay.
   */
  @Bean
  public JavaMailSender javaMailSender(Environment environment) {
    JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    mailSender.setHost(environment.getProperty("spring.mail.host", ""));
    mailSender.setPort(environment.getProperty("spring.mail.port", Integer.class, 587));
    mailSender.setProtocol(environment.getProperty("spring.mail.protocol", "smtp"));
    mailSender.setDefaultEncoding(environment.getProperty("spring.mail.default-encoding", "UTF-8"));

    String username = environment.getProperty("spring.mail.username", "");
    String password = environment.getProperty("spring.mail.password", "");
    // Do not call setUsername/setPassword for blank values; that triggers failed SMTP AUTH.
    if (StringUtils.hasText(username)) {
      mailSender.setUsername(username);
    }
    if (StringUtils.hasText(password)) {
      mailSender.setPassword(password);
    }

    String smtpAuth =
        firstText(environment, "spring.mail.properties.mail.smtp.auth", "SPRING_MAIL_SMTP_AUTH");
    String startTls =
        firstText(
            environment,
            "spring.mail.properties.mail.smtp.starttls.enable",
            "SPRING_MAIL_SMTP_STARTTLS_ENABLE");
    String smtpFrom =
        firstText(
            environment,
            "spring.mail.properties.mail.smtp.from",
            "SPRING_MAIL_SMTP_FROM",
            "account.deletion.from-email",
            "ACCOUNT_DELETION_FROM_EMAIL");
    String smtpLocalhost =
        firstText(
            environment,
            "spring.mail.properties.mail.smtp.localhost",
            "SPRING_MAIL_SMTP_LOCALHOST");

    // Google SMTP relay uses these values to associate the allowed IP with the Workspace domain.
    Properties javaMailProperties = new Properties();
    javaMailProperties.put("mail.smtp.auth", StringUtils.hasText(smtpAuth) ? smtpAuth : "false");
    javaMailProperties.put(
        "mail.smtp.starttls.enable", StringUtils.hasText(startTls) ? startTls : "true");
    if (StringUtils.hasText(smtpFrom)) {
      javaMailProperties.put("mail.smtp.from", smtpFrom);
    }
    if (StringUtils.hasText(smtpLocalhost)) {
      javaMailProperties.put("mail.smtp.localhost", smtpLocalhost);
    }
    mailSender.setJavaMailProperties(javaMailProperties);

    System.out.println(
        "Mail sender configured | host="
            + mailSender.getHost()
            + " | port="
            + mailSender.getPort()
            + " | usernameConfigured="
            + StringUtils.hasText(username)
            + " | smtpAuth="
            + javaMailProperties.getProperty("mail.smtp.auth")
            + " | smtpFrom="
            + javaMailProperties.getProperty("mail.smtp.from")
            + " | smtpLocalhost="
            + javaMailProperties.getProperty("mail.smtp.localhost"));

    return mailSender;
  }

  private String firstText(Environment environment, String... propertyNames) {
    for (String propertyName : propertyNames) {
      String value = environment.getProperty(propertyName);
      if (StringUtils.hasText(value)) {
        return value;
      }
    }
    return "";
  }
}
