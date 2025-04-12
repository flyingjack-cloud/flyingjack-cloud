package top.flyingjack.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

/**
 * 统一格式的Api返回
 *
 * @param <T> T表示携带的相应数据格式
 */
@Schema(description = "Api统一返回格式")
public class ApiRes<T> {
    @Schema(description = "状态码")
    private int code;

    @Schema(description = "提示信息")
    private String message;

    @Schema(description = "响应数据")
    private T data;

    @Schema(description = "时间戳")
    private long timestamp; // 时间戳

    public static <T> ApiRes<T> success(T data) {
        return new ApiRes<>(200, "Success", data, System.currentTimeMillis());
    }

    public static <T> ApiRes<T> success() {
        return success(null);
    }

    public static <T> ApiRes<T> error(int code, String message) {
        return new ApiRes<>(code, message, null, System.currentTimeMillis());
    }

    public static <T> ApiRes<T> fail(HttpStatus status) {
        return new ApiRes<T>(status, null);
    }

    public static <T> ApiRes<T> fail(HttpStatus status, String message) {
        return new ApiRes<T>(status.value(), message, null, System.currentTimeMillis());
    }

    public ApiRes(int code, String message, T data, long timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
    }

    // 快速构造方法
    public ApiRes(HttpStatus status, T data) {
        this.code = status.value();
        this.message = status.getReasonPhrase();
        this.data = data;
    }

    public ApiRes(){}

    public void setCode(int code) {
        this.code = code;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setData(T data) {
        this.data = data;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public long getTimestamp() {
        return timestamp;
    }
}