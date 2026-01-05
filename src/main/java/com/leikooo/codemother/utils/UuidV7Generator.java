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

    public static String bytesToUuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long mostSig = buffer.getLong();
        long leastSig = buffer.getLong();
        return new UUID(mostSig, leastSig).toString();
    }

    /**
     * String 格式 UUID 转为 byte[]
     */
    public static byte[] stringToBytes(String uuid) {
        String s = uuid.replace("-", "");
        byte[] bytes = new byte[16];
        for (int i = 0; i < 16; i++) {
            bytes[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
