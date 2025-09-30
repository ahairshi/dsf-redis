import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.ConnectionPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Production-ready Redis connection manager using JedisPooled and UnifiedJedis.
 * Provides thread-safe connection pooling with proper resource management.
 */
public class RedisConnectionManager implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisConnectionManager.class);
    
    private final JedisPooled jedisPooled;
    private final String host;
    private final int port;
    private volatile boolean closed = false;
    
    /**
     * Creates a Redis connection manager with default configuration.
     */
    public RedisConnectionManager(String host, int port) {
        this(host, port, null, createDefaultPoolConfig());
    }
    
    /**
     * Creates a Redis connection manager with authentication.
     */
    public RedisConnectionManager(String host, int port, String password) {
        this(host, port, password, createDefaultPoolConfig());
    }
    
    /**
     * Creates a Redis connection manager with full configuration.
     */
    public RedisConnectionManager(String host, int port, String password, ConnectionPoolConfig poolConfig) {
        this.host = host;
        this.port = port;
        
        JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .password(password)
                .connectionTimeoutMillis(5000)
                .socketTimeoutMillis(5000)
                .ssl(false)
                .build();
        
        this.jedisPooled = new JedisPooled(poolConfig, host, port, clientConfig);
        
        logger.info("Redis connection manager initialized for {}:{}", host, port);
    }
    
    /**
     * Gets the UnifiedJedis instance for Redis operations.
     * This instance is thread-safe and manages connections from the pool.
     */
    public UnifiedJedis getConnection() {
        if (closed) {
            throw new IllegalStateException("Redis connection manager has been closed");
        }
        return jedisPooled;
    }
    
    /**
     * Tests the connection to Redis.
     */
    public boolean testConnection() {
        try {
            String response = jedisPooled.ping();
            logger.debug("Redis PING response: {}", response);
            return "PONG".equals(response);
        } catch (Exception e) {
            logger.error("Redis connection test failed", e);
            return false;
        }
    }
    
    /**
     * Gets connection pool statistics.
     */
    public PoolStats getPoolStats() {
        return new PoolStats(
            jedisPooled.getPool().getNumActive(),
            jedisPooled.getPool().getNumIdle(),
            jedisPooled.getPool().getNumWaiters()
        );
    }
    
    /**
     * Closes all connections and cleans up resources.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        
        synchronized (this) {
            if (closed) {
                return;
            }
            
            try {
                logger.info("Closing Redis connection manager for {}:{}", host, port);
                jedisPooled.close();
                closed = true;
                logger.info("Redis connection manager closed successfully");
            } catch (Exception e) {
                logger.error("Error closing Redis connection manager", e);
                throw new RuntimeException("Failed to close Redis connection manager", e);
            }
        }
    }
    
    /**
     * Creates default connection pool configuration for production use.
     */
    private static ConnectionPoolConfig createDefaultPoolConfig() {
        ConnectionPoolConfig config = new ConnectionPoolConfig();
        
        // Pool sizing
        config.setMaxTotal(50);
        config.setMaxIdle(20);
        config.setMinIdle(5);
        
        // Connection validation
        config.setTestOnBorrow(true);
        config.setTestOnReturn(false);
        config.setTestWhileIdle(true);
        
        // Eviction policy
        config.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
        config.setMinEvictableIdleTime(Duration.ofMinutes(1));
        config.setNumTestsPerEvictionRun(3);
        
        // Blocking behavior
        config.setBlockWhenExhausted(true);
        config.setMaxWait(Duration.ofSeconds(10));
        
        // JMX monitoring
        config.setJmxEnabled(true);
        config.setJmxNamePrefix("redis-pool");
        
        return config;
    }
    
    /**
     * Immutable class representing pool statistics.
     */
    public static class PoolStats {
        private final int activeConnections;
        private final int idleConnections;
        private final int waitingThreads;
        
        public PoolStats(int activeConnections, int idleConnections, int waitingThreads) {
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
            this.waitingThreads = waitingThreads;
        }
        
        public int getActiveConnections() { return activeConnections; }
        public int getIdleConnections() { return idleConnections; }
        public int getWaitingThreads() { return waitingThreads; }
        
        @Override
        public String toString() {
            return String.format("PoolStats{active=%d, idle=%d, waiting=%d}", 
                activeConnections, idleConnections, waitingThreads);
        }
    }
    
    /**
     * Example usage demonstrating connection lifecycle.
     */
    public static void main(String[] args) {
        // Basic usage with try-with-resources
        try (RedisConnectionManager manager = new RedisConnectionManager("localhost", 6379)) {
            
            // Test connection
            if (!manager.testConnection()) {
                logger.error("Failed to connect to Redis");
                return;
            }
            
            // Get UnifiedJedis instance for operations
            UnifiedJedis redis = manager.getConnection();
            
            // Perform Redis operations
            redis.set("key", "value");
            String value = redis.get("key");
            logger.info("Retrieved value: {}", value);
            
            // Check pool stats
            PoolStats stats = manager.getPoolStats();
            logger.info("Pool statistics: {}", stats);
            
        } catch (Exception e) {
            logger.error("Redis operation failed", e);
        }
        // Connection automatically closed via try-with-resources
    }
}