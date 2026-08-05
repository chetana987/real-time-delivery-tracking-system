package com.deliverytracking.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Token-bucket rate limiter backed by a single Redis hash per key.
 *
 * <p>The bucket holds up to {@code capacity} tokens and is refilled at
 * {@code refillPerSecond} tokens/second. Each request must consume one token;
 * the whole check-and-consume runs atomically inside one Lua script, so the
 * logic is safe even when many requests arrive concurrently (and across
 * multiple application instances sharing the same Redis).
 *
 * <p>If Redis is unreachable the limiter fails open (allows the request) so a
 * cache/Redis outage never blocks the core business flow.
 */
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private static final String TOKEN_BUCKET_SCRIPT = """
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])

            local data = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
            local current = tonumber(data[1])
            local ts = tonumber(data[2])
            local now = tonumber(redis.call('TIME')[1])

            if current == nil then
                current = capacity
                ts = now
            end

            current = math.min(capacity, current + (now - ts) * rate)

            local allowed = 0
            if current >= 1 then
                current = current - 1
                allowed = 1
            end

            redis.call('HSET', KEYS[1], 'tokens', current, 'ts', now)
            redis.call('EXPIRE', KEYS[1], 60)
            return allowed
            """;

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> tokenBucketScript =
            new DefaultRedisScript<>(TOKEN_BUCKET_SCRIPT, Long.class);

    /**
     * @param key              unique bucket key, e.g. "ratelimit:location:42"
     * @param capacity         max tokens the bucket can hold (max burst size)
     * @param refillPerSecond  tokens added to the bucket every second
     * @return true if a token was consumed (request allowed), false otherwise
     */
    public boolean tryAcquire(String key, int capacity, double refillPerSecond) {
        try {
            Long result = stringRedisTemplate.execute(
                    tokenBucketScript,
                    List.of(key),
                    String.valueOf(capacity),
                    String.valueOf(refillPerSecond));
            return result != null && result == 1L;
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable for key {}, failing open", key, e);
            return true;
        }
    }
}
