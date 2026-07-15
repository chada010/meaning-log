package com.chad.meaninglog.time;

import java.time.ZoneId;

/**
 * Meaning Log 单一业务时区入口。
 *
 * @author wwj
 */
public final class BusinessTime {

    public static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private BusinessTime() {
    }
}
