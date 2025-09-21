entitlement-service/
├── pom.xml
├── README.md
├── Dockerfile
├── docker-compose.yml
├── .gitignore
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── company/
│   │   │           └── entitlement/
│   │   │               ├── EntitlementServiceApplication.java
│   │   │               ├── config/
│   │   │               │   ├── RedisConfig.java
│   │   │               │   ├── VaultAutoConfiguration.java
│   │   │               │   └── CacheConfiguration.java
│   │   │               ├── controller/
│   │   │               │   └── EntitlementController.java
│   │   │               ├── service/
│   │   │               │   ├── EntitlementService.java
│   │   │               │   ├── EntitlementBackendService.java
│   │   │               │   ├── CacheService.java
│   │   │               │   ├── RedisCacheService.java
│   │   │               │   ├── LocalCacheService.java
│   │   │               │   └── VaultConfigService.java
│   │   │               ├── vault/
│   │   │               │   ├── HashiCorpVaultService.java
│   │   │               │   ├── AwsSecretsManagerService.java
│   │   │               │   ├── AzureKeyVaultService.java
│   │   │               │   └── NoVaultConfigService.java
│   │   │               ├── properties/
│   │   │               │   ├── VaultProperties.java
│   │   │               │   ├── CacheConfigProperties.java
│   │   │               │   ├── RedisConfigProperties.java
│   │   │               │   └── VaultAwareCacheProperties.java
│   │   │               ├── model/
│   │   │               │   ├── EntitlementRequest.java
│   │   │               │   └── EntitlementResponse.java
│   │   │               └── exception/
│   │   │                   └── CacheException.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       ├── application-test.yml
│   │       ├── cache-config.properties
│   │       ├── cache-config-dev.properties
│   │       ├── cache-config-prod.properties
│   │       └── logback-spring.xml
│   └── test/
│       ├── java/
│       │   └── com/
│       │       └── company/
│       │           └── entitlement/
│       │               ├── EntitlementServiceApplicationTests.java
│       │               ├── service/
│       │               │   ├── EntitlementServiceTest.java
│       │               │   ├── RedisCacheServiceTest.java
│       │               │   └── VaultIntegrationTest.java
│       │               ├── vault/
│       │               │   ├── HashiCorpVaultServiceTest.java
│       │               │   └── VaultPerformanceTest.java
│       │               └── controller/
│       │                   └── EntitlementControllerTest.java
│       └── resources/
│           ├── application-test.yml
│           └── test-cache-config.properties
