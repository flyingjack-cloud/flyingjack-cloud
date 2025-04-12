package top.flyingjack.common.tool;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Map;

/**
 * 常用的一些Http工具
 *
 * @author Zumin Li
 * @date 2025/4/11 23:08
 */
public class HttpTools {
    private static final Logger log = LoggerFactory.getLogger(HttpTools.class);

    // 生成zh-CN头
    public static HttpEntity<Object> zhHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept-Language", "zh-CN");
        return new HttpEntity<>(headers);
    }

    // 生成en-US头
    public static HttpEntity<Object> usHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept-Language", "en-US");
        return new HttpEntity<>(headers);
    }

    /**
     * 从 HttpServletRequest 中读取 JSON 并转换为指定类型的对象
     *
     */
    public static <T> T parseJsonToObject(HttpServletRequest request, Class<T> clazz) throws IOException {
        // 检查Content-Type
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
            throw new IllegalArgumentException("Content-Type must be application/json");
        }

        try {
            return new ObjectMapper().readValue(request.getInputStream(), clazz);
        } catch (JsonMappingException e){
            throw new IllegalArgumentException("Type not supported.");
        }catch (IOException e) {
            log.error("Parse JSON failed: {}", e.getMessage());
            throw new IllegalArgumentException("Parse JSON failed: " + e.getMessage(), e);
        }
    }

    // 获取请求ip, 注意如果使用Feign或者Gateway时一定要转发X-Forwarded-For头，不然获取不到nginx转发的真实请求头
    public static String getClientIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0];
        }
        return ipAddress;
    }
}
