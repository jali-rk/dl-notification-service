package dopaminelite.notifications.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * User information provided by BFF.
 * Contains essential user details to avoid additional service calls.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDto {
    
    @NotNull(message = "User ID is required")
    private UUID id;
    
    @NotNull(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
}
