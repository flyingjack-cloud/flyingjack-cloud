package top.flyingjack.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.flyingjack.common.error.exception.*;
import top.flyingjack.common.tool.MessageTool;

/**
 * 全局异常处理
 *
 * @author Zumin Li
 * @date 2025/4/11 15:11
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    // 处理认证异常（如未登录）
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorRes> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authenticated failed from security: {}", ex.getMessage());

        ErrorCode errorCode;
        if (ex instanceof BadCredentialsException || ex instanceof UsernameNotFoundException ){
            errorCode = ex instanceof AttemptAuthenticationException ? ErrorCode.TO_MANY_LOGIN_ATTEMPT : ErrorCode.BAD_CREDENTIAL;
        } else if (ex instanceof LockedException || ex instanceof DisabledException || ex instanceof AccountExpiredException ){
            errorCode = ErrorCode.INVALID_ACCOUNT;
        } else if (ex instanceof CredentialsExpiredException){
            errorCode = ErrorCode.EXPIRED_CREDENTIAL;
        } else if (ex instanceof AuthenticationServiceException){
            errorCode = ErrorCode.AUTHENTICATE_FAILED;
        } else if (ex instanceof InsufficientAuthenticationException ){
            errorCode = ex instanceof CaptchaAuthenticationException ? ErrorCode.NEED_CAPTCHA : ErrorCode.INSUFFICIENT;
        } else {
            errorCode = ErrorCode.UNAUTHORIZED;
        }
        return buildResponse(errorCode, request);
    }

    // 处理权限异常（如权限不足）
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorRes> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Accessed denied from security: {}", ex.getMessage());
        return buildResponse(ErrorCode.ACCESS_DENIED, request);
    }

    // 参数类异常
    @ExceptionHandler({IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorRes> handleSystemException(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return buildResponse(ErrorCode.INVALID_PARAM, request);
    }
    // 系统类错误
    @ExceptionHandler(ServiceInternalException.class)
    public ResponseEntity<ErrorRes> handleSystemException(
            ServiceInternalException ex, HttpServletRequest request) {
        log.warn("Internal system error : {}", ex.getMessage());

        return buildResponse(ErrorCode.SYSTEM_ERROR, request);
    }

    // 系统类错误
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorRes> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        log.warn("Business error: {}", ex.getMessage());
        return buildResponse(ex.errorCode(), request);
    }

    // 处理所有未捕获异常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorRes> handleUnknownException(
            Exception ex, HttpServletRequest request) {
        log.error("System error: ", ex);
        return buildResponse(ErrorCode.SYSTEM_ERROR, request);
    }

    private ResponseEntity<ErrorRes> buildResponse(ErrorCode errorCode, HttpServletRequest request) {
        ErrorRes error = new ErrorRes(
                errorCode.getCode(),
                MessageTool.getMessageByContext(this.messageSource, errorCode.getId()),
                request.getRequestURI(),
                System.currentTimeMillis()
        );
        return ResponseEntity.status(errorCode.getCode()).body(error);
    }
}
