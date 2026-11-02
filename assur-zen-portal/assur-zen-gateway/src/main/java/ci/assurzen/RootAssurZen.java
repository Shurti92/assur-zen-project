package ci.assurzen;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;


@SpringBootApplication(scanBasePackages = "ci.assurzen")
// ci.assurzen.sycapay.repository/entity ajoutes explicitement (integration SycaPay, 2026-09-22) :
// basePackages ici ne suit PAS scanBasePackages ci-dessus (portee independante propre a Spring Data
// JPA / EntityScan) - sans cet ajout, SycaPayTransactionRepository/SycaPayTransaction restent
// invisibles a Spring/Hibernate malgre @SpringBootApplication(scanBasePackages = "ci.assurzen") qui,
// lui, couvre bien la detection des @Service/@Component (erreur reelle constatee au demarrage :
// bean SycaPayTransactionRepository introuvable). Meme chose pour ci.assurzen.webpush (2026-09-26).
@EnableJpaRepositories(basePackages = {"ci.assurzen.repositories", "ci.assurzen.sycapay.repository", "ci.assurzen.webpush.repository"})
@EntityScan(basePackages = {"ci.assurzen.entities", "ci.assurzen.sycapay.entity", "ci.assurzen.webpush.entity"})
@EnableTransactionManagement
@EnableScheduling
@Import({ JmsSetting.class })
@Slf4j
public class RootAssurZen extends SpringBootServletInitializer  {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(RootAssurZen.class);
    }

    public static void main(String[] args) {

        SpringApplication.run(RootAssurZen.class, args);

        log.info(
            "\n" +
            "\n+-------------------------------------------------------------------------------------------------+" +
            "\n|      AssurZen GATEWAY :::::: AU NOM DE DIEU, LE TOUT MISERICORDIEUX, LE TRES MISERICORDIEUX     |" +
            "\n+-------------------------------------------------------------------------------------------------+" +
            "\n"
        );
    }
}
