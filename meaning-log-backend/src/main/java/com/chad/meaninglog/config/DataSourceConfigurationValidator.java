package com.chad.meaninglog.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DataSourceConfigurationValidator implements InitializingBean {

    private final String password;

    public DataSourceConfigurationValidator(
            @Value("${spring.datasource.password:}") String password
    ) {
        this.password = password;
    }

    @Override
    public void afterPropertiesSet() {
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException("DB_PASSWORD must contain a non-empty datasource credential");
        }
    }
}
