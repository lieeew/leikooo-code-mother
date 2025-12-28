package com.leikooo.codemother.rejection;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates a ThreadPoolExecutor configured to reject tasks,
 * using a standard RejectedExecutionHandler instead of brittle reflection.
 */
public class ThreadPoolRejectionSimulation {

    // A custom handler to simulate the rejection logging/printing shown in the screenshot
    private static final class CustomRejectionHandler implements RejectedExecutionHandler {
        private final AtomicInteger rejectedCount = new AtomicInteger(0);

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            int currentRejected = rejectedCount.incrementAndGet();

            // Equivalent to the logging and printing seen in the screenshot
            System.err.printf(
                    "--- TASK REJECTED --- [Count: %d] Queue full (Size: %d). Task rejected: %s%n",
                    currentRejected,
                    executor.getQueue().size(),
                    r.toString()
            );
            // In a real application, you would throw or handle the exception appropriately.
            // For simulation, we just log and exit.
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // Core Rules for ThreadPool Configuration:
        // 1. Fixed thread count (Core: 1, Max: 1)
        // 2. Bounded Queue (Capacity: 1) - This is key for rejection
        // 3. Custom Rejection Handler
        int CORE_POOL_SIZE = 1;
        int MAX_POOL_SIZE = 1;
        int QUEUE_CAPACITY = 1;
        long KEEP_ALIVE_TIME = 0L; // Not critical for this example

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                new CustomRejectionHandler()
        );

        System.out.println("Starting Thread Pool Rejection Test...");

        // Task 1: Runs immediately on the core thread.
        // Task description (similar to the 'ChartDO@19909' arg in the image)
        Runnable task1 = () -> {
            System.out.println("Task 1 (Executing): Processing Chart Data.");
            try {
                // Simulate long-running work
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        // Task 2: Will be placed in the queue (capacity 1).
        Runnable task2 = () -> {
            System.out.println("Task 2 (Queued): Processing Secondary Data.");
        };

        // Task 3: The rejection candidate. Queue is full (Task 2 is waiting),
        // Max threads are reached (Task 1 is running), so it gets rejected.
        Runnable task3 = () -> {
            System.out.println("Task 3 (Should be Rejected): Processing Critical Data.");
        };


        // Submit tasks
        executor.execute(task1); // Running
        executor.execute(task2); // Queued

        // This task will trigger the CustomRejectionHandler
        try {
            executor.execute(task3);
        } catch (RejectedExecutionException e) {
            // This catch block is generally not needed if a handler is configured, 
            // but is good practice if the handler *also* throws.
        }

        // Wait for running tasks to finish
        Thread.sleep(1000);

        // Important: Always shut down the executor
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("Thread Pool Test Finished.");
    }
}