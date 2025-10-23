import redis.clients.jedis.CacheConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Protocol.RedisProtocol;
import redis.clients.jedis.params.ClientTrackingParams;

// --- Class Scope or Singleton Initialization ---

private static final String REDIS_HOST = "localhost";
private static final int REDIS_PORT = 6379;
private static final String CACHE_PREFIX = "my-app-data:";

// The JedisPooled instance, which is thread-safe and manages connections.
private final JedisPooled client; 

public YourApplicationClass() {
    // 1. Configure the connection (must use RESP3)
    DefaultJedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
            .protocol(RedisProtocol.RESP3) 
            .build();

    // 2. Configure the client-side cache (Broadcasting mode recommended)
    CacheConfig cacheConfig = CacheConfig.builder()
            .maxSize(5000) // Set your desired local cache limit
            // Enable Broadcasting and define prefixes to track
            .clientTrackingParams(ClientTrackingParams.clientTrackingParams().bcast().prefix(CACHE_PREFIX)) 
            .build();
            
    // 3. Initialize the JedisPooled instance
    // JedisPooled combines connection pooling, RESP3, and client-side caching
    this.client = new JedisPooled(
        new HostAndPort(REDIS_HOST, REDIS_PORT), 
        clientConfig, 
        cacheConfig
    );
}
