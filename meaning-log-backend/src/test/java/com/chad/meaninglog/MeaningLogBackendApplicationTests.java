package com.chad.meaninglog;

import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.MeaningLogRepository;
import com.chad.meaninglog.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "jwt.secret=meaning-log-test-jwt-secret-at-least-32-characters")
class MeaningLogBackendApplicationTests {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private MeaningLogRepository meaningLogRepository;

    @Test
    void contextLoads() {
    }

    @Test
    @Transactional
    void mybatisPlusRepositoriesCanPersistAndQueryByUser() {
        UserAccount user = new UserAccount();
        user.setEmail("mybatis-plus-smoke@example.com");
        user.setUsername("mybatisPlusSmoke");
        user.setPasswordHash("test-hash");
        userAccountRepository.save(user);

        MeaningLog log = new MeaningLog();
        log.setUser(user);
        log.setTitle("MyBatis-Plus smoke test");
        log.setContent("Verify insert and query wrapper mapping.");
        log.setLogDate(LocalDate.now());
        meaningLogRepository.save(log);

        assertThat(user.getId()).isNotNull();
        assertThat(log.getId()).isNotNull();
        assertThat(meaningLogRepository.findByIdAndUser(log.getId(), user)).isPresent();
    }
}
