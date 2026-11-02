package ci.assurzen.health;

import ci.assurzen.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("auth")
@RequiredArgsConstructor
public class AuthHealthIndicator implements HealthIndicator {

    private final UserRepository userRepository;

    @Override
    public Health health() {
        try {
            long userCount = userRepository.count();
            return Health.up()
                    .withDetail("userCount", userCount)
                    .withDetail("message", "Authentification operationnelle")
                    .build();
        } catch (RuntimeException e) {
            return Health.down()
                    .withDetail("message", "Base utilisateurs injoignable")
                    .withException(e)
                    .build();
        }
    }
}
