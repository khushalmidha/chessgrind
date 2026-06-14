package com.mateforge.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisCacheService {
    private final Optional<StringRedisTemplate> redis;
    private final ObjectMapper mapper;

    public RedisCacheService(ObjectProvider<StringRedisTemplate> redis, ObjectMapper mapper) {
        this.redis = Optional.ofNullable(redis.getIfAvailable());
        this.mapper = mapper;
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        return read(key).flatMap(value -> deserialize(value, type));
    }

    public <T> Optional<T> get(String key, TypeReference<T> type) {
        return read(key).flatMap(value -> deserialize(value, type));
    }

    public void put(String key, Object value, Duration ttl) {
        redis.ifPresent(template -> {
            try {
                template.opsForValue().set(key, mapper.writeValueAsString(value), ttl);
            } catch (RedisConnectionFailureException ignored) {
            } catch (Exception ignored) {
            }
        });
    }

    public void evict(String key) {
        redis.ifPresent(template -> {
            try {
                template.delete(key);
            } catch (RedisConnectionFailureException ignored) {
            }
        });
    }

    public String key(String prefix, String value) {
        return prefix + ":" + sha256(value);
    }

    private Optional<String> read(String key) {
        if (redis.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(redis.get().opsForValue().get(key));
        } catch (RedisConnectionFailureException ignored) {
            return Optional.empty();
        }
    }

    private <T> Optional<T> deserialize(String value, Class<T> type) {
        try {
            return Optional.of(mapper.readValue(value, type));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private <T> Optional<T> deserialize(String value, TypeReference<T> type) {
        try {
            return Optional.of(mapper.readValue(value, type));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
