package top.flyingjack.common.tool;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;

/**
 * 灵活Instant反序列化器
 *
 * @author Zumin Li
 * @date 2025/4/5 0:10
 */
public class FlexibleInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {

        String text = p.getText();
        try {
            // 尝试解析为数字时间戳
            long timestamp = Long.parseLong(text);

            // 根据数字长度判断是秒还是毫秒
            if (text.length() <= 10) { // 10位或更少是秒
                return Instant.ofEpochSecond(timestamp);
            } else { // 超过10位是毫秒
                return Instant.ofEpochMilli(timestamp);
            }

        } catch (NumberFormatException e) {
            // 如果不是数字，尝试解析为ISO格式
            return Instant.parse(text);
        }
    }
}