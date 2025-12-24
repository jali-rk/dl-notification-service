package dopaminelite.notifications.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * User public data DTO matching BFF's /users/{userId}/public response.
 * Contains public information about a user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserPublicDataDto {
    
    private UUID id;
    private String fullName;
    private String email;
    private String whatsappNumber;
    private String school;
    private String address;
    private String role;
    private String status;
    private String codeNumber;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;
    private Boolean verified;
}
