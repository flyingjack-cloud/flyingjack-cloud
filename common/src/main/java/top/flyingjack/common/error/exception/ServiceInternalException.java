package top.flyingjack.common.error.exception;

/**
 * 系统类错误
 *
 * @author Zumin Li
 * @date 2025/4/13 0:34
 */
public class ServiceInternalException extends RuntimeException{
    public ServiceInternalException(String message) {
        super(message);
    }
}
