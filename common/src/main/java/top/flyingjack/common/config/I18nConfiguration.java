package top.flyingjack.common.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * 统一国际化messageSource配置
 *
 * @author Zumin Li
 * @date 2025/4/5 13:54
 */
@Configuration
public class I18nConfiguration {
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource =
                new ReloadableResourceBundleMessageSource();
        // 先加载自己class的messages再加载common的
        messageSource.setBasenames("classpath:messages", "classpath:common-messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setCacheSeconds(3600); // 缓存1小时
        return messageSource;
    }
}