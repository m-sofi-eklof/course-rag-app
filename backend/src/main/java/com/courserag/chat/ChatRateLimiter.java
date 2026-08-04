package com.courserag.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter for the chat endpoint.
 *
 * Keyed by client IP. Each call to isAllowed() records a timestamp and
 * returns false if more than maxRequests timestamps fall within the last
 * windowSeconds. No external dependency — pure java.util.concurrent.
 *
 * Why per-IP rather than per-course: a single course can be queried from
 * many sessions, so per-course limits would be too coarse and unfair across
 * users. Per-IP is the right boundary for runaway-cost protection.
 */
@Component
public class ChatRateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    public ChatRateLimiter(
            @Value("${chat.rate-limit.max-requests-per-window:5}") int maxRequests,
            @Value("${chat.rate-limit.window-seconds:60}") int windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1_000L;
    }

    /**
     * Returns true if the request is within the allowed rate, false if it should be rejected.
     * Thread-safe: ConcurrentHashMap.compute is atomic per key.
     */
    public boolean isAllowed(String clientIp) {
        long now = System.currentTimeMillis();
        long cutoff = now - windowMillis;
        int[] count = {0};

        windows.compute(clientIp, (key, deque) -> {
            if (deque == null) deque = new ArrayDeque<>();
            // Evict timestamps outside the window
            while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
                deque.pollFirst();
            }
            deque.addLast(now);
            count[0] = deque.size();
            return deque;
        });

        return count[0] <= maxRequests;
    }
}
