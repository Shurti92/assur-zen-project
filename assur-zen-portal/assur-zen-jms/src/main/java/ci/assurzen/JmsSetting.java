package ci.assurzen;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class JmsSetting {

    /*@Value("${spring.activemq.broker-url}")
    private String brokerUrl;

    @Value("${spring.activemq.username}")
    private String username;

    @Value("${spring.activemq.password}")
    private String password;*/

    /*
     * ConnectionFactory ActiveMQ
     */
    /*@Bean
    public ActiveMQConnectionFactory connectionFactory() {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory();
        connectionFactory.setBrokerURL(brokerUrl);
        connectionFactory.setUserName(username);
        connectionFactory.setPassword(password);

        // Redelivery Policy
        RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
        redeliveryPolicy.setMaximumRedeliveries(5);
        redeliveryPolicy.setInitialRedeliveryDelay(2000);   // 2s
        redeliveryPolicy.setBackOffMultiplier(2.0);
        redeliveryPolicy.setUseExponentialBackOff(true);
        redeliveryPolicy.setMaximumRedeliveryDelay(60000);  // 1 min max
        connectionFactory.setRedeliveryPolicy(redeliveryPolicy);

        return connectionFactory;
    }*/

    /*
     * Sérialisation JSON via TextMessage
     */
    /*@Bean
    public MessageConverter jacksonJmsMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        return converter;
    }*/

    /*
     * Factory pour la réception des messages (listeners)
     */
    /*@Bean
    public JmsListenerContainerFactory<?> jsaFactory(
            ConnectionFactory connectionFactory,
            DefaultJmsListenerContainerFactoryConfigurer configurer) {

        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setMessageConverter(jacksonJmsMessageConverter());

        // ACK manuel pour contrôler la redélivrance
        factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE);

        // Pas de transaction (gestion manuelle)
        factory.setSessionTransacted(false);

        // Parallélisme contrôlé
        factory.setConcurrency("1-20");

        // Gestion des erreurs
        factory.setErrorHandler(t ->
                log.error("Erreur JMS : {}", t.getMessage(), t));

        configurer.configure(factory, connectionFactory);

        log.info("JMS Listener Factory configurée en CLIENT_ACKNOWLEDGE");

        return factory;
    }*/

    /*
     * Template pour l'envoi de messages
     */
    /*@Bean
    public JmsTemplate jmsTemplate() {
        JmsTemplate template = new JmsTemplate();
        template.setMessageConverter(jacksonJmsMessageConverter());
        template.setConnectionFactory(connectionFactory());
        template.setPubSubDomain(false); // false = queue, pas topic
        template.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE);
        return template;
    }*/
}