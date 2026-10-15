package com.amdocs.telecom.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TimeConfigurationTest {

    @Test
    void providesAnInjectableUtcClock() {
        new ApplicationContextRunner().withUserConfiguration(TimeConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
        });
    }

    @Test
    void allowsAFixedClockToReplaceTheDefaultForDeterministicTests() {
        Clock fixed = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
        new ApplicationContextRunner().withBean(Clock.class, () -> fixed)
                .withUserConfiguration(TimeConfiguration.class).run(context -> {
                    assertThat(context).hasSingleBean(Clock.class);
                    assertThat(context.getBean(Clock.class)).isSameAs(fixed);
                });
    }
}
