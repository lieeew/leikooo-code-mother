package com.leikooo.codemother.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.hash.Hashing;

/**
 * 缓存 key 生成工具类
 *
 * @author yupi
 */
public class CacheKeyGenerator {

    // 1. 专门用于生成Key的Mapper，强制按字母顺序排序字段
    private static final ObjectMapper SORTED_MAPPER = new ObjectMapper();

    static {
        // CRITICAL: 保证字段顺序确定性 (e.g., {a:1, b:2} == {b:2, a:1})
        SORTED_MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        SORTED_MAPPER.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
    }

    /**
     * 生成分布式缓存 Key
     * 改进点：
     * 1. 确定性：强制 JSON 字段排序，防止同一对象生成不同 Key。
     * 2. 性能：使用 MurmurHash3 (128位) 代替 MD5。
     * 3. 碰撞：128位哈希几乎消除了缓存碰撞风险。
     */
    public static String generateKey(Object obj) {
        if (obj == null) {
            return "NULL_KEY";
        }

        try {
            byte[] jsonBytes = SORTED_MAPPER.writeValueAsBytes(obj);
            return Hashing.murmur3_128()
                    .hashBytes(jsonBytes)
                    .toString();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to generate cache key", e);
        }
    }
}