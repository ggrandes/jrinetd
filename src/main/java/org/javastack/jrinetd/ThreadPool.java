package org.javastack.jrinetd;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadPool {
	private final ExecutorService threadPool;

	public ThreadPool() {
		threadPool = Executors.newCachedThreadPool();
	}

	public void newTask(final Runnable r) {
		threadPool.submit(r);
	}

	public void destroy() {
		shutdownAndAwaitTermination(threadPool);
	}

	void shutdownAndAwaitTermination(final ExecutorService pool) {
		pool.shutdown(); // Disable new tasks from being submitted
		try {
			if (!pool.awaitTermination(3, TimeUnit.SECONDS)) {
				pool.shutdownNow(); // Cancel currently executing tasks
				if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
					Log.error(getClass().getSimpleName(), "Pool did not terminate");
				}
			}
		} catch (InterruptedException ie) {
			pool.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	public boolean isTerminated() {
		return threadPool.isTerminated();
	}
}
