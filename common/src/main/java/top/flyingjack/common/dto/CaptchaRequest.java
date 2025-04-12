package top.flyingjack.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * 客户端返回的验证码请求
 *
 * @author Zumin Li
 * @date 2025/4/13 0:42
 */
@Schema(description = "验证码验证请求")
public record CaptchaRequest(
        @NotBlank
        @Schema(description = "唯一标识符, 可能是uuid(验证码图片), 手机号, 邮箱号")
        String captchaId,

        @NotBlank
        @Schema(description = "待验证的验证码")
        String captcha
){ }