import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

public class RoleService {
    
    private static final Logger LOGGER = Logger.getLogger(RoleService.class.getName());
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String ROLE_KEY_PREFIX = "user:roles:";
    private static final int CACHE_EXPIRATION_HOURS = 24; // Cache for 24 hours
    
    private final RedisRESP3Pool redisPool;
    private final RoleRepository roleRepository;
    
    public RoleService(RedisRESP3Pool redisPool, RoleRepository roleRepository) {
        this.redisPool = redisPool;
        this.roleRepository = roleRepository;
    }
    
    /**
     * Get user roles with Redis cache and database fallback
     * 
     * @param userId User ID to fetch roles for
     * @return Map of role data (role_id -> role_name, permissions, etc.)
     */
    public Map<String, String> getUserRoles(String userId) {
        String cacheKey = ROLE_KEY_PREFIX + userId;
        
        // Step 1: Try to get from Redis cache with retries
        Map<String, String> roles = getRolesFromCache(cacheKey);
        
        if (roles != null && !roles.isEmpty()) {
            LOGGER.info("Roles found in cache for user: " + userId);
            return roles;
        }
        
        // Step 2: Cache miss - fetch from database
        LOGGER.info("Cache miss for user: " + userId + ". Fetching from database.");
        roles = getRolesFromDatabase(userId);
        
        // Step 3: Store in cache for future requests
        if (roles != null && !roles.isEmpty()) {
            storeRolesInCache(cacheKey, roles);
            LOGGER.info("Roles cached for user: " + userId);
        }
        
        return roles != null ? roles : new HashMap<>();
    }
    
    /**
     * Attempt to get roles from Redis cache with retry logic
     * 
     * @param cacheKey Redis cache key
     * @return Map of roles or null if not found/error
     */
    private Map<String, String> getRolesFromCache(String cacheKey) {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                Map<String, String> roles = redisPool.getMap(cacheKey);
                
                if (roles != null && !roles.isEmpty()) {
                    // Check if cache entry is still valid (not expired)
                    String cachedAt = roles.get("_cached_at");
                    if (cachedAt != null && isCacheValid(cachedAt)) {
                        // Remove metadata before returning
                        roles.remove("_cached_at");
                        return roles;
                    } else {
                        LOGGER.info("Cache entry expired for key: " + cacheKey);
                        // Remove expired entry
                        redisPool.deleteKey(cacheKey);
                        return null;
                    }
                }
                
                // Empty result means cache miss, no need to retry
                return null;
                
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, 
                    String.format("Attempt %d/%d failed to fetch roles from cache for key: %s", 
                    attempt, MAX_RETRY_ATTEMPTS, cacheKey), e);
                
                if (attempt == MAX_RETRY_ATTEMPTS) {
                    LOGGER.log(Level.SEVERE, "All cache attempts failed for key: " + cacheKey, e);
                    return null;
                }
                
                // Wait before retry (exponential backoff)
                try {
                    Thread.sleep(100 * attempt); // 100ms, 200ms, 300ms
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }
    
