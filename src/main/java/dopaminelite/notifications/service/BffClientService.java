package dopaminelite.notifications.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dopaminelite.notifications.dto.UserPublicDataDto;
import dopaminelite.notifications.exception.ResourceNotFoundException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
     * Fetch public data for multiple users in a single batch HTTP call.
     * Calls POST /users/public/batch endpoint on BFF.
     * This replaces N individual getUserPublicData() calls with a single request,
     * reducing total fetch time from ~25-50 seconds to ~200ms for 500 users.
     *
     * @param userIds List of user UUIDs to fetch
     * @return Map of userId -> UserPublicDataDto for O(1) lookup
     */
    public Map<UUID, UserPublicDataDto> getBatchUserPublicData(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            log.info("Fetching batch user public data for {} users", userIds.size());
            long startTime = System.currentTimeMillis();

            RestClient restClient = restClientBuilder
                .baseUrl(bffBaseUrl)
                .build();

            // Convert UUIDs to strings for the request body
            List<String> userIdStrings = userIds.stream()
                .map(UUID::toString)
                .collect(Collectors.toList());

            Map<String, Object> requestBody = Map.of("userIds", userIdStrings);

            BffBatchResponse response = restClient.post()
                .uri("/users/public/batch")
                .header("X-Service-Token", serviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, resp) -> {
                    throw new RuntimeException("BFF batch request failed with 4xx error");
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, resp) -> {
                    throw new RuntimeException("BFF server error during batch user fetch");
                })
                .body(BffBatchResponse.class);

            if (response == null || response.getData() == null) {
                log.warn("Batch user fetch returned null response");
                return Collections.emptyMap();
            }

            // Convert List to Map<UUID, UserPublicDataDto> for O(1) lookup
            Map<UUID, UserPublicDataDto> resultMap = new HashMap<>();
            for (UserPublicDataDto user : response.getData()) {
                if (user.getId() != null) {
                    resultMap.put(user.getId(), user);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch user fetch completed in {}ms - requested: {}, returned: {}",
                duration, userIds.size(), resultMap.size());

            return resultMap;

        } catch (Exception e) {
            log.error("Failed to fetch batch user public data for {} users", userIds.size(), e);
            throw new RuntimeException("Failed to fetch batch user public data", e);
        }
    }
    
    /**
     * Wrapper class for BFF API response format (single user).
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BffResponse {
        private Boolean success;
        private UserPublicDataDto data;
    }

    /**
     * Wrapper class for BFF batch API response format.
     * Response format: { success: true, data: [{ id, fullName, email, ... }, ...] }
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BffBatchResponse {
        private Boolean success;
        private List<UserPublicDataDto> data;
    }
}
