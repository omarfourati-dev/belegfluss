package de.omarfourati.belegfluss.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final LoginRateLimiter limiter = new LoginRateLimiter(clock);

    @Test
    void blocksAnAccountAfterFiveFailures() {
        fail(5, "1.2.3.4", "a@b.de");

        assertThatThrownBy(() -> limiter.checkAllowed("1.2.3.4", "A@b.de"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        assertThatCode(() -> limiter.checkAllowed("5.6.7.8", "a@b.de")).doesNotThrowAnyException();
    }

    @Test
    void blocksAnIpThatTriesManyAccounts() {
        for (int i = 0; i < LoginRateLimiter.MAX_PER_IP; i++) {
            limiter.recordFailure("9.9.9.9", "user" + i + "@b.de");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("9.9.9.9", "fresh@b.de"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void failuresExpireAfterTheWindow() {
        fail(5, "1.2.3.4", "a@b.de");
        clock.advance(LoginRateLimiter.WINDOW.plusSeconds(1));

        assertThatCode(() -> limiter.checkAllowed("1.2.3.4", "a@b.de")).doesNotThrowAnyException();
    }

    @Test
    void successfulLoginResetsTheAccountCounter() {
        fail(4, "1.2.3.4", "a@b.de");
        limiter.recordSuccess("1.2.3.4", "a@b.de");
        fail(4, "1.2.3.4", "a@b.de");

        assertThatCode(() -> limiter.checkAllowed("1.2.3.4", "a@b.de")).doesNotThrowAnyException();
    }

    private void fail(int times, String ip, String email) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(ip, email);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-07T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
