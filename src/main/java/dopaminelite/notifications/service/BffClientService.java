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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * Fetch public data for multiple users in a single BFF call.
     * Calls POST /users/public/batch with a list of user IDs.
     * Returns a map of userId -> UserPublicDataDto for easy lookup.
     * Users not found in BFF are silently omitted from the result map.
     *
     * @param userIds List of user IDs to fetch
     * @return Map of userId to UserPublicDataDto
     */
    public Map<UUID, UserPublicDataDto> getBatchUserPublicData(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }

        log.info("Fetching public data for {} users in a single batch call", userIds.size());

        try {
            RestClient restClient = restClientBuilder
                .baseUrl(bffBaseUrl)
                .build();

            Map<String, List<UUID>> requestBody = new HashMap<>();
            requestBody.put("userIds", userIds);

            BffBatchResponse response = restClient.post()
                .uri("/users/public/batch")
                .header("X-Service-Token", serviceToken)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, resp) -> {
                    throw new RuntimeException("BFF batch request failed with status " + resp.getStatusCode());
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, resp) -> {
                    throw new RuntimeException("BFF server error during batch user fetch (status " + resp.getStatusCode() + ")");                })
                .body(BffBatchResponse.class);

            if (response == null || response.getData() == null) {
                log.warn("BFF batch response was empty for {} users", userIds.size());
                return new HashMap<>();
            }

            // Index by userId for O(1) lookup in the calling service
            Map<UUID, UserPublicDataDto> result = new HashMap<>();
            for (UserPublicDataDto user : response.getData()) {
                if (user.getId() != null) {
                    result.put(user.getId(), user);
                }
            }

            log.info("Batch fetch complete: requested={}, returned={}", userIds.size(), result.size());
            return result;

        } catch (Exception e) {
            throw new RuntimeException("Failed to batch fetch user public data", e);
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

    /**
     * Wrapper class for BFF batch API response format.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BffBatchResponse {
        private Boolean success;
        private List<UserPublicDataDto> data;
    }
}
