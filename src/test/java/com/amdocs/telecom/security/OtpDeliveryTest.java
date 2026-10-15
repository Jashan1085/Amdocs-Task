package com.amdocs.telecom.security;

import com.amdocs.telecom.config.AuthenticationConfiguration;
import com.amdocs.telecom.controller.DevOtpController;
import com.amdocs.telecom.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class OtpDeliveryTest {
    @Test void demoInboxIsBoundToSessionAndExpires() {
        MutableClock clock = new MutableClock();
        DevOtpDelivery delivery = new DevOtpDelivery(clock);
        UUID id = UUID.randomUUID();
        delivery.deliver(id, "session-secret", "123456", clock.instant().plusSeconds(300));
        assertThat(delivery.preview(id, "another-session")).isEmpty();
        assertThat(delivery.preview(id, "session-secret")).contains("123456");
        clock.advance(Duration.ofMinutes(5));
        assertThat(delivery.preview(id, "session-secret")).isEmpty();
    }
    @Test void consumedDemoMessagesAreRemoved() {
        MutableClock clock = new MutableClock();
        DevOtpDelivery delivery = new DevOtpDelivery(clock);
        UUID id = UUID.randomUUID();
        delivery.deliver(id, "session-secret", "123456", clock.instant().plusSeconds(300));
        delivery.forget(id);
        assertThat(delivery.preview(id, "session-secret")).isEmpty();
    }
    @Test void withoutDevProfileThereIsNoInboxAndDeliveryFailsClosed() {
        new ApplicationContextRunner().withUserConfiguration(AuthenticationConfiguration.class, DevOtpController.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DevOtpController.class);
                    assertThat(context.getBean(OtpDelivery.class).isDemo()).isFalse();
                    assertThatThrownBy(() -> context.getBean(OtpDelivery.class)
                            .deliver(UUID.randomUUID(), "binding", "123456", new MutableClock().instant()))
                            .isInstanceOf(OtpDeliveryUnavailableException.class);
                });
    }
    @Test void passwordPolicyRejectsTruncationAndShortPasswords() {
        assertThat(PasswordPolicy.accepts("short")).isFalse();
        assertThat(PasswordPolicy.accepts("LongEnough-Password12")).isTrue();
        assertThat(PasswordPolicy.accepts(repeat("a", 72))).isTrue();
        assertThat(PasswordPolicy.accepts(repeat("a", 73))).isFalse();
        assertThat(PasswordPolicy.accepts(repeat("\u20ac", 25))).isFalse();
    }
    private String repeat(String value, int count) { return new String(new char[count]).replace("\0", value); }
}
