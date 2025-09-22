import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.Jedis;
import java.util.Map;
import java.util.HashMap;

public class RedisConnectionPool {
    
    private final JedisPool jedisPool;
    private final HostAndPort hostAndPort;
    
    /**
     * Constructor with basic configuration
     * 
     * @param host Redis server host
     * @param port Redis server port
     * @param maxTotal Maximum number of connections in the pool
     * @param maxIdle Maximum number of idle connections
     * @param minIdle Minimum number of idle connections
     */
    public RedisConnectionPool(String host, int port, int maxTotal, int maxIdle, int minIdle) {
        this.hostAndPort = new HostAndPort(host, port);
        this.jedisPool = createPool(maxTotal, maxIdle, minIdle, null);
    }
    
    /**
     * Constructor with authentication
     * 
     * @param host Redis server host
     * @param port Redis server port
     * @param password Redis authentication password
     * @param maxTotal Maximum number of connections in the pool
     * @param maxIdle Maximum number of idle connections
     * @param minIdle Minimum number of idle connections
     */
    public RedisConnectionPool(String host, int port, String password, 
                              int maxTotal, int maxIdle, int minIdle) {
        this.hostAndPort = new HostAndPort(host, port);
        this.jedisPool = createPool(maxTotal, maxIdle, minIdle, password);
    }
    
    /**
     * Constructor with default pool settings
     * 
     * @param host Redis server host
     * @param port Redis server port
     */
    public RedisConnectionPool(String host, int port) {
        this(host, port, 20, 10, 5); // Default pool settings
    }
    
    /**
     * Constructor with default pool settings and authentication
     * 
     * @param host Redis server host
     * @param port Redis server port
     * @param password Redis authentication password
     */
    public RedisConnectionPool(String host, int port, String password) {
        this(host, port, password, 20, 10, 5); // Default pool settings
    }
    
    /**
     * Create the actual JedisPool - compatible with older Jedis versions
     */
    private JedisPool createPool(int maxTotal, int maxIdle, int minIdle, String password) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        
        // Create standard JedisPool - compatible with all Jedis versions
        return new JedisPool(poolConfig, hostAndPort.getHost(), 
                           hostAndPort.getPort(), 
                           Protocol.DEFAULT_TIMEOUT, 
                           password, 
                           Protocol.DEFAULT_DATABASE);
    }
    
    /**
     * Get a Jedis resource from the pool
     * 
     * @return Jedis instance from the pool
     */
    public Jedis getResource() {
        return jedisPool.getResource();
    }
    
    /**
     * Store a String key with Map value using Redis Hash
     * 
     * @param key Redis key
     * @param value Map to store as Redis hash
     */
    public void storeMap(String key, Map<String, String> value) {
        try (Jedis jedis = getResource()) {
            jedis.hmset(key, value);  // hmset is more compatible than hset
        }
    }
    
    /**
     * Retrieve a Map value by String key
     * 
     * @param key Redis key
     * @return Map stored at the key, empty map if key doesn't exist
     */
    public Map<String, String> getMap(String key) {
        try (Jedis jedis = getResource()) {
            return jedis.hgetAll(key);
        }
    }
    
    /**
     * Get a specific field from a hash
     * 
     * @param key Redis key
     * @param field Field name in the hash
     * @return Field value or null if not exists
     */
    public String getMapField(String key, String field) {
        try (Jedis jedis = getResource()) {
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
        try (Jedis jedis = getResource()) {
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
     * Test Redis connectivity
     * 
     * @return true if Redis connection works
     */
    public boolean testConnection() {
        try (Jedis jedis = getResource()) {
            String result = jedis.ping();
            return "PONG".equals(result);
        } catch (Exception e) {
            System.err.println("Connection test failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get connection info
     * 
     * @return Simple connection test result
     */
    public String getConnectionInfo() {
        try (Jedis jedis = getResource()) {
            String pong = jedis.ping();
            return "Redis connection successful - " + pong;
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
     * Example usage
     */
    public void exampleUsage() {
        System.out.println("Connected to: " + getHostAndPort());
        
        // Test basic connectivity
        if (testConnection()) {
            System.out.println("Redis connection successful!");
            System.out.println("Connection info: " + getConnectionInfo());
        } else {
            System.out.println("Redis connection failed!");
            return;
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
        
        // Retrieve the entire map
        Map<String, String> retrievedMap = getMap(key);
        System.out.println("Retrieved map: " + retrievedMap);
        
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