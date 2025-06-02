package customexecutor;

import java.util.concurrent.*;

public class CustomThreadPoolExecutorDemo {

    public static void main(String[] args) throws InterruptedException {
        int taskCount = 15;

        //Кастомный пул с RR балансировкой
        CustomThreadPoolExecutor customExecutor = new CustomThreadPoolExecutor(
                2, 4, 3, 5_000, TimeUnit.MILLISECONDS, 1);

        Runnable task = () -> {
            try {
                Thread.sleep(1000); // имитация работы
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        long startCustom = System.currentTimeMillis();

        for (int i = 0; i < taskCount; i++) {
            boolean accepted = customExecutor.submit(task);
            if (!accepted) {
                System.out.println("[CustomPool] Задача " + i + " отклонена");
            }
        }

        // Ждем завершения задач кастомного пула
        customExecutor.shutdown();
        customExecutor.awaitTermination(30, TimeUnit.SECONDS);

        long endCustom = System.currentTimeMillis();
        System.out.println("Время выполнения кастомного пула: " + (endCustom - startCustom) + " мс");

        // === Стандартный пул ThreadPoolExecutor ===
        ThreadPoolExecutor standardExecutor = new ThreadPoolExecutor(
                2, 4, 5, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(3),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy()
        );

        long startStandard = System.currentTimeMillis();

        for (int i = 0; i < taskCount; i++) {
            try {
                standardExecutor.execute(task);
            } catch (RejectedExecutionException e) {
                System.out.println("[StandardPool] Задача " + i + " отклонена");
            }
        }

        standardExecutor.shutdown();
        standardExecutor.awaitTermination(30, TimeUnit.SECONDS);

        long endStandard = System.currentTimeMillis();
        System.out.println("Время выполнения стандартного пула: " + (endStandard - startStandard) + " мс");
    }
}
