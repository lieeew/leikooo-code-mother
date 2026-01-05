package com.leikooo.codemother.async;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConcurrentLockRemovalAlwaysFail {

    private static final ConcurrentHashMap<Integer, Object> lockMap = new ConcurrentHashMap<>();

    private static volatile int sharedCounter = 0;
    private static final int KEY = 123;

    // ===== 精准时序控制 =====
    // A 已经创建并进入 synchronized
    private static final CountDownLatch aEnteredLatch = new CountDownLatch(1);
    // A 已经 remove 了 key
    private static final CountDownLatch aRemovedLatch = new CountDownLatch(1);
    // B 已经进入 synchronized(oldLock)
    private static final CountDownLatch bEnteredLatch = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(3);

        executor.execute(ConcurrentLockRemovalAlwaysFail::threadA);
        executor.execute(ConcurrentLockRemovalAlwaysFail::threadB);
        executor.execute(ConcurrentLockRemovalAlwaysFail::threadC);

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("\n最终 sharedCounter = " + sharedCounter);
        System.out.println("理论正确值 = 1");
        System.err.println("❌ 实际结果证明发生了并发进入");
    }

    /**
     * 线程 A：创建锁，进入 synchronized，然后 remove
     */
    private static void threadA() {
        Object lock = lockMap.computeIfAbsent(KEY, k -> {
            Object newLock = new Object();
            System.out.println("[A] 创建 oldLock: " + System.identityHashCode(newLock));
            return newLock;
        });

        synchronized (lock) {
            System.out.println("[A] 进入 synchronized(oldLock)");
            aEnteredLatch.countDown();

            try {
                // 等 B 拿到 oldLock 的引用
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException ignored) {}

            lockMap.remove(KEY);
            System.out.println("[A] remove(KEY) 完成");
            aRemovedLatch.countDown();
        }

        System.out.println("[A] 释放 oldLock");
    }

    /**
     * 线程 B：拿到 oldLock，并进入 synchronized
     */
    private static void threadB() {
        try {
            aEnteredLatch.await();
        } catch (InterruptedException ignored) {}

        Object lock = lockMap.get(KEY);
        System.out.println("[B] 获取 oldLock 引用: " + System.identityHashCode(lock));

        synchronized (lock) {
            System.out.println(">>> [B] 进入 synchronized(oldLock)");
            bEnteredLatch.countDown();

            try {
                TimeUnit.SECONDS.sleep(2); // 长时间占用
                sharedCounter++;
                System.out.println(">>> [B] sharedCounter++");
            } catch (InterruptedException ignored) {}
        }

        System.out.println(">>> [B] 释放 oldLock");
    }

    /**
     * 线程 C：在 remove 后创建 newLock 并进入 synchronized
     */
    private static void threadC() {
        try {
            // 等 A remove 完成
            aRemovedLatch.await();
            // 确保 B 已经进入 oldLock
            bEnteredLatch.await();
        } catch (InterruptedException ignored) {}

        Object lock = lockMap.computeIfAbsent(KEY, k -> {
            Object newLock = new Object();
            System.err.println("[C] ❗创建 newLock: " + System.identityHashCode(newLock));
            return newLock;
        });

        synchronized (lock) {
            System.err.println("!!! [C] 进入 synchronized(newLock)");
            sharedCounter++;
            System.err.println("!!! [C] sharedCounter++");
        }

        System.err.println("[C] 释放 newLock");
    }
}
