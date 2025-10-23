// DataService.java (Code is essentially the same, but the key prefix matches the config)
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.UnifiedJedis;

@Service
public class DataService {

    private static final String KEY_PREFIX = "cachedemo:user:"; // MUST match the prefix in JedisConfig
    private final UnifiedJedis jedis;
    
    // ... Constructor and other setup (same as before) ...
    @Autowired
    public DataService(UnifiedJedis jedis) {
        this.jedis = jedis;
    }

    // --- READ Operation ---
    public String getUserData(String userId) {
        String key = KEY_PREFIX + userId;
        long startTime = System.nanoTime();
        
        // Jedis automatically checks local cache and falls back to Redis if miss.
        String data = jedis.get(key);
        
        long endTime = System.nanoTime();
        double durationMs = (endTime - startTime) / 1_000_000.0;
        
        String source;

        if (data != null) {
            source = "Cache " + (data.contains("LOCAL") ? "HIT (Local Memory)" : "MISS (Redis Network)");
            
            // This simulation logic is to prove it was a miss/hit
            if (data.contains("MISS")) {
                try { Thread.sleep(300); } catch (InterruptedException e) {} 
                data = data.replace("MISS", "HIT (LOCAL)");
                jedis.set(key, data); 
            }
        } else {
            // COLD MISS: Key not in Redis (or local cache). Simulate DB fetch.
            source = "COLD MISS (DB Simulation)";
            try { Thread.sleep(500); } catch (InterruptedException e) {} 
            data = "User Data for " + userId + " - Fetched at " + System.currentTimeMillis() + " - LOCAL";
            jedis.set(key, data);
        }
        
        return String.format("[%s] Time: %.2f ms | Data: %s", source, durationMs, data);
    }

    // --- WRITE/INVALIDATION Operation ---
    public String invalidateCache(String userId) {
        String key = KEY_PREFIX + userId;
        
        // This SET command automatically triggers the BCAST invalidation message 
        // because the key matches the "cachedemo:user:" prefix.
        String newValue = "User Data UPDATED by External Client at " + System.currentTimeMillis() + " - MISS";
        jedis.set(key, newValue); 
        
        return "Key '" + key + "' SET on Redis. Broadcasting Mode activated invalidation on all clients tracking the prefix.";
    }
}
// Controller.java remains the same.
