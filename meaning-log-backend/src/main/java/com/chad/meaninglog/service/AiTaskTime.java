package com.chad.meaninglog.service;

import java.time.LocalDateTime;
import java.time.ZoneId;

public final class AiTaskTime {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private AiTaskTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }
}
