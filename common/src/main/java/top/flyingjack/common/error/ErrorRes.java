package top.flyingjack.common.error;

/**
 * 统一错误响应体
 *
 * @author Zumin Li
 * @date 2025/4/11 12:31
 */
public record ErrorRes(
        int code,
        String message,
        String path,
        long timestamp
) { }
