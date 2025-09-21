package com.company.entitlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.company.entitlement.properties.VaultProperties;
import com.company.entitlement.properties.CacheConfigProperties;
import com.company.entitlement.properties.RedisConfigProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    VaultProperties.class,
    CacheConfigProperties.class,
    RedisConfigProperties.class
})
@EnableScheduling
public class EntitlementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EntitlementServiceApplication.class, args);
    }
}
