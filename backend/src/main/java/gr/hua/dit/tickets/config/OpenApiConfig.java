package gr.hua.dit.tickets.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Adds an "Authorize" button (HTTP Basic) to Swagger UI. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ticketsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tickets API")
                        .version("v1")
                        .description("""
                                Demo users (password: `password`):
                                * `alice`, `bob` — role **USER**: create / edit / delete own OPEN tickets
                                * `agent` — role **AGENT**: see all tickets, assign and resolve them"""))
                .components(new Components().addSecuritySchemes("basicAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }
}
