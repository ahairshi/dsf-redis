import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.CacheConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.Protocol.RedisProtocol;
import java.util.Arrays;

@Configuration
public class JedisConfig {

    private static final String CACHE_PREFIX = "cachedemo:user:";

    @Bean
    public UnifiedJedis unifiedJedis() {
        HostAndPort endpoint = new HostAndPort("localhost", 6379);

        // 1. Client Configuration (RESP3 is a hard requirement for push messages)
        DefaultJedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .protocol(RedisProtocol.RESP3) 
                .build();

        // 2. Client-Side Cache Configuration with Broadcasting
        CacheConfig cacheConfig = CacheConfig.builder()
                .maxSize(1000) 
                // --- ENABLE BROADCASTING MODE ---
                .broadcast() 
                // --- DEFINE PREFIXES TO TRACK ---
                // Only keys starting with this prefix will trigger invalidations.
                // NOTE: The prefix must end with the delimiter (e.g., ':')
                .prefixes(Arrays.asList(CACHE_PREFIX)) 
                // ---------------------------------
                .build();

        // 3. Create the UnifiedJedis instance
        return new UnifiedJedis(endpoint, clientConfig, cacheConfig);
    }
}
