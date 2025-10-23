import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.CacheConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.Protocol.RedisProtocol;

@Configuration
public class JedisConfig {

    // IMPORTANT: Ensure your Redis is running on default port 6379 
    // and supports RESP3 (Redis 6.0+).

    @Bean
    public UnifiedJedis unifiedJedis() {
        // 1. Connection Endpoint
        HostAndPort endpoint = new HostAndPort("localhost", 6379);

        // 2. Client Configuration (MUST use RESP3 for client-side caching/tracking)
        DefaultJedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .protocol(RedisProtocol.RESP3) // Crucial for client-side tracking/invalidation
                .build();

        // 3. Client-Side Cache Configuration
        CacheConfig cacheConfig = CacheConfig.builder()
                .maxSize(1000) // Max 1000 entries in the local cache
                .build();

        // 4. Create the UnifiedJedis instance with client-side caching enabled
        return new UnifiedJedis(endpoint, clientConfig, cacheConfig);
    }
}
