package top.flyingjack.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Base64编码的图片验证码
 *
 * @author Zumin Li
 * @date 2025/4/13 0:42
 */
@Schema(description = "图片验证码")
public record CaptchaImgRes (
        @NotBlank
        @Schema(description = "唯一标识符")
        UUID uuid,

        @NotBlank
        @Schema(description = "base64编码的图片(160*60)")
        String base64Image
){ }