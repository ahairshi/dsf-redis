import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CacheController {

    private final DataService dataService;

    @Autowired
    public CacheController(DataService dataService) {
        this.dataService = dataService;
    }

    // Endpoint to READ data and observe caching performance
    @GetMapping("/user/{id}")
    public String getData(@PathVariable String id) {
        return dataService.getUserData(id);
    }

    // Endpoint to UPDATE data (simulates another client writing)
    @PostMapping("/user/{id}/invalidate")
    public String invalidate(@PathVariable String id) {
        return dataService.invalidateCache(id);
    }
}
