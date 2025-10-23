import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.Protocol.RedisProtocol;
import redis.clients.jedis.CacheConfig;

// ...

HostAndPort endpoint = new HostAndPort("localhost", 6379);

// 1. Configure the Jedis client to use RESP3
DefaultJedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
        .protocol(RedisProtocol.RESP3) // Essential for client-side caching
        .build();

// 2. Configure the local client cache
CacheConfig cacheConfig = CacheConfig.builder()
        .maxSize(1000) // Maximum number of entries in the local cache
        // You can add more configurations here if available in your Jedis version, 
        // such as TTL or eviction policy, though the core feature relies on invalidation.
        .build();

// 3. Create the UnifiedJedis client with both configurations
UnifiedJedis client = new UnifiedJedis(endpoint, clientConfig, cacheConfig);

// Now, client-side caching is enabled on this 'client' instance.
// The first 'GET' will fetch from Redis and cache locally.
// Subsequent 'GET's will check the local cache first.
// If another client modifies a key this client has read, Redis will send an 
// invalidation message, and the client will remove the key from its local cache.
