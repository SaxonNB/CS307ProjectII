package io.sustc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CacheService {

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String RECIPE_PREFIX = "recipe:";
    private static final String RECIPE_SEARCH_PREFIX = "recipe:search:";
    private static final String REVIEW_PREFIX = "review:recipe:";

    private static final long RECIPE_CACHE_TIME = 60;
    private static final long SEARCH_CACHE_TIME = 10;
    private static final long REVIEW_CACHE_TIME = 30;

    public <T> T getRecipe(Long recipeId, Class<T> clazz) {
        String key = RECIPE_PREFIX + recipeId;
        return get(key, clazz);
    }

    public void setRecipe(Long recipeId, Object value) {
        String key = RECIPE_PREFIX + recipeId;
        set(key, value, RECIPE_CACHE_TIME);
    }

    public void deleteRecipe(Long recipeId) {
        String key = RECIPE_PREFIX + recipeId;
        delete(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getSearchResult(String keyword, String category, Double minRating,
                                 Integer page, Integer size, String sort, Class<T> clazz) {
        if (redisTemplate == null) {
            return null;
        }
        String key = buildSearchKey(keyword, category, minRating, page, size, sort);
        try {
            Object value = redisTemplate.opsForValue().get(RECIPE_SEARCH_PREFIX + key);
            if (value == null) {
                return null;
            }
            if (clazz.isInstance(value)) {
                return (T) value;
            }
            return (T) objectMapper.convertValue(value, clazz);
        } catch (Exception e) {
            log.error("Redis getSearchResult error, key: {}", key, e);
            return null;
        }
    }

    public void setSearchResult(String keyword, String category, Double minRating,
                               Integer page, Integer size, String sort, Object value) {
        String key = buildSearchKey(keyword, category, minRating, page, size, sort);
        set(RECIPE_SEARCH_PREFIX + key, value, SEARCH_CACHE_TIME);
    }

    public void deleteAllSearchResults() {
        if (redisTemplate == null) {
            return;
        }
        try {
            Set<String> keys = redisTemplate.keys(RECIPE_SEARCH_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.error("Redis deleteAllSearchResults error", e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getReviews(Long recipeId, Integer page, Integer size, String sort, Class<T> clazz) {
        String key = buildReviewKey(recipeId, page, size, sort);
        return get(REVIEW_PREFIX + key, clazz);
    }

    public void setReviews(Long recipeId, Integer page, Integer size, String sort, Object value) {
        String key = buildReviewKey(recipeId, page, size, sort);
        set(REVIEW_PREFIX + key, value, REVIEW_CACHE_TIME);
    }

    public void deleteReviews(Long recipeId) {
        if (redisTemplate == null) {
            return;
        }
        try {
            Set<String> keys = redisTemplate.keys(REVIEW_PREFIX + recipeId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.error("Redis deleteReviews error, recipeId: {}", recipeId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T get(String key, Class<T> clazz) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            if (clazz.isInstance(value)) {
                return (T) value;
            }
            return objectMapper.convertValue(value, clazz);
        } catch (Exception e) {
            log.error("Redis get error, key: {}", key, e);
            return null;
        }
    }

    private void set(String key, Object value, long timeoutMinutes) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, timeoutMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis set error, key: {}", key, e);
        }
    }

    private void delete(String key) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Redis delete error, key: {}", key, e);
        }
    }

    private String buildSearchKey(String keyword, String category, Double minRating,
                                  Integer page, Integer size, String sort) {
        StringBuilder sb = new StringBuilder();
        sb.append(keyword != null ? keyword : "null");
        sb.append(":");
        sb.append(category != null ? category : "null");
        sb.append(":");
        sb.append(minRating != null ? minRating : "null");
        sb.append(":");
        sb.append(page);
        sb.append(":");
        sb.append(size);
        sb.append(":");
        sb.append(sort != null ? sort : "null");
        return sb.toString();
    }

    private String buildReviewKey(Long recipeId, Integer page, Integer size, String sort) {
        return recipeId + ":" + page + ":" + size + ":" + (sort != null ? sort : "null");
    }
}
