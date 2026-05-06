# Chapter: RedisConfig - Redis Infrastructure Setup

## Introduction

**RedisConfig** configures Redis as the distributed caching backend for your application. It sets up the RedisTemplate for data operations and the CacheManager for Spring's caching abstractions, enabling both semantic and exact caching across multiple application instances.

## Code

```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        return RedisCacheManager.builder(connectionFactory).build();
    }
}
```

## Key Concepts

### RedisTemplate

**RedisTemplate** provides a high-level abstraction for Redis operations:

```java
RedisTemplate<String, String> template = new RedisTemplate<>();
```

**Generic parameters**:
- `<K, V>` where K is key type, V is value type
- We use `<String, String>` for simplicity
- Could also use `<String, Object>` with JSON serialization

### Serializers

**Why explicit serializers?**

By default, RedisTemplate uses `JdkSerializationRedisSerializer`:
- Stores Java objects in binary format
- Not human-readable in Redis CLI
- Not compatible with non-Java clients
- Includes Java class metadata (bloated)

**StringRedisSerializer**:
- Stores data as UTF-8 strings
- Human-readable in Redis CLI
- Compatible with any Redis client
- Smaller storage footprint

```java
template.setKeySerializer(new StringRedisSerializer());      // Key serialization
template.setValueSerializer(new StringRedisSerializer());    // Value serialization
template.setHashKeySerializer(new StringRedisSerializer());  // Hash field serialization
template.setHashValueSerializer(new StringRedisSerializer()); // Hash value serialization
```

### RedisConnectionFactory

**RedisConnectionFactory** manages the connection pool to Redis:

Spring Boot auto-configures it based on `application.properties`:

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=secret
spring.data.redis.timeout=2000
spring.data.redis.lettuce.pool.max-active=8
spring.data.redis.lettuce.pool.max-idle=8
spring.data.redis.lettuce.pool.min-idle=0
```

**Lettuce** (default connection library):
- Thread-safe, asynchronous client
- Single connection for multiple threads
- Built on Netty (non-blocking I/O)

**Jedis** (alternative):
- Thread-per-connection model
- Simpler, blocking API
- May perform better for synchronous workloads

### CacheManager

**CacheManager** integrates Redis with Spring's `@Cacheable` annotations:

```java
@Bean
public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    return RedisCacheManager.builder(connectionFactory).build();
}
```

Enables declarative caching:

```java
@Cacheable(value = "exactQueryCache", key = "#query")
public String exactCacheGet(String query) {
    return null; // Only called on cache miss
}
```

## Redis Data Structures Used

### Hash (for semantic cache)

```java
redisTemplate.opsForHash().put(
    SEMANTIC_CACHE_PREFIX + "queries",  // Key: "semantic:queries"
    query,                               // Field: query text
    response                             // Value: cached response
);
```

**Redis structure**:
```
semantic:queries (Hash)
  ├─ "How do I reset my password?" → "To reset your password..."
  ├─ "What are your business hours?" → "We're open Monday-Friday..."
  └─ "How can I contact support?" → "You can reach support at..."
```

**Why Hash?**
- Groups related cache entries under one key
- Efficient for storing multiple field-value pairs
- Single TTL applies to entire hash

### String (for exact cache)

```java
@Cacheable(value = "exactQueryCache", key = "#query")
```

**Redis structure**:
```
exactQueryCache::How do I reset my password? → "To reset your password..."
exactQueryCache::What are your business hours? → "We're open Monday-Friday..."
```

**Why String?**
- Spring Cache abstraction uses simple key-value
- Each entry can have independent TTL
- Faster lookup for exact keys

## Configuration Options

### Custom Serializers

**For complex objects**, use JSON serialization:

```java
@Bean
public RedisTemplate<String, Object> jsonRedisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    // JSON serialization for values
    Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer =
        new Jackson2JsonRedisSerializer<>(Object.class);

    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
    objectMapper.activateDefaultTyping(
        LaissezFaireSubTypeValidator.instance,
        ObjectMapper.DefaultTyping.NON_FINAL
    );

    jackson2JsonRedisSerializer.setObjectMapper(objectMapper);

    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(jackson2JsonRedisSerializer);
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setHashValueSerializer(jackson2JsonRedisSerializer);

    return template;
}
```

### Cache Manager with TTL

```java
@Bean
public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofHours(1))              // Default TTL: 1 hour
        .disableCachingNullValues()                 // Don't cache nulls
        .serializeKeysWith(
            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
        )
        .serializeValuesWith(
            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
        );

    // Different TTLs for different caches
    Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
    cacheConfigurations.put("exactQueryCache",
        cacheConfig.entryTtl(Duration.ofMinutes(30)));
    cacheConfigurations.put("userProfileCache",
        cacheConfig.entryTtl(Duration.ofMinutes(5)));

    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(cacheConfig)
        .withInitialCacheConfigurations(cacheConfigurations)
        .build();
}
```

### Connection Pool Tuning

```properties
# Maximum number of connections in pool
spring.data.redis.lettuce.pool.max-active=16

# Maximum idle connections
spring.data.redis.lettuce.pool.max-idle=8

# Minimum idle connections (keep-alive)
spring.data.redis.lettuce.pool.min-idle=2

# Maximum time to wait for connection (ms)
spring.data.redis.lettuce.pool.max-wait=1000

