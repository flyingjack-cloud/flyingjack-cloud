package top.flyingjack.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 无敏感信息，交换用User对象
 */
@Schema(description = "用户登录请求")
public record UserDto (
        @Schema(description = "用户id", example = "303066491119603712")
        @JsonFormat(shape = JsonFormat.Shape.STRING) // 防止前端丢失id精度，转为String
        Long id,

        @Schema(description = "用户名", example = "flyingjack")
        String username,

        @Schema(description = "手机号", example = "13012345678")
        String phone,

        @Schema(description = "邮箱", example = "test@test.com")
        String email
){ }
