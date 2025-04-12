package top.flyingjack.common.error;

/**
 * 常用的错误类型
 *
 * @author Zumin Li
 * @date 2025/4/3 13:32
 */
public enum ErrorCode {
    // 系统错误: 系统本身错误或者bug
    SYSTEM_ERROR(500,"error.system.fail", "System error"),
    UNKNOWN_ERROR(500, "error.system.unknown", "Unknown error"),

    // 业务错误
    BUSINESS_DEFAULT(500, "error.business.default", "Unsuccessful"),
    FLOW_CONTROL(429,  "error.business.sentinel.flow", "Too Many Requests"),
    OBJECT_CONFLICT(429,  "error.business.conflict", "Already exist"),

    // 校验错误 - MethodArgumentNotValidException/TypeMismatchException
    INVALID_PARAM(400, "error.common.param.invalid", "Bad Request"),
    MISSING_REQUIRED_FIELD(400,"error.common.param.miss", "Missing required field"),
    NEED_CAPTCHA(400, "error.common.param.miss-captcha", "Missing captcha"),

    // 认证授权错误 - 多来源于spring security
    // 401 认证阶段异常 (默认)- AuthenticationException
    UNAUTHORIZED(401,"error.security.authenticated.default", "Full authentication is required"),
    // 1. 认证失败，例如用户名或密码错误 - BadCredentialsException/UsernameNotFoundException
    BAD_CREDENTIAL(401,"error.security.authenticated.bad-credential", "Wrong user or password"),
    TO_MANY_LOGIN_ATTEMPT(429, "error.security.authenticated.authenticated.over-attempt", "Attempt to many"),
    // 2. 账户锁定/过期/禁用等 - LockedException/DisabledException/AccountExpiredException
    INVALID_ACCOUNT(401 ,"error.security.authenticated.invalid-account", "Unavailable account" +
            "(Locked/disabled/expired)"),
    // 3. 密码过期 - CredentialsExpiredException
    EXPIRED_CREDENTIAL(401,"error.security.authenticated.expired-credential", "Expired credential"),
    // 4. (内部)认证服务问题 - AuthenticationServiceException/InternalAuthenticationServiceException
    AUTHENTICATE_FAILED(500,"error.security.authenticated.authenticated.internal", "Internal Server Error"),
    // 5. 认证信息不足 - InsufficientAuthenticationException
    INSUFFICIENT(400, "error.security.authenticated.authenticated.insufficient", "Unauthorized"),

    // 403 授权阶段异常(默认) - AccessDeniedException
    ACCESS_DENIED(403,"error.security.access-denied.default", "Access denied"),

    // oauth2 相关错误
    OAUTH_FAIL(403,"error.oauth2.default", "Forbidden"),
    CLIENT_NOT_FOUND(403,"error.oauth2.client.miss", "Client not found"),;

    private final int code;

    // 唯一标识符 - 也是国际化识别码
    private final String id;

    // 默认信息一般只用于默认的log展出
    private final String defaultMessage;

    ErrorCode(int code, String id, String defaultMessage) {
        this.code = code;
        this.id = id;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public String getId() {
        return id;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * 根据id查询error类型
     */
    public static ErrorCode fromId(String id) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.id.equals(id)) {
                return errorCode;
            }
        }
        return UNKNOWN_ERROR;
    }
}