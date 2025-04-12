package top.flyingjack.common.config.anotation;

import org.springframework.context.annotation.Import;
import top.flyingjack.common.config.ExceptionHandlerAutoConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用全局异常处理
 * 使用这个注解就会将ExceptionHandlerAutoConfiguration注册到spring上下文
 *
 * @author Zumin Li
 * @date 2025/4/11 16:20
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(ExceptionHandlerAutoConfiguration.class)
public @interface EnableGlobalException {
}