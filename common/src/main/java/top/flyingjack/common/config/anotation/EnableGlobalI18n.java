package top.flyingjack.common.config.anotation;

import org.springframework.context.annotation.Import;
import top.flyingjack.common.config.I18nConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 开启全局i18n处理，默认先读取自己的message，在读取common包下的message
 *
 * @author Zumin Li
 * @date 2025/4/13 23:47
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(I18nConfiguration.class)
public @interface EnableGlobalI18n {
}
