package dopaminelite.notifications.client;

import lombok.Data;

/**
 * DTO for user data from User Service.
 */
@Data
public class UserDto {
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
}
