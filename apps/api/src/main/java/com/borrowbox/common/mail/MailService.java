package com.borrowbox.common.mail;

import com.borrowbox.common.config.AppProperties;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Sends plain-text emails. Modes (app.mail.mode): smtp (JavaMailSender), log (print to the
 * console; dev profile, and the fallback when no SMTP host is configured), memory (tests).
 */
@Service
public class MailService {
  private static final Logger log = LoggerFactory.getLogger(MailService.class);

  public record Mail(String to, String subject, String text) {}

  private final AppProperties props;
  private final ObjectProvider<JavaMailSender> sender;
  private final List<Mail> outbox = new CopyOnWriteArrayList<>();

  public MailService(AppProperties props, ObjectProvider<JavaMailSender> sender) {
    this.props = props;
    this.sender = sender;
  }

  public void send(String to, String subject, String text) {
    String mode = props.mail().mode();
    if ("memory".equals(mode)) {
      outbox.add(new Mail(to, subject, text));
      return;
    }
    JavaMailSender smtp = sender.getIfAvailable();
    if (!"smtp".equals(mode) || smtp == null || !hasHost(smtp)) {
      log.info("\n[email] to={}\n[email] subject={}\n{}\n", to, subject, text);
      return;
    }
    SimpleMailMessage msg = new SimpleMailMessage();
    msg.setFrom(props.mail().from());
    msg.setTo(to);
    msg.setSubject(subject);
    msg.setText(text);
    smtp.send(msg);
  }

  private static boolean hasHost(JavaMailSender smtp) {
    return !(smtp instanceof org.springframework.mail.javamail.JavaMailSenderImpl impl) || StringUtils.hasText(impl.getHost());
  }

  /** Emails captured in memory mode (tests read verification/reset links from here). */
  public List<Mail> outbox() {
    return outbox;
  }
}
