package ci.assurzen.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    @Bean(name = "auditTaskExecutor")
    public TaskExecutor auditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-flux-");
        executor.setRejectedExecutionHandler((r, exec) ->
                log.warn("Audit task queue saturated, discarding audit write"));
        executor.initialize();
        return executor;
    }

    @Bean(name = "campagneTaskExecutor")
    public TaskExecutor campagneTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("campagne-envoi-");
        executor.setRejectedExecutionHandler((r, exec) ->
                log.warn("Campagne task queue saturee, lancement de campagne rejete"));
        executor.initialize();
        return executor;
    }

    @Bean(name = "satisfactionImportTaskExecutor")
    public TaskExecutor satisfactionImportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("satisfaction-import-");
        executor.setRejectedExecutionHandler((r, exec) ->
                log.warn("Satisfaction import task queue saturee, import rejete"));
        executor.initialize();
        return executor;
    }

    @Bean(name = "telegramTaskExecutor")
    public TaskExecutor telegramTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("telegram-notif-");
        executor.setRejectedExecutionHandler((r, exec) ->
                log.warn("Telegram task queue saturee, notification abandonnee"));
        executor.initialize();
        return executor;
    }

    /** Paiements automatiques de commissions partenaire (2026-09-23) : appels HTTP SycaPay, jamais sur
     * le thread de la requete qui signe le contrat. Un seul virement a la fois suffit (volume faible) ;
     * une file saturee n'est jamais bloquante : la commission reste simplement payable manuellement. */
    @Bean(name = "commissionPaiementTaskExecutor")
    public TaskExecutor commissionPaiementTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("commission-auto-");
        executor.setRejectedExecutionHandler((r, exec) ->
                log.error("File des paiements automatiques de commission saturee - traitement abandonne, commission a payer manuellement"));
        executor.initialize();
        return executor;
    }

    /** Notifications Web Push de l'application EP (2026-09-26) : appels HTTP vers les services push des
     * navigateurs, hors du thread metier. File saturee : push abandonne (la notification reste dans
     * l'application, pushStatus reste PENDING). */
    @Bean(name = "webPushTaskExecutor")
    public TaskExecutor webPushTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("web-push-");
        executor.setRejectedExecutionHandler((r, exec) ->
                log.warn("File des notifications push saturee - push abandonne"));
        executor.initialize();
        return executor;
    }
}
