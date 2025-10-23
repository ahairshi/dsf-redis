import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.Protocol.RedisProtocol;
import redis.clients.jedis.CacheConfig;

// ... configuration from before
HostAndPort endpoint = new HostAndPort("localhost", 6379);
DefaultJedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
        .protocol(RedisProtocol.RESP3)
        .build();
CacheConfig cacheConfig = CacheConfig.builder()
        .maxSize(1000)
        .build();
UnifiedJedis client = new UnifiedJedis(endpoint, clientConfig, cacheConfig);

// --- Application Logic ---
public void demonstrateCaching() {
    String key = "user:profile:101";
    String initialValue = "data-version-1";

    // 1. Initial SET operation (Write-through to Redis)
    client.set(key, initialValue);
    System.out.println("Step 1: SET key to " + initialValue);

    // 2. First GET operation (Cache Miss)
    // -> Jedis sends GET to Redis server.
    // -> Redis server sends response and starts tracking this key for this client.
    // -> Jedis client stores the value locally in its in-memory cache.
    String value1 = client.get(key); 
    System.out.println("Step 2: First GET. Fetched from Redis. Value: " + value1); 
    // This is a "slow" read (network latency involved).

    // 3. Second GET operation (Cache Hit)
    // -> Jedis checks local cache first.
    // -> Finds the value locally.
    // -> Returns the value directly from the local memory.
    String value2 = client.get(key); 
    System.out.println("Step 3: Second GET. Fetched from Local Cache. Value: " + value2);
    // This is a "fast" read (no network latency involved).
    
    // --- (Imagine another client or process modifies the key) ---
    // If a server-side SET happens:
    // client.set(key, "data-version-2"); 
    // Redis sends an INVALIDATION MESSAGE to this client.
    // Jedis client automatically removes "user:profile:101" from its local cache.

    // 4. Third GET operation after invalidation (Cache Miss again)
    // -> Jedis checks local cache (key is gone).
    // -> Jedis sends GET to Redis server for the new value.
    // -> New value is returned and cached locally.
    String value3 = client.get(key);
    System.out.println("Step 4: Third GET after invalidation. Fetched from Redis. Value: " + value3);
    
}
