package com.pablo.aerolinea.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

public class RedisCacheErrorHandler implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheErrorHandler.class);

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Error al leer del caché '{}' (key={}): {}. Se continúa sin caché.",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("Error al escribir en el caché '{}' (key={}): {}. Se continúa sin cachear.",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Error al invalidar el caché '{}' (key={}): {}. La entrada puede haber quedado desactualizada.",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("Error al limpiar el caché '{}': {}.", cache.getName(), exception.getMessage());
    }
}