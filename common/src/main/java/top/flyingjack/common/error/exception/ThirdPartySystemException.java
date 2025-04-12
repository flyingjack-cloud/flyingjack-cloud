package top.flyingjack.common.error.exception;

/**
 * 第三方接口错误
 *
 * @author Zumin Li
 * @date 2025/4/16 23:34
 */
public class ThirdPartySystemException extends ServiceInternalException{
    private String serverName;
    private String module;

    public ThirdPartySystemException(String message, String serverName, String module) {
        super(message);
        this.serverName = serverName;
        this.module = module;
    }

    public String getModule() {
        return module;
    }

    public String getServerName() {
        return serverName;
    }
}
