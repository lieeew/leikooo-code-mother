package com.leikooo.codemother.utils;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @description Token 估算工具，基于 CL100K_BASE 编码
 */
public final class TokenEstimator {

    private static final Encoding ENCODING = Encodings.newDefaultEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    private static final double SAFETY_MULTIPLIER = 1.1;

    private static final int PER_MESSAGE_OVERHEAD = 4;

    private TokenEstimator() {
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(ENCODING.countTokens(text) * SAFETY_MULTIPLIER);
    }

    public static int estimateTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return messages.stream()
                .mapToInt(m -> estimateTokens(m.getText()) + PER_MESSAGE_OVERHEAD)
                .sum();
    }
}
