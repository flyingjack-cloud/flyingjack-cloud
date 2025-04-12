package top.flyingjack.common.error.exception;

import org.springframework.security.authentication.InsufficientAuthenticationException;

/**
 *
 *
 * @author Zumin Li
 * @date 2025/4/15 0:14
 */
public class CaptchaAuthenticationException extends InsufficientAuthenticationException {
    public CaptchaAuthenticationException(String msg) {
        super(msg);
    }
}
