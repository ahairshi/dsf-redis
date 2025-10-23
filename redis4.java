import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.UnifiedJedis;

@Service
public class DataService {

    private static final String KEY_PREFIX = "cachedemo:user:";
    private final UnifiedJedis jedis;

    @Autowired
    public DataService(UnifiedJedis jedis) {
        this.jedis = jedis;
    }

    // --- READ Operation ---
    public String getUserData(String userId) {
        String key = KEY_PREFIX + userId;
        long startTime = System.nanoTime();

        // 1. STANDARD GET CALL
        // This is where Jedis internally decides: Local Cache Hit (FAST) or Redis Network Call (SLOW)
        String data = jedis.get(key);
        
        long endTime = System.nanoTime();
        double durationMs = (endTime - startTime) / 1_000_000.0;
        
        String source;

        if (data != null) {
            source = "Cache " + (data.contains("LOCAL") ? "HIT (Local Memory)" : "MISS (Redis Network)");
            
            // If it was a miss, simulate slow DB fetch and SET the data
            if (data.contains("MISS")) {
                // Simulate slow DB read only when cache misses
                try { Thread.sleep(300); } catch (InterruptedException e) {} 
                data = data.replace("MISS", "HIT (LOCAL)");
                // Update Redis (and thus the local cache for next time)
                jedis.set(key, data); 
            }
        } else {
            // This is a cold start cache miss (key doesn't exist in Redis or local cache)
            source = "COLD MISS (DB Simulation)";
            
            // Simulate slow DB read
            try { Thread.sleep(500); } catch (InterruptedException e) {} 
            
            data = "User Data for " + userId + " - Fetched at " + System.currentTimeMillis() + " - LOCAL";
            // Store in Redis (next GET will be a local cache hit)
            jedis.set(key, data);
        }
        
        return String.format("[%s] Time: %.2f ms | Data: %s", source, durationMs, data);
    }

    // --- WRITE/INVALIDATION Operation ---
    public String invalidateCache(String userId) {
        String key = KEY_PREFIX + userId;
        
        // Simulating an update from another process, which triggers IN-MEMORY INVALIDATION
        String newValue = "User Data UPDATED at " + System.currentTimeMillis() + " - MISS";
        jedis.set(key, newValue); 
        
        // This SET operation on Redis will cause Redis to send an invalidation push 
        // to all clients (including this one) that were tracking the key.
        
        return "Key '" + key + "' SET on Redis. Local cache on all clients should be invalidated.";
    }
}
