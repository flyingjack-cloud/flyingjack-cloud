package top.flyingjack.common.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.resource.ClientResources;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import top.flyingjack.common.cache.CacheService;
import top.flyingjack.common.cache.RedisCacheServiceImpl;

import java.time.Duration;

/**
 * Redis操作模块
 *
 * @author Zumin Li
 * @date 2025/4/12 15:07
 */
@Configuration
@EnableCaching // 扫描项目缓存相关注解
public class RedisConfig {
    @Bean
    public CacheService cacheService(RedisTemplate<String, Object> redisTemplate){
        return new RedisCacheServiceImpl(redisTemplate);
    }

    // Redis的直接操作，不通过spring cache
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Jackson2来序列化和反序列化redis的value值
        GenericJackson2JsonRedisSerializer jackson2JsonRedisSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        // string序列化key
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setHashKeySerializer(stringSerializer);
        template.setKeySerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }

    // 配置spring使用RedisCacheManager - @Cacheable等spring操作会使用
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory,
                                     RedisCacheConfiguration redisCacheConfiguration) {
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .transactionAware()
                .build();
    }

    @Bean
    public RedisCacheConfiguration cacheConfiguration(
            @Value("${spring.data.redis.default-expire:1800}") int expiredSeconds
    ){
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(expiredSeconds))  // 默认缓存半小时
                .disableCachingNullValues()     // 不缓存null值
                // 下面两行设置spring也是用string和jackson进行cache序列化
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }

    // 配置redis连接客户端
    @Bean
    public RedisConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database,
            @Value("${spring.data.redis.timeout:5000}") long timeout
    ) {
        // 配置redis连接信息
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(host);
        config.setPort(port);
        config.setPassword(password);
        config.setDatabase(database);

        // 配置连接客户端lettuce
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(timeout))
                .clientOptions(ClientOptions.builder()
                        .autoReconnect(true)
                        .pingBeforeActivateConnection(true)
                        .build())
                .clientResources(ClientResources.builder()
                        .ioThreadPoolSize(4)
                        .computationThreadPoolSize(4)
                        .build())
                .build();
        return new LettuceConnectionFactory(config, clientConfig);
    }
}
