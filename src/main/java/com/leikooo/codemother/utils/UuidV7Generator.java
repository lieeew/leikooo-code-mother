package com.leikooo.codemother.utils;

import com.github.f4b6a3.uuid.UuidCreator;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * @author leikooo
 * @description 生成 uuidv7 B+Tree 插入顺序是友好的
 */
public final class UuidV7Generator {

    private UuidV7Generator() {

    }

    private static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    /**
     * UUID v7 (time-ordered, epoch milliseconds)
     */
    public static byte[] generate() {
        return uuidToBytes(UuidCreator.getTimeOrderedEpoch());
    }
}
