package dopaminelite.notifications.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

import java.util.List;

/**
 * Email delivery via AWS SES.
 * Uses AWS SDK v2 with DefaultCredentialsProvider.
 * Ensure environment has AWS credentials and region configured.
 */
@Slf4j
@Service
public class SesEmailService {

    @Value("${notifications.email.sender}")
    private String senderEmail;

    @Value("${notifications.email.region}")
    private String region;

    private final SesClient sesClient;

    /**
     * Primary constructor for Spring where client is built from environment.
     */
    @Autowired
    public SesEmailService(@Value("${notifications.email.sender}") String senderEmail,
                           @Value("${notifications.email.region}") String region) {
        this.senderEmail = senderEmail;
        this.region = region;
        this.sesClient = buildClient();
    }

    /**
     * Testing/override constructor that accepts a preconfigured SesClient.
     */
    public SesEmailService(String senderEmail, String region, SesClient sesClient) {
        this.senderEmail = senderEmail;
        this.region = region;
        this.sesClient = sesClient;
    }

    private SesClient buildClient() {
        return SesClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Cleanup SES client when Spring context shuts down.
     */
    @jakarta.annotation.PreDestroy
    public void cleanup() {
        if (sesClient != null) {
            sesClient.close();
        }
    }

    /**
     * Send a simple text email to a single recipient.
     */
    public void sendEmail(String recipientEmail, String subject, String bodyText) {
        try {
            Destination destination = Destination.builder()
                    .toAddresses(recipientEmail)
                    .build();

            Content subjectContent = Content.builder().data(subject).build();
            Content bodyContent = Content.builder().data(bodyText).build();

            Message message = Message.builder()
                    .subject(subjectContent)
                    .body(b -> b.text(bodyContent))
                    .build();

            SendEmailRequest request = SendEmailRequest.builder()
                    .destination(destination)
                    .message(message)
                    .source(senderEmail)
                    .build();

            SendEmailResponse response = sesClient.sendEmail(request);
            log.info("SES send success: messageId={}", response.messageId());
        } catch (Exception e) {
            log.error("SES send failed", e);
            throw e;
        }
    }

    /**
     * Send a simple text email to multiple recipients.
     */
    public void sendEmailBatch(List<String> recipientEmails, String subject, String bodyText) {
        try {
            Destination destination = Destination.builder()
                    .toAddresses(recipientEmails)
                    .build();

            Content subjectContent = Content.builder().data(subject).build();
            Content bodyContent = Content.builder().data(bodyText).build();

            Message message = Message.builder()
                    .subject(subjectContent)
                    .body(b -> b.text(bodyContent))
                    .build();

            SendEmailRequest request = SendEmailRequest.builder()
                    .destination(destination)
                    .message(message)
                    .source(senderEmail)
                    .build();

            SendEmailResponse response = sesClient.sendEmail(request);
            log.info("SES batch send success: messageId={}", response.messageId());
        } catch (Exception e) {
            log.error("SES batch send failed", e);
            throw e;
        }
    }
}
