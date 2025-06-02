package customexecutor;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CustomThreadPoolExecutor {
    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueCapacity;
    private final long keepAliveTimeMillis;
    private final int minSpareThreads;

    private final List<BlockingQueue<Runnable>> taskQueues;
    private final Set<Worker> workers;
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private int rrIndex = 0; // для Round Robin

    public CustomThreadPoolExecutor(int corePoolSize, int maxPoolSize, int queueCapacity, long keepAliveTime, TimeUnit unit, int minSpareThreads) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueCapacity = queueCapacity;
        this.keepAliveTimeMillis = unit.toMillis(keepAliveTime);
        this.minSpareThreads = minSpareThreads;

        taskQueues = new ArrayList<>();
        workers = Collections.synchronizedSet(new HashSet<>());

        // Инициализируем очередь для каждого потенциального воркера (максимум maxPoolSize)
        for (int i = 0; i < maxPoolSize; i++) {
            taskQueues.add(new ArrayBlockingQueue<>(queueCapacity));
        }

        // Создаем corePoolSize воркеров заранее
        for (int i = 0; i < corePoolSize; i++) {
            addWorker(i);
        }
    }

    private void addWorker(int index) {
        Worker worker = new Worker(taskQueues.get(index), "Worker-" + index);
        workers.add(worker);
        worker.thread.start();
        System.out.println("[Pool] Создан новый воркер " + worker.thread.getName());
    }

    public synchronized boolean submit(Runnable task) {
        if (isShutdown.get()) {
            System.out.println("[Pool] Пул завершён, задачи не принимаются");
            return false;
        }

        // Round Robin распределение задач по очередям
        for (int i = 0; i < maxPoolSize; i++) {
            int queueIndex = (rrIndex + i) % maxPoolSize;
            BlockingQueue<Runnable> queue = taskQueues.get(queueIndex);
            if (queue.offer(task)) {
                System.out.println("[Pool] Задача добавлена в очередь " + queueIndex);
                rrIndex = (queueIndex + 1) % maxPoolSize;

                // Если меньше corePoolSize воркеров — создаём нового для этой очереди
                if (workers.size() < corePoolSize) {
                    addWorker(queueIndex);
                }
                return true;
            }
        }

        // Все очереди заполнены. Если можно, добавляем нового воркера и кладём туда задачу
        if (workers.size() < maxPoolSize) {
            int newIndex = workers.size();
            addWorker(newIndex);
            BlockingQueue<Runnable> newQueue = taskQueues.get(newIndex);
            if (newQueue.offer(task)) {
                System.out.println("[Pool] Задача добавлена в новую очередь " + newIndex);
                return true;
            }
        }

        // Очереди заполнены и воркеров максимум — отклоняем задачу
        System.out.println("[Pool] Все очереди заполнены, задача отклонена!");
        return false;
    }

    public void shutdown() {
        isShutdown.set(true);
        System.out.println("[Pool] Инициирован shutdown");

        synchronized (workers) {
            for (Worker worker : workers) {
                worker.shutdown();
            }
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        for (Worker worker : workers) {
            long waitTime = deadline - System.currentTimeMillis();
            if (waitTime > 0) {
                worker.thread.join(waitTime);
            }
        }
        return workers.stream().allMatch(w -> !w.thread.isAlive());
    }

    private class Worker implements Runnable {
        private final BlockingQueue<Runnable> taskQueue;
        private final Thread thread;
        private volatile boolean running = true;

        public Worker(BlockingQueue<Runnable> taskQueue, String name) {
            this.taskQueue = taskQueue;
            this.thread = new Thread(this, name);
        }

        @Override
        public void run() {
            while (running || !taskQueue.isEmpty()) {
                try {
                    Runnable task = taskQueue.poll(keepAliveTimeMillis, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        System.out.println("[" + thread.getName() + "] выполняет задачу");
                        task.run();
                        System.out.println("[" + thread.getName() + "] завершил задачу");
                    } else {
                        // Таймаут ожидания задачи — проверяем условия завершения
                        if (isShutdown.get() && taskQueue.isEmpty()) {
                            break;
                        }
                        synchronized (workers) {
                            // Удерживаем минимум minSpareThreads активных воркеров
                            if (workers.size() > corePoolSize && workers.size() > minSpareThreads) {
                                running = false;
                                workers.remove(this);
                                System.out.println("[" + thread.getName() + "] завершил работу (таймаут)");
                                break;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println("[" + thread.getName() + "] завершил работу");
        }

        public void shutdown() {
            running = false;
            thread.interrupt();
        }
    }
}
