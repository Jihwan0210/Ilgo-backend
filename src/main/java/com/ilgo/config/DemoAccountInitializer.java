package com.ilgo.config;

import com.ilgo.user.User;
import com.ilgo.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 포트폴리오 시연용 계정을 기동 시 한 번 만들어 둔다. 이미 있으면 건드리지 않는다.
 * 비활성화: app.demo.enabled=false
 */
@Component
public class DemoAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String email;
    private final String password;

    public DemoAccountInitializer(UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  @Value("${app.demo.enabled:true}") boolean enabled,
                                  @Value("${app.demo.email:demo@ilgo.app}") String email,
                                  @Value("${app.demo.password:ilgo1234}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(new User(email, passwordEncoder.encode(password)));
        log.info("시연용 계정 생성: {}", email);
    }
}
