package dopaminelite.notifications.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * Client for User Service to resolve user emails by userId.
 */
@Slf4j
@Component
public class UserServiceClient {

    private final RestClient restClient;
    private final String userServiceUrl;

    public UserServiceClient(
            @Value("${notifications.user-service.url:http://localhost:8082}") String userServiceUrl,
            RestClient.Builder restClientBuilder
    ) {
        this.userServiceUrl = userServiceUrl;
        this.restClient = restClientBuilder.baseUrl(userServiceUrl).build();
    }

    /**
     * Fetch user by userId and return email.
     * Falls back to env var NOTIFICATIONS_TEST_EMAIL if service unavailable.
     */
    public String getUserEmail(UUID userId) {
        try {
            UserDto user = restClient.get()
                    .uri("/api/v1/users/{userId}", userId)
                    .retrieve()
                    .body(UserDto.class);
            
            if (user != null && user.getEmail() != null) {
                return user.getEmail();
            }
            
            log.warn("User {} has no email; falling back to test email", userId);
            return getFallbackEmail();
        } catch (RestClientException e) {
            log.error("Failed to fetch user {} from User Service; using fallback", userId, e);
            return getFallbackEmail();
        }
    }

    private String getFallbackEmail() {
        return System.getenv().getOrDefault("NOTIFICATIONS_TEST_EMAIL", "test@example.com");
    }
}
