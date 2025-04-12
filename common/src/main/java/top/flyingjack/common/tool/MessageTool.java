package top.flyingjack.common.tool;


import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

public class MessageTool {
    private final static Logger logger = LoggerFactory.getLogger(MessageTool.class);

    public static Locale DEFAULT_MESSAGE_LOCALE = Locale.CHINA;

    /**
     * 指定Locale获取message
     *
     */
    public static String getMessageByLocale(MessageSource messageSource, String code, Locale locale) {
        String result;
        try {
            result = messageSource.getMessage(code, null, locale);
        } catch (NoSuchMessageException e) {
            logger.trace("Cannot found i18 message, return directly");
            return code;
        }
        return result;
    }

    /**
     * 根据LocaleContextHolder上下文的Locale获取message
     *
     */
    public static String getMessageByContext(MessageSource messageSource, String code) {
        Locale locale = LocaleContextHolder.getLocale();
        return getMessageByLocale(messageSource, code, locale);
    }

    /**
     * 根据默认Locale获取message
     *
     */
    public static String getMessageByDefault(MessageSource messageSource, String code) {
        return getMessageByLocale(messageSource, code, DEFAULT_MESSAGE_LOCALE);
    }

    /**
     * 根据Request Accept-Language头获取message
     *
     */
    public static String getMessageByRequest(MessageSource messageSource, String code, HttpServletRequest request) {
        String localeHeader = request.getHeader("Accept-Language");
        try {
            Locale locale = new Locale.Builder().setLanguageTag(localeHeader.replace('_', '-')).build();
            return getMessageByLocale(messageSource, code, locale);
        } catch (Exception e) {
            logger.trace("Accept-Language format is illegal");
            return getMessageByDefault(messageSource, code);
        }
    }
}