    /**
     * Fetch roles from database
     * 
     * @param userId User ID
     * @return Map of roles
     */
    private Map<String, String> getRolesFromDatabase(String userId) {
        try {
            return roleRepository.findRolesByUserId(userId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch roles from database for user: " + userId, e);
            return new HashMap<>();
        }
    }
    
    /**
     * Store roles in Redis cache with expiration metadata
     * 
     * @param cacheKey Redis cache key
     * @param roles Map of roles to cache
     */
    private void storeRolesInCache(String cacheKey, Map<String, String> roles) {
        try {
            // Add cache timestamp for expiration checking
            Map<String, String> cacheData = new HashMap<>(roles);
            cacheData.put("_cached_at", String.valueOf(System.currentTimeMillis()));
            
            redisPool.storeMap(cacheKey, cacheData);
            LOGGER.info("Successfully cached roles for key: " + cacheKey);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to cache roles for key: " + cacheKey, e);
            // Cache failure shouldn't break the flow, just log it
        }
    }
    
    /**
     * Check if cache entry is still valid based on timestamp
     * 
     * @param cachedAtStr Timestamp when data was cached
     * @return true if cache is still valid
     */
    private boolean isCacheValid(String cachedAtStr) {
        try {
            long cachedAt = Long.parseLong(cachedAtStr);
            long expirationTime = TimeUnit.HOURS.toMillis(CACHE_EXPIRATION_HOURS);
            return (System.currentTimeMillis() - cachedAt) < expirationTime;
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid cache timestamp format: " + cachedAtStr);
            return false;
        }
    }
    
    /**
     * Invalidate user roles cache (useful after role updates)
     * 
     * @param userId User ID whose cache should be cleared
     * @return true if cache was cleared successfully
     */
    public boolean invalidateUserRolesCache(String userId) {
        String cacheKey = ROLE_KEY_PREFIX + userId;
        try {
            long deleted = redisPool.deleteKey(cacheKey);
            LOGGER.info("Cache invalidated for user: " + userId + " (deleted: " + deleted + ")");
            return deleted > 0;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to invalidate cache for user: " + userId, e);
            return false;
        }
    }
    
    /**
     * Check if roles exist in cache (without fetching them)
     * 
     * @param userId User ID to check
     * @return true if roles are cached and valid
     */
    public boolean areRolesCached(String userId) {
        String cacheKey = ROLE_KEY_PREFIX + userId;
        try {
            return redisPool.hasMapField(cacheKey, "_cached_at");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to check cache status for user: " + userId, e);
            return false;
        }
    }
    
    /**
     * Get cache statistics for monitoring
     * 
     * @return Map containing cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            String connectionInfo = redisPool.getConnectionInfo();
            stats.put("redis_connection", "OK");
            stats.put("protocol_info", connectionInfo);
            stats.put("max_retry_attempts", MAX_RETRY_ATTEMPTS);
            stats.put("cache_expiration_hours", CACHE_EXPIRATION_HOURS);
        } catch (Exception e) {
            stats.put("redis_connection", "ERROR: " + e.getMessage());
        }
        return stats;
    }
}

/**
 * Interface for role repository (database layer)
 */
interface RoleRepository {
    /**
     * Find roles by user ID from database
     * 
     * @param userId User ID
     * @return Map of roles (role_id -> role_details)
     */
    Map<String, String> findRolesByUserId(String userId);
}

/**
 * Example implementation of RoleRepository
 */
class DatabaseRoleRepository implements RoleRepository {
    
    @Override
    public Map<String, String> findRolesByUserId(String userId) {
        // Simulate database call
        Map<String, String> roles = new HashMap<>();
        
        // Example role data
        if ("user123".equals(userId)) {
            roles.put("role_1", "ADMIN");
            roles.put("role_2", "USER_MANAGER");
            roles.put("permissions", "READ,WRITE,DELETE,MANAGE_USERS");
            roles.put("department", "IT");
        } else if ("user456".equals(userId)) {
            roles.put("role_1", "USER");
            roles.put("permissions", "READ,WRITE");
            roles.put("department", "SALES");
        }
        
        return roles;
    }
}

/**
 * Example usage class
 */
class RoleServiceExample {
    public static void main(String[] args) {
        // Initialize Redis pool
        RedisRESP3Pool redisPool = new RedisRESP3Pool("localhost", 6379);
        
        // Initialize repository
        RoleRepository repository = new DatabaseRoleRepository();
        
        // Create role service
        RoleService roleService = new RoleService(redisPool, repository);
        
        try {
            // Get roles for user (will fetch from DB first time)
            Map<String, String> roles1 = roleService.getUserRoles("user123");
            System.out.println("First call - Roles: " + roles1);
            
            // Get roles again (should come from cache)
            Map<String, String> roles2 = roleService.getUserRoles("user123");
            System.out.println("Second call - Roles: " + roles2);
            
            // Check cache status
            boolean cached = roleService.areRolesCached("user123");
            System.out.println("Are roles cached: " + cached);
            
            // Invalidate cache
            roleService.invalidateUserRolesCache("user123");
            
            // Get cache statistics
            Map<String, Object> stats = roleService.getCacheStats();
            System.out.println("Cache stats: " + stats);
            
        } finally {
            redisPool.close();
        }
    }
}