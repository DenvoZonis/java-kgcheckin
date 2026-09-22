package xyz.denvo.service;

import xyz.denvo.util.AppConfig;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 通过 SMTP 发送通知邮件，用于 checkin 失败时提醒用户人工接管。
 * <p>
 * 本功能是可选项，由配置项 {@code mailEnabled} 控制（默认关闭）。未启用时 {@link #send} 是空操作；
 * 配置不完整或发送失败都只记录日志，不会影响调用方的退出码。
 */
public final class MailService {

    private static final Logger LOG = LoggerFactory.getLogger(MailService.class);
    private static final String TIMEOUT_MS = "15000";

    private MailService() {}

    /** 按配置发送一封纯文本邮件。 */
    public static void send(String subject, String body) {
        if (!AppConfig.getBoolean("mailEnabled", false)) {
            return;
        }

        String host = AppConfig.getString("mailHost", "");
        String username = AppConfig.getString("mailUsername", "");
        String password = AppConfig.getString("mailPassword", "");
        String to = AppConfig.getString("mailTo", "");
        int port = (int) AppConfig.getLong("mailPort", 465);
        boolean ssl = AppConfig.getBoolean("mailSsl", true);

        if (host.isEmpty() || username.isEmpty() || to.isEmpty()) {
            LOG.error("已启用邮件通知，但 mailHost / mailUsername / mailTo 未填写完整，跳过发送");
            return;
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        if (ssl) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        // 避免 SMTP 无响应时把定时任务一直卡住
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        props.put("mail.smtp.timeout", TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MS);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject, StandardCharsets.UTF_8.name());
            message.setText(body, StandardCharsets.UTF_8.name());
            Transport.send(message);
            LOG.info("已发送邮件通知到 {}", to);
        } catch (Exception e) {
            LOG.error("发送邮件通知失败: {}", e.getMessage());
        }
    }
}
