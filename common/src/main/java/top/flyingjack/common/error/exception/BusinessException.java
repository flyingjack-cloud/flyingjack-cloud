package top.flyingjack.common.error.exception;

import top.flyingjack.common.error.ErrorCode;

/**
 * 业务类错误
 *
 * @author Zumin Li
 * @date 2025/4/13 0:34
 */
public class BusinessException extends RuntimeException{
    // 国际化翻译标识符
    private ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String langCode() {
        return this.errorCode.getId();
    }

    public int status() {
        return this.errorCode.getCode();
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
