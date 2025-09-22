import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import java.util.Map;
import java.util.HashMap;

public class RedisRESP3Pool {
    
    private final JedisPool jedisPool;
    private final HostAndPort hostAndPort;
    
    /**
     * Constructor with basic configuration and RESP3
     * 
     * @param host Redis server host
     * @param port Redis server port
     * @param maxTotal Maximum number of connections in the pool
     * @param maxIdle Maximum number of idle connections
     * @param minIdle Minimum number of idle connections
     */
    public RedisRESP3Pool(String host, int port, int maxTotal, int maxIdle, int minIdle) {
        this.hostAndPort = new HostAndPort(host, port);
        this.jedisPool = createPool(maxTotal, maxIdle, minIdle, null);
    }
    
    /**
     * Constructor with authentication and RESP3
     * 
     * @param host Redis server host
     * @param port Redis server port
     * @param password Redis authentication password
     * @param maxTotal Maximum number of connections in the pool
     * @param maxIdle Maximum number of idle connections
     * @param minIdle Minimum number of idle connections
     */
    public RedisRESP3Pool(String host, int port, String password, 
                         int maxTotal, int maxIdle, int minIdle) {
        this.hostAndPort = new HostAndPort(host, port);
        this.jedisPool = createPool(maxTotal, maxIdle, minIdle, password);
    }
    
    /**
     * Constructor with default pool settings and RESP3
     * 
     * @param host Redis server host
     * @param port Redis server port
     */
    public RedisRESP3Pool(String host, int port) {
        this(host, port, 20, 10, 5); // Default pool settings
    }
    
    /**
     * Constructor with default pool settings, authentication and RESP3
     * 
     * @param host Redis server host
     * @param port Redis server port
     * @param password Redis authentication password
     */
    public RedisRESP3Pool(String host, int port, String password) {
        this(host, port, password, 20, 10, 5); // Default pool settings
    }
    
    /**
     * Private method to create the actual JedisPool with RESP3
     */
    private JedisPool createPool(int maxTotal, int maxIdle, int minIdle, String password) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        
        // Build client configuration with RESP3 for client-side caching
        DefaultJedisClientConfig.Builder configBuilder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(Protocol.DEFAULT_TIMEOUT)
                .socketTimeoutMillis(Protocol.DEFAULT_TIMEOUT)
                .database(Protocol.DEFAULT_DATABASE);
        
        // Add password if provided
        if (password != null && !password.trim().isEmpty()) {
            configBuilder.password(password);
        }
        
        // Enable RESP3 protocol for client-side caching support
        JedisClientConfig clientConfig = configBuilder
                .protocol(Protocol.RESP3)  // This enables RESP3
                .build();
        
