package com.example.emailsystem.service;

import com.example.emailsystem.domain.EmailDirection;
import com.example.emailsystem.domain.EmailMessage;
import com.example.emailsystem.domain.User;
import com.example.emailsystem.repository.EmailMessageRepository;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class EmailService {

    private final EmailMessageRepository emailMessageRepository;
    private final JavaMailSender mailSender;
    private final String smtpUsername;
    private final String pop3Host;
    private final int pop3Port;
    private final boolean pop3Ssl;
    private final String pop3Username;
    private final String pop3Password;
    private final int pop3MaxMessages;

    public EmailService(
            EmailMessageRepository emailMessageRepository,
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String smtpUsername,
            @Value("${app.mail.pop3.host:}") String pop3Host,
            @Value("${app.mail.pop3.port}") int pop3Port,
            @Value("${app.mail.pop3.ssl}") boolean pop3Ssl,
            @Value("${app.mail.pop3.username:}") String pop3Username,
            @Value("${app.mail.pop3.password:}") String pop3Password,
            @Value("${app.mail.pop3.max-messages}") int pop3MaxMessages) {
        this.emailMessageRepository = emailMessageRepository;
        this.mailSender = mailSender;
        this.smtpUsername = smtpUsername;
        this.pop3Host = pop3Host;
        this.pop3Port = pop3Port;
        this.pop3Ssl = pop3Ssl;
        this.pop3Username = pop3Username;
        this.pop3Password = pop3Password;
        this.pop3MaxMessages = pop3MaxMessages;
    }

    @Transactional
    public EmailMessage send(User owner, String recipient, String subject, String body) {
        EmailMessage saved = saveMessage(
                owner,
                EmailDirection.DRAFT,
                owner.getEmail(),
                recipient,
                subject,
                body,
                LocalDateTime.now(),
                null);

        if (!StringUtils.hasText(smtpUsername)) {
            return saved;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            message.setFrom(new InternetAddress(owner.getEmail()));
            message.setRecipients(Message.RecipientType.TO, recipient);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);

            saved.setDirection(EmailDirection.SENT);
            saved.setProviderMessageId(message.getMessageID());
            return emailMessageRepository.save(saved);
        } catch (MessagingException | MailException ex) {
            throw new IllegalStateException("Unable to send email. Check SMTP settings and credentials.", ex);
        }
    }

    @Transactional(readOnly = true)
    public List<EmailMessage> history(User owner) {
        return emailMessageRepository.findByOwnerOrderByMessageDateDescCreatedAtDesc(owner);
    }

    @Transactional(readOnly = true)
    public EmailMessage getOwnedMessage(User owner, Long id) {
        EmailMessage message = emailMessageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Email not found."));
        if (!message.getOwner().getId().equals(owner.getId())) {
            throw new IllegalArgumentException("Email not found.");
        }
        return message;
    }

    @Transactional
    public int receive(User owner) {
        if (!StringUtils.hasText(pop3Host) || !StringUtils.hasText(pop3Username) || !StringUtils.hasText(pop3Password)) {
            throw new IllegalStateException("POP3 settings are not configured.");
        }

        Properties properties = new Properties();
        String protocol = pop3Ssl ? "pop3s" : "pop3";
        properties.put("mail.store.protocol", protocol);
        properties.put("mail." + protocol + ".host", pop3Host);
        properties.put("mail." + protocol + ".port", String.valueOf(pop3Port));
        properties.put("mail." + protocol + ".connectiontimeout", "10000");
        properties.put("mail." + protocol + ".timeout", "10000");
        properties.put("mail." + protocol + ".writetimeout", "10000");

        int imported = 0;
        try {
            Session session = Session.getInstance(properties);
            Store store = session.getStore(protocol);
            store.connect(pop3Host, pop3Port, pop3Username, pop3Password);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            int messageCount = inbox.getMessageCount();
            int start = Math.max(1, messageCount - pop3MaxMessages + 1);
            Message[] messages = messageCount == 0 ? new Message[0] : inbox.getMessages(start, messageCount);

            for (int i = messages.length - 1; i >= 0; i--) {
                Message source = messages[i];
                String providerId = firstHeader(source, "Message-ID");
                if (providerId != null && emailMessageRepository.existsByOwnerAndProviderMessageId(owner, providerId)) {
                    continue;
                }

                Address[] from = source.getFrom();
                Address[] recipients = source.getRecipients(Message.RecipientType.TO);
                String body = extractText(source);
                saveMessage(
                        owner,
                        EmailDirection.RECEIVED,
                        addressValue(from, "unknown@example.com"),
                        addressValue(recipients, owner.getEmail()),
                        source.getSubject() == null ? "(no subject)" : source.getSubject(),
                        StringUtils.hasText(body) ? body : "(empty message)",
                        messageDate(source),
                        providerId);
                imported++;
            }

            inbox.close(false);
            store.close();
            return imported;
        } catch (MessagingException | IOException ex) {
            throw new IllegalStateException("Unable to receive email. Check POP3 settings and credentials.", ex);
        }
    }

    private EmailMessage saveMessage(
            User owner,
            EmailDirection direction,
            String sender,
            String recipient,
            String subject,
            String body,
            LocalDateTime messageDate,
            String providerMessageId) {
        EmailMessage emailMessage = new EmailMessage();
        emailMessage.setOwner(owner);
        emailMessage.setDirection(direction);
        emailMessage.setSender(sender);
        emailMessage.setRecipient(recipient);
        emailMessage.setSubject(subject);
        emailMessage.setBody(body);
        emailMessage.setMessageDate(messageDate);
        emailMessage.setProviderMessageId(providerMessageId);
        return emailMessageRepository.save(emailMessage);
    }

    private LocalDateTime messageDate(Message message) throws MessagingException {
        if (message.getReceivedDate() != null) {
            return LocalDateTime.ofInstant(message.getReceivedDate().toInstant(), ZoneId.systemDefault());
        }
        if (message.getSentDate() != null) {
            return LocalDateTime.ofInstant(message.getSentDate().toInstant(), ZoneId.systemDefault());
        }
        return LocalDateTime.now();
    }

    private String firstHeader(Message message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        return values == null || values.length == 0 ? null : values[0];
    }

    private String extractText(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            return String.valueOf(part.getContent());
        }
        if (part.isMimeType("text/html")) {
            return String.valueOf(part.getContent()).replaceAll("<[^>]*>", " ");
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                builder.append(extractText(multipart.getBodyPart(i))).append('\n');
            }
            return builder.toString().trim();
        }
        return "";
    }

    private String addressValue(Address[] addresses, String fallback) {
        if (addresses == null || addresses.length == 0) {
            return fallback;
        }
        if (addresses[0] instanceof InternetAddress internetAddress) {
            return internetAddress.getAddress();
        }
        return addresses[0].toString();
    }
}
