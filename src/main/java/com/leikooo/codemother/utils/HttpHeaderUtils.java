package com.leikooo.codemother.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * @author leikooo
 */
public class HttpHeaderUtils {

    /**
     * 获取 Authorization: Bearer <token> 这个 token
     * @param authHeader 完整的 Authorization
     * @return token
     */
    public static String extractBearerToken(String authHeader) {
        if (StringUtils.isNotBlank(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