# Connection timeout (ms)
spring.data.redis.timeout=2000
```

**Tuning guidelines**:
- **max-active**: Set to expected concurrent requests × safety factor (2x)
- **max-idle**: Balance between memory and connection creation overhead
- **min-idle**: Keep warm connections for consistent latency
- **timeout**: Fail fast if Redis is unavailable

## Production Configurations

### Redis Sentinel (High Availability)

```java
@Configuration
public class RedisSentinelConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration()
            .master("mymaster")
            .sentinel("sentinel1", 26379)
            .sentinel("sentinel2", 26379)
            .sentinel("sentinel3", 26379);

        return new LettuceConnectionFactory(sentinelConfig);
    }
}
```

### Redis Cluster

```java
@Configuration
public class RedisClusterConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(
            Arrays.asList("node1:6379", "node2:6379", "node3:6379")
        );

        return new LettuceConnectionFactory(clusterConfig);
    }
}
```

### SSL/TLS

```properties
spring.data.redis.ssl.enabled=true
spring.data.redis.ssl.bundle=redis-ssl
```

### Password Authentication

```properties
spring.data.redis.password=${REDIS_PASSWORD}
```

Or in code:

```java
@Bean
public RedisConnectionFactory redisConnectionFactory() {
    RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
    config.setHostName("localhost");
    config.setPort(6379);
    config.setPassword(RedisPassword.of("secret"));

    return new LettuceConnectionFactory(config);
}
```

## Health Checks

Spring Boot Actuator automatically configures Redis health indicator:

```json
GET /actuator/health

{
  "status": "UP",
  "components": {
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.0.5"
      }
    }
  }
}
```

## Operations

### Basic RedisTemplate Operations

```java
// String operations
redisTemplate.opsForValue().set("key", "value");
String value = redisTemplate.opsForValue().get("key");

// With TTL
redisTemplate.opsForValue().set("key", "value", Duration.ofMinutes(10));

// Hash operations
redisTemplate.opsForHash().put("hash-key", "field", "value");
Object value = redisTemplate.opsForHash().get("hash-key", "field");
Map<Object, Object> all = redisTemplate.opsForHash().entries("hash-key");

// List operations
redisTemplate.opsForList().leftPush("list-key", "value");
String value = redisTemplate.opsForList().rightPop("list-key");

// Set operations
redisTemplate.opsForSet().add("set-key", "value1", "value2");
Set<String> members = redisTemplate.opsForSet().members("set-key");

// Sorted Set operations
redisTemplate.opsForZSet().add("zset-key", "value", 1.0);
Set<String> top10 = redisTemplate.opsForZSet().range("zset-key", 0, 9);

// Key operations
redisTemplate.delete("key");
redisTemplate.expire("key", Duration.ofMinutes(10));
Boolean exists = redisTemplate.hasKey("key");
```

## Monitoring

### Redis Metrics (via Actuator)

Enable Lettuce metrics:

```properties
management.metrics.enable.lettuce=true
```

Exposed metrics:
- `lettuce.command.completion.seconds` - Command latency
- `lettuce.command.active.connections` - Active connections
- `lettuce.command.success` - Successful commands
- `lettuce.command.failure` - Failed commands

### Custom Metrics

```java
@Component
public class RedisMetrics {

    private final Counter cacheOps;
    private final Timer cacheLatency;

    public RedisMetrics(MeterRegistry registry) {
        this.cacheOps = Counter.builder("redis.operations.total")
            .tag("operation", "cache")
            .register(registry);

        this.cacheLatency = Timer.builder("redis.operation.latency")
            .register(registry);
    }

    public <T> T time(String operation, Supplier<T> supplier) {
        cacheOps.increment();
        return cacheLatency.record(supplier);
    }
}
```

## Testing

### Embedded Redis (TestContainers)

```java
@SpringBootTest
@Testcontainers
class CachingServiceIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Test
    void shouldCacheInRedis() {
        // Test with real Redis
    }
}
```

### Embedded Redis (In-Memory)

```xml
<dependency>
    <groupId>it.ozimov</groupId>
    <artifactId>embedded-redis</artifactId>
    <version>0.7.3</version>
    <scope>test</scope>
</dependency>
```

```java
@TestConfiguration
public class EmbeddedRedisConfig {

    private RedisServer redisServer;

    @PostConstruct
    public void startRedis() {
        redisServer = new RedisServer(6379);
        redisServer.start();
    }

    @PreDestroy
    public void stopRedis() {
        if (redisServer != null) {
            redisServer.stop();
        }
    }
}
```

## Best Practices

**Use explicit serializers**:
- Avoid JDK serialization (not human-readable)
- Prefer String or JSON serialization
- Consistent serialization across services

**Configure connection pooling**:
- Size pool based on expected concurrency
- Monitor pool exhaustion metrics
- Keep minimum idle connections for stable latency

**Set appropriate TTLs**:
- Prevents unbounded memory growth
- Balances freshness with cache efficiency
- Different TTLs for different cache types

**Use Redis Cluster for scale**:
- Horizontal scaling for large datasets
- Automatic sharding across nodes
- High availability with replication

**Monitor Redis health**:
- Use Spring Actuator health checks
- Alert on connection failures
- Track memory usage and eviction rate

## Key Takeaways

- **RedisConfig sets up Redis infrastructure** for distributed caching in Spring Boot
- **RedisTemplate provides high-level operations** for all Redis data structures
- **Explicit serializers make data human-readable** and compatible with non-Java clients
- **CacheManager integrates with Spring's @Cacheable** for declarative caching
- **Connection pooling is essential** for performance and resource management
- **Redis supports high availability** through Sentinel and Cluster configurations

## Next Steps

Learn how **TokenOptimizer** reduces LLM costs by intelligently selecting context and compressing prompts.

---

**Next Chapter**: [07 - TokenOptimizer: Cost Optimization Through Token Management](./07-token-optimizer.md)
