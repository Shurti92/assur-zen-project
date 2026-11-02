package ci.assurzen.swaggerconfig;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SwaggerProperties.class)
@ComponentScan(basePackages = {"ci.assurzen.controller"})
public class SwaggerConfig {

    @Bean
    public OpenAPI assurZenOpenAPI(
            @Value("${swagger.name}")
            String applicationName,
            SwaggerProperties swaggerProperties
    ) {

        return new OpenAPI()
                .info(new Info()
                        .title(applicationName + " API")
                        .description("""
                                API Gateway AssurZen.\n
                                Documentation officielle et centralisée de l'API Gateway AssurZen.\n
                                Cette API centralise les services destinés aux applications mobiles,
                                partenaires et microservices de la plateforme Assurance
                                """)
                        .version(swaggerProperties.getVersion())
                        .contact(new Contact()
                                .name("AssurZen Assurances Support")
                                .email("support@assurzen.ci"))
                )
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                );
    }
}
