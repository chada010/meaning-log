package com.chad.meaninglog.config;

import com.chad.meaninglog.time.BusinessTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 统一提供可在测试中替换的业务 Clock。
 *
 * @author wwj
 */
@Configuration
public class BusinessTimeConfig {

    @Bean
    public Clock businessClock() {
        return Clock.system(BusinessTime.ZONE_ID);
    }
}
