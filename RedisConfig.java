// Configuration Class
@Configuration
@EnableConfigurationProperties(RedisConfigProperties.class)
public class RedisConfig {
    
    @Autowired
    private RedisConfigProperties redisProperties;
    
    @Bean
    @ConditionalOnProperty(name = "cache.redis.enabled", havingValue = "true")
    public JedisPool jedisPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(redisProperties.getPool().getMaxActive());
        poolConfig.setMaxIdle(redisProperties.getPool().getMaxIdle());
        poolConfig.setMinIdle(redisProperties.getPool().getMinIdle());
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMaxWaitMillis(redisProperties.getPool().getMaxWait());
        
        return new JedisPool(poolConfig, 
            redisProperties.getHost(), 
            redisProperties.getPort(), 
            redisProperties.getTimeout(),
            redisProperties.getPassword());
    }
    
    @Bean
    @ConditionalOnProperty(name = "cache.redis.enabled", havingValue = "false", matchIfMissing = true)
    public Map<String, Object> localCache() {
        return new ConcurrentHashMap<>();
    }
}

// Properties Configuration
@ConfigurationProperties(prefix = "cache.redis")
@Data
public class RedisConfigProperties {
    private boolean enabled = false;
    private String host = "localhost";
    private int port = 6379;
    private String password;
    private int timeout = 2000;
    private int retryCount = 3;
    private Pool pool = new Pool();
    
    @Data
    public static class Pool {
        private int maxActive = 20;
        private int maxIdle = 10;
        private int minIdle = 5;
        private long maxWait = 3000;
    }
}

// Entitlement Data Models
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EntitlementResponse {
    private Map<String, Map<String, Object>> allEntitlement;
    private Map<String, Map<String, Object>> npnxEntitlement;
    private Map<String, Map<String, Object>> asdsEntitlement;
    private List<String> roles;
    private Integer someNumber;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EntitlementRequest {
    private String username;
    private String ibd;
    private String productCode;
}

// Cache Service Interface
public interface CacheService {
    void put(String key, Object value, int ttlSeconds);
    <T> T get(String key, Class<T> clazz);
    void delete(String key);
    boolean exists(String key);
}

// Redis Cache Implementation
@Service
@ConditionalOnProperty(name = "cache.redis.enabled", havingValue = "true")
@Slf4j
public class RedisCacheService implements CacheService {
    
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final RedisConfigProperties redisProperties;
    
    public RedisCacheService(JedisPool jedisPool, RedisConfigProperties redisProperties) {
        this.jedisPool = jedisPool;
        this.redisProperties = redisProperties;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    @Override
    public void put(String key, Object value, int ttlSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            String jsonValue = objectMapper.writeValueAsString(value);
            jedis.setex(key, ttlSeconds, jsonValue);
            log.debug("Cached value for key: {}", key);
        } catch (Exception e) {
            log.error("Error caching value for key: {}", key, e);
        }
    }
    
    @Override
    public <T> T get(String key, Class<T> clazz) {
        int retryCount = 0;
        while (retryCount < redisProperties.getRetryCount()) {
            try (Jedis jedis = jedisPool.getResource()) {
                String jsonValue = jedis.get(key);
                if (jsonValue != null) {
                    T value = objectMapper.readValue(jsonValue, clazz);
                    log.debug("Retrieved cached value for key: {}", key);
                    return value;
                }
                return null;
            } catch (JedisConnectionException | SocketTimeoutException e) {
                retryCount++;
                log.warn("Redis connection timeout/error for key: {}, retry: {}/{}", 
                    key, retryCount, redisProperties.getRetryCount(), e);
                
                if (retryCount >= redisProperties.getRetryCount()) {
                    log.error("Max retries exceeded for Redis get operation, key: {}", key);
                    throw new CacheException("Redis operation failed after retries", e);
                }
                
                try {
                    Thread.sleep(100 * retryCount); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CacheException("Thread interrupted during retry", ie);
                }
            } catch (Exception e) {
                log.error("Error retrieving cached value for key: {}", key, e);
                return null;
            }
        }
        return null;
    }
    
    @Override
    public void delete(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
            log.debug("Deleted cached value for key: {}", key);
        } catch (Exception e) {
            log.error("Error deleting cached value for key: {}", key, e);
        }
    }
    
    @Override
    public boolean exists(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.exists(key);
        } catch (Exception e) {
            log.error("Error checking existence for key: {}", key, e);
            return false;
        }
    }
}

