package top.flyingjack.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import top.flyingjack.common.error.GlobalExceptionHandler;

/**
 * 全局异常处理配置器 - 搭配@EnableGlobalException使用
 *
 * @author Zumin Li
 * @date 2025/4/11 15:17
 */
@Configuration
@ConditionalOnClass(GlobalExceptionHandler.class)
@Import(GlobalExceptionHandler.class)  // 确保全局异常处理器被加载
public class ExceptionHandlerAutoConfiguration {
}