        return new JedisPool(poolConfig, hostAndPort, clientConfig);
    }
    
    /**
     * Get a Jedis resource from the pool with RESP3 enabled
     * 
     * @return Jedis instance from the pool with RESP3
     */
    public Jedis getResource() {
        return jedisPool.getResource();
    }
    
    /**
     * Enable client-side caching for the connection
     * This method sets up client-side caching using RESP3 features
     * 
     * @param jedis Jedis connection
     * @return true if client caching was enabled successfully
     */
    public boolean enableClientSideCaching(Jedis jedis) {
        try {
            // Enable client tracking for client-side caching
            // This uses RESP3's CLIENT TRACKING command
            String response = jedis.clientTracking(true, null, null, false, false, false, false);
            return "OK".equals(response);
        } catch (Exception e) {
            System.err.println("Failed to enable client-side caching: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get a Jedis resource with client-side caching enabled
     * 
     * @return Jedis instance with client-side caching enabled
     */
    public Jedis getResourceWithCaching() {
        Jedis jedis = getResource();
        enableClientSideCaching(jedis);
        return jedis;
    }
    
    /**
     * Store a String key with Map value
     * 
     * @param key Redis key
     * @param value Map to store as Redis hash
     */
    public void storeMap(String key, Map<String, String> value) {
        try (Jedis jedis = getResource()) {
            jedis.hset(key, value);
        }
    }
    
    /**
     * Retrieve a Map value by String key (uses client-side caching if enabled)
     * 
     * @param key Redis key
     * @return Map stored at the key, empty map if key doesn't exist
     */
    public Map<String, String> getMap(String key) {
        try (Jedis jedis = getResourceWithCaching()) {
            return jedis.hgetAll(key);
        }
    }
    
    /**
     * Get a specific field from a hash (uses client-side caching if enabled)
     * 
     * @param key Redis key
     * @param field Field name in the hash
     * @return Field value or null if not exists
     */
    public String getMapField(String key, String field) {
        try (Jedis jedis = getResourceWithCaching()) {
            return jedis.hget(key, field);
        }
    }
    
    /**
     * Set a specific field in a hash
     * 
     * @param key Redis key
     * @param field Field name
     * @param value Field value
     */
    public void setMapField(String key, String field, String value) {
        try (Jedis jedis = getResource()) {
            jedis.hset(key, field, value);
        }
    }
    
    /**
     * Check if a field exists in a hash
     * 
     * @param key Redis key
     * @param field Field name
     * @return true if field exists, false otherwise
     */
    public boolean hasMapField(String key, String field) {
        try (Jedis jedis = getResourceWithCaching()) {
            return jedis.hexists(key, field);
        }
    }
    
    /**
     * Delete a key
     * 
     * @param key Redis key to delete
     * @return Number of keys deleted (0 or 1)
     */
    public long deleteKey(String key) {
        try (Jedis jedis = getResource()) {
            return jedis.del(key);
        }
    }
    
    /**
     * Get connection info and verify RESP3
     * 
     * @return Redis HELLO response showing protocol version
     */
    public Map<String, Object> getConnectionInfo() {
        try (Jedis jedis = getResource()) {
            // HELLO command is available in RESP3 and shows protocol info
            return jedis.hello();
        }
    }
    
    /**
     * Test RESP3 functionality
     * 
     * @return true if RESP3 is working properly
     */
    public boolean testRESP3() {
        try (Jedis jedis = getResource()) {
            Map<String, Object> hello = jedis.hello();
            Object version = hello.get("proto");
            return version != null && version.equals(3L);
        } catch (Exception e) {
            System.err.println("RESP3 test failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Close the connection pool
     */
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
    
    /**
     * Get the configured host and port
     * 
     * @return HostAndPort object
     */
    public HostAndPort getHostAndPort() {
        return hostAndPort;
    }
    
    /**
     * Example usage with RESP3 and client-side caching
     */
    public void exampleUsage() {
        System.out.println("Connected to: " + getHostAndPort());
        
        // Test RESP3
        if (testRESP3()) {
            System.out.println("RESP3 protocol is active!");
            System.out.println("Connection info: " + getConnectionInfo());
        } else {
            System.out.println("RESP3 protocol test failed - check Redis and Jedis versions");
        }
        
        // Working with String key and Map value
        String key = "user:1001";
        Map<String, String> userMap = new HashMap<>();
        userMap.put("name", "John Doe");
        userMap.put("email", "john.doe@example.com");
        userMap.put("age", "30");
        userMap.put("city", "New York");
        
        // Store the map
        storeMap(key, userMap);
        
        // Retrieve the entire map (will use client-side caching)
        Map<String, String> retrievedMap = getMap(key);
        System.out.println("Retrieved map: " + retrievedMap);
        
        // Second retrieval should hit client cache if enabled
        Map<String, String> cachedMap = getMap(key);
        System.out.println("Second retrieval (cached): " + cachedMap);
        
        // Work with individual fields
        String name = getMapField(key, "name");
        System.out.println("Name: " + name);
        
        // Update a field
        setMapField(key, "age", "31");
        
        // Check field existence
        boolean hasEmail = hasMapField(key, "email");
        System.out.println("Has email: " + hasEmail);
    }
}