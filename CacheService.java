package com.company.entitlement.service;

public interface CacheService {
    void put(String key, Object value, int ttlSeconds);
    <T> T get(String key, Class<T> clazz);
    void delete(String key);
    boolean exists(String key);
}
