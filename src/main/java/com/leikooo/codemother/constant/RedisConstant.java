package com.leikooo.codemother.constant;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2025/12/25
 * @description
 */
public interface RedisConstant {

    /**
     * Redis 设置前缀
     */
    String MAIL_PREFIX = "codemother:mail:code";

    /**
     * Redis 发送标记
     */
    String MAIL_SEND_FLAG_PREFIX = "codemother:mail:send:code";

    /**
     * Redis 的 token
     */
    String MAIL_TOKEN_PREFIX = "codemother:mail:token";

    /**
     * 验证码过期时间 5 分钟
     */
    Integer CODE_ACTIVE_TTL = 300;

    /**
     * 3 分钟之后才可以发送
     */
    Integer CODE_SEND_TTL = 180;

    Integer TOKEN_ACTIVE_TTL = 180;
}