// Local Cache Implementation (fallback)
@Service
@ConditionalOnProperty(name = "cache.redis.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class LocalCacheService implements CacheService {
    
    private final Map<String, CacheEntry> localCache;
    
    public LocalCacheService(@Qualifier("localCache") Map<String, Object> localCache) {
        this.localCache = (Map<String, CacheEntry>) localCache;
    }
    
    @Override
    public void put(String key, Object value, int ttlSeconds) {
        long expirationTime = System.currentTimeMillis() + (ttlSeconds * 1000L);
        localCache.put(key, new CacheEntry(value, expirationTime));
        log.debug("Cached value locally for key: {}", key);
    }
    
    @Override
    public <T> T get(String key, Class<T> clazz) {
        CacheEntry entry = localCache.get(key);
        if (entry != null && !entry.isExpired()) {
            log.debug("Retrieved locally cached value for key: {}", key);
            return clazz.cast(entry.getValue());
        } else if (entry != null && entry.isExpired()) {
            localCache.remove(key);
        }
        return null;
    }
    
    @Override
    public void delete(String key) {
        localCache.remove(key);
        log.debug("Deleted locally cached value for key: {}", key);
    }
    
    @Override
    public boolean exists(String key) {
        CacheEntry entry = localCache.get(key);
        return entry != null && !entry.isExpired();
    }
    
    @Data
    @AllArgsConstructor
    private static class CacheEntry {
        private Object value;
        private long expirationTime;
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
}

// Custom Exception
public class CacheException extends RuntimeException {
    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Backend Service Interface
public interface EntitlementBackendService {
    EntitlementResponse fetchEntitlements(EntitlementRequest request);
}

// Mock Backend Service Implementation
@Service
@Slf4j
public class EntitlementBackendServiceImpl implements EntitlementBackendService {
    
    @Override
    public EntitlementResponse fetchEntitlements(EntitlementRequest request) {
        log.info("Fetching entitlements from backend for user: {}, IBD: {}, product: {}", 
            request.getUsername(), request.getIbd(), request.getProductCode());
        
        // Simulate backend call
        try {
            Thread.sleep(100); // Simulate network delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Mock response
        EntitlementResponse response = new EntitlementResponse();
        response.setAllEntitlement(createMockEntitlement("ALL"));
        response.setNpnxEntitlement(createMockEntitlement("NPNX"));
        response.setAsdsEntitlement(createMockEntitlement("ASDS"));
        response.setRoles(Arrays.asList("USER", "ADMIN", "VIEWER"));
        response.setSomeNumber(42);
        
        return response;
    }
    
    private Map<String, Map<String, Object>> createMockEntitlement(String type) {
        Map<String, Map<String, Object>> entitlement = new HashMap<>();
        Map<String, Object> permissions = new HashMap<>();
        permissions.put("read", true);
        permissions.put("write", type.equals("ALL"));
        permissions.put("delete", type.equals("ALL"));
        entitlement.put("permissions", permissions);
        return entitlement;
    }
}

// Main Service
@Service
@Slf4j
public class EntitlementService {
    
    private final CacheService cacheService;
    private final EntitlementBackendService backendService;
    private final RedisConfigProperties redisProperties;
    
    private static final int CACHE_TTL_SECONDS = 3600; // 1 hour
    
    public EntitlementService(CacheService cacheService, 
                            EntitlementBackendService backendService,
                            RedisConfigProperties redisProperties) {
        this.cacheService = cacheService;
        this.backendService = backendService;
        this.redisProperties = redisProperties;
    }
    
    public EntitlementResponse getEntitlements(String username, String ibd, String productCode) {
        String cacheKey = buildCacheKey(username, ibd, productCode);
        boolean fetchFromBackend = false;
        
        log.info("Getting entitlements for user: {}, IBD: {}, product: {}", username, ibd, productCode);
        
        // Check cache first if Redis is enabled
        if (redisProperties.isEnabled()) {
            try {
                EntitlementResponse cachedResponse = cacheService.get(cacheKey, EntitlementResponse.class);
                if (cachedResponse != null) {
                    log.info("Entitlements found in cache for key: {}", cacheKey);
                    return cachedResponse;
                }
                log.debug("No cached entitlements found for key: {}", cacheKey);
            } catch (CacheException e) {
                log.error("Cache operation failed, falling back to backend", e);
                fetchFromBackend = true;
            }
        }
        
        // Fetch from backend
        EntitlementRequest request = new EntitlementRequest(username, ibd, productCode);
        EntitlementResponse response = backendService.fetchEntitlements(request);
        
        // Cache the response if Redis is enabled and fetch was successful
        if (redisProperties.isEnabled() && response != null) {
            try {
                cacheService.put(cacheKey, response, CACHE_TTL_SECONDS);
                log.info("Entitlements cached for key: {}", cacheKey);
            } catch (Exception e) {
                log.error("Failed to cache entitlements for key: {}", cacheKey, e);
                // Continue execution even if caching fails
            }
        }
        
        return response;
    }
    
    public void invalidateCache(String username, String ibd, String productCode) {
        String cacheKey = buildCacheKey(username, ibd, productCode);
        cacheService.delete(cacheKey);
        log.info("Invalidated cache for key: {}", cacheKey);
    }
    
    private String buildCacheKey(String username, String ibd, String productCode) {
        return String.format("entitlement:%s:%s:%s", username, ibd, productCode);
    }
}

// Controller
@RestController
@RequestMapping("/api/entitlements")
@Slf4j
public class EntitlementController {
    
    private final EntitlementService entitlementService;
    
    public EntitlementController(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }
    
    @GetMapping
    public ResponseEntity<EntitlementResponse> getEntitlements(
            @RequestParam String username,
            @RequestParam String ibd,
            @RequestParam String productCode) {
        
        try {
            EntitlementResponse response = entitlementService.getEntitlements(username, ibd, productCode);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting entitlements", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/cache")
    public ResponseEntity<Void> invalidateCache(
            @RequestParam String username,
            @RequestParam String ibd,
            @RequestParam String productCode) {
        
        entitlementService.invalidateCache(username, ibd, productCode);
        return ResponseEntity.ok().build();
    }
}
