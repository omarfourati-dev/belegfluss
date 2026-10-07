package de.omarfourati.belegfluss.auth;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: at most 5 failed logins per account and client IP, and
 * at most 20 per IP across all accounts, within 15 minutes. In-memory is enough for a
 * single instance; several instances would need a shared store (e.g. Redis).
 */
@Component
public class LoginRateLimiter {

    static final int MAX_PER_ACCOUNT = 5;
    static final int MAX_PER_IP = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Throws if this client must wait before trying again. */
    public void checkAllowed(String ip, String email) {
        Instant now = clock.instant();
        long account = recent(accountKey(ip, email), now);
        long perIp = recent(ipKey(ip), now);
        if (account >= MAX_PER_ACCOUNT || perIp >= MAX_PER_IP) {
            throw new TooManyLoginAttemptsException(WINDOW);
        }
    }

    public void recordFailure(String ip, String email) {
        Instant now = clock.instant();
        add(accountKey(ip, email), now);
        add(ipKey(ip), now);
    }

    public void recordSuccess(String ip, String email) {
        failures.remove(accountKey(ip, email));
    }

    private long recent(String key, Instant now) {
        Deque<Instant> times = failures.get(key);
        if (times == null) {
            return 0;
        }
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(WINDOW))) {
                times.pollFirst();
            }
            return times.size();
        }
    }

    private void add(String key, Instant now) {
        Deque<Instant> times = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(now);
        }
    }

    private static String accountKey(String ip, String email) {
        return "a|" + ip + "|" + (email == null ? "" : email.strip().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String ip) {
        return "i|" + ip;
    }
}
