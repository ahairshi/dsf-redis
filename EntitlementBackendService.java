package com.company.entitlement.service;

import com.company.entitlement.model.EntitlementRequest;
import com.company.entitlement.model.EntitlementResponse;

public interface EntitlementBackendService {
    EntitlementResponse fetchEntitlements(EntitlementRequest request);
}

// src/main/java/com/company/entitlement/vault/VaultConfigService.java
package com.company.entitlement.vault;

import java.util.Map;

public interface VaultConfigService {
    String getSecret(String path, String key);
    Map<String, String> getAllSecrets(String path);
    void refreshSecrets();
    boolean isVaultEnabled();
}
