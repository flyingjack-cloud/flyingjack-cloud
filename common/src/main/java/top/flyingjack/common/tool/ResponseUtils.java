package top.flyingjack.common.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import top.flyingjack.common.dto.ApiRes;
import top.flyingjack.common.error.ErrorRes;

import java.io.IOException;

/**
 * Response相关工具
 *
 * @author Zumin Li
 * @date 2025/4/2 16:31
 */
public class ResponseUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将 ApiResponse 写入 HttpServletResponse
     */
    public static void writeJsonResponse(
            HttpServletResponse response,
            ApiRes<?> apiRes
    ) throws IOException {
        // 设置状态码
        response.setStatus(apiRes.getCode());

        // 设置 Content-Type 为 JSON
        response.setContentType("application/json;charset=UTF-8");

        // 将 ApiResponse 序列化为 JSON 并写入响应
        objectMapper.writeValue(response.getWriter(), apiRes);
    }

    /**
     * 将对象写入 HttpServletResponse
     */
    public static void writeErrorRes(
            HttpServletResponse response,
            ErrorRes errorRes
    ) throws IOException {
        // 设置状态码
        response.setStatus(errorRes.code());

        // 设置 Content-Type 为 JSON
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), errorRes);
    }
}
