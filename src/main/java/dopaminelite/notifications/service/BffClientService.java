package dopaminelite.notifications.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dopaminelite.notifications.dto.UserPublicDataDto;
import dopaminelite.notifications.exception.ResourceNotFoundException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Service for communicating with the BFF API.
 * Handles fetching user public data from BFF endpoints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BffClientService {

    private final RestClient.Builder restClientBuilder;

    @Value("${notifications.bff.base-url}")
    private String bffBaseUrl;

    @Value("${notifications.service.internalToken}")
    private String serviceToken;

    /**
     * Fetch user public data from BFF.
     * Calls GET /users/{userId}/public endpoint.
     *
     * @param userId The user ID to fetch
     * @return UserPublicDataDto containing public user information
     * @throws ResourceNotFoundException if user not found
     */
    public UserPublicDataDto getUserPublicData(UUID userId) {
        try {
            RestClient restClient = restClientBuilder
                .baseUrl(bffBaseUrl)
                .build();

            BffResponse response = restClient.get()
                .uri("/users/{userId}/public", userId)
                .header("X-Service-Token", serviceToken)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, resp) -> {
                    throw new ResourceNotFoundException("User not found: " + userId);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, resp) -> {
                    throw new RuntimeException("BFF server error while fetching user: " + userId);
                })
                .body(BffResponse.class);

            if (response == null || response.getData() == null) {
                throw new ResourceNotFoundException("User not found: " + userId);
            }

            return response.getData();

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch user public data: " + userId, e);
        }
    }
    
    /**
     * Wrapper class for BFF API response format.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BffResponse {
        private Boolean success;
        private UserPublicDataDto data;
    }
}
