package top.flyingjack.common.error.exception;

import org.springframework.security.authentication.BadCredentialsException;

/**
 * 登录失败次数过多时抛出的异常
 *
 * @author Zumin Li
 * @date 2025/4/14 23:59
 */
public class AttemptAuthenticationException extends BadCredentialsException {
    // 还需等待时间
    private final long waitRemainSeconds;

    public AttemptAuthenticationException(String msg, long waitRemainSeconds) {
        super(msg);
        this.waitRemainSeconds = waitRemainSeconds;
    }

    public long getWaitRemainSeconds() {
        return waitRemainSeconds;
    }
}
