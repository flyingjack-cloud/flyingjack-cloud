package top.flyingjack.common.error;

import top.flyingjack.common.tool.Verify;

/**
 * 统一的错误信息拼装，方便后续进行国际化
 *
 * @author Zumin Li
 * @date 2025/4/3 13:38
 */
public class SysErrorMsgTool {
    /**
     * 用代号和拼接成统一格式错误信息
     *
     * @param code 错误代码，尽量使用SysErrorCode已有的
     * @param message 实际提示的的信息
     */
    public static String fromError(ErrorCode code, String message) {
        return "[" + code.getId() + "]:" + message;
    }

    public static String fromError(ErrorCode code) {
        return fromError(code, code.getDefaultMessage());
    }

    /**
     * 提取message中的code，如果查询不到就返回未知错
     *
     * @param message 包含code的完整信息
     */
    public static ErrorCode fromMessage(String message) {
        String code = "";

        if (Verify.isNotBlank(message)) {
            int start = message.indexOf('[');
            int end = message.indexOf(']');
            if (start != -1 && end != -1 && start < end) {
                code = message.substring(start + 1, end);
            }
        }

        return ErrorCode.fromId(code);
    }
}
