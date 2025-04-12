package top.flyingjack.common.config.anotation;

import org.springframework.context.annotation.Import;
import top.flyingjack.common.config.RedisConfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 导入common的redis配置：
 * - CacheService
 * - RedisTemplate
 * - CacheManager/RedisCacheConfiguration/RedisConnectionFactory
 *
 * @author Zumin Li
 * @date 2025/4/13 23:56
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(RedisConfig.class)
public @interface EnableGlobalCache {
}
