package com.company.entitlement.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EntitlementResponse {
    private Map<String, Map<String, Object>> allEntitlement;
    private Map<String, Map<String, Object>> npnxEntitlement;
    private Map<String, Map<String, Object>> asdsEntitlement;
    private List<String> roles;
    private Integer someNumber;
}

// src/main/java/com/company/entitlement/exception/CacheException.java
package com.company.entitlement.exception;

public class CacheException extends RuntimeException {
    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public CacheException(String message) {
        super(message);
    }
}
