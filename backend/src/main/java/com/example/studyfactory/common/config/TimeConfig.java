package com.example.studyfactory.common.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** Every branch operates on the Korean calendar, wherever the JVM happens to run. */
    public static final ZoneId STUDY_FACTORY_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * Pinned to the business zone rather than the JVM default, so that
     * {@code LocalDate.now(clock)} means "today in Korea" on every host.
     *
     * The JVM default zone is deliberately left alone: Spring Data auditing
     * writes {@code created_at}/{@code updated_at} through it, and rows already
     * stored would be reinterpreted nine hours out if that zone moved.
     */
    @Bean
    public Clock clock() {
        return Clock.system(STUDY_FACTORY_ZONE);
    }
}
