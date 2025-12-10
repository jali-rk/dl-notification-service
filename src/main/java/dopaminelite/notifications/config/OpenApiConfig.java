package dopaminelite.notifications.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI configuration.
 * Access UI at /swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Notifications Service API")
                        .description("Microservice for managing and delivering notifications across multiple channels")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("DopamineLite Team")
                                .email("dev@dopaminelite.com")));
    }
}
