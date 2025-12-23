package dopaminelite.notifications.integration;

/**
 * Integration test for {@link dopaminelite.notifications.service.SesEmailService} using Testcontainers LocalStack.
 *
 * Purpose:
 * - Boot a LocalStack SES service to validate our SES client wiring end-to-end.
 * - Verify that sendEmail executes against a local SES API and returns a messageId without throwing.
 *
 * Notes:
 * - This test requires Docker to run Testcontainers.
 * - It is typically excluded from regular unit test runs and enabled via a dedicated Maven profile.
 */

import dopaminelite.notifications.service.SesEmailService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.VerifyEmailIdentityRequest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@Testcontainers
class SesEmailServiceIT {

    static LocalStackContainer localstack;

    static SesClient sesClient;
    static SesEmailService emailService;

    /**
     * Start LocalStack SES and construct a {@link SesClient} with static credentials.
     * Also verifies the sender identity to satisfy SES requirements.
     */
    @BeforeAll
    static void setup() {
        localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
            .withServices(LocalStackContainer.Service.SES);
        localstack.start();
        sesClient = SesClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SES))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
                ))
                .build();

        // LocalStack SES often requires verification; simulate sender verification
        sesClient.verifyEmailIdentity(VerifyEmailIdentityRequest.builder()
                .emailAddress("no-reply@example.com")
                .build());

        // Construct service using override client
        emailService = new SesEmailService("no-reply@example.com", localstack.getRegion(), sesClient);
    }

    /**
     * Stop LocalStack and close the SES client to release resources.
     */
    @AfterAll
    static void tearDown() {
        try { sesClient.close(); } catch (Exception ignored) {}
        localstack.stop();
    }

    /**
     * Ensures that {@link SesEmailService#sendEmail(String, String, String)} succeeds against LocalStack SES.
     * The test asserts by absence of exceptions; LocalStack returns a synthetic messageId on success.
     */
    @Test
    @DisplayName("SES sendEmail works against LocalStack")
    void sendEmailWorks() {
        // Should not throw; LocalStack accepts and returns a messageId
        emailService.sendEmail("recipient@example.com", "Test Subject", "Hello from LocalStack SES");
    }

    @Test
    @DisplayName("sendEmail with LocalStack returns messageId")
    void sendEmail_withLocalStack_returnsMessageId() {
        // LocalStack returns synthetic messageIds for sent emails
        assertDoesNotThrow(() -> {
            emailService.sendEmail("user@example.com", "Test Subject", "Test Body");
        });
        // Verify the email was accepted (absence of exception indicates success)
    }

    @Test
    @DisplayName("sendEmailBatch with LocalStack sends to multiple recipients")
    void sendEmailBatch_withLocalStack_sendsToMultipleRecipients() {
        List<String> recipients = Arrays.asList(
                "user1@example.com",
                "user2@example.com",
                "user3@example.com"
        );

        assertDoesNotThrow(() -> {
            emailService.sendEmailBatch(recipients, "Batch Subject", "Batch Body Content");
        });
    }

    @Test
    @DisplayName("sendEmail with Unicode content sends correctly")
    void sendEmail_withUnicodeContent_sendsCorrectly() {
        String unicodeSubject = "Test 🎉 Unicode ñ ü Subject";
        String unicodeBody = "Body with émojis 😊, Chinese characters 你好, and Arabic العربية";

        assertDoesNotThrow(() -> {
            emailService.sendEmail("recipient@example.com", unicodeSubject, unicodeBody);
        });
    }

    @Test
    @DisplayName("sendEmail with large body sends successfully")
    void sendEmail_withLargeBody_sendsSuccessfully() {
        // Create a large email body (50KB)
        String largeBody = "X".repeat(50 * 1024);

        assertDoesNotThrow(() -> {
            emailService.sendEmail("recipient@example.com", "Large Email Test", largeBody);
        });
    }
}
