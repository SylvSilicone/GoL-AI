import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** Calls student AI code without allowing one broken AI to freeze the tournament. */
class SafeAIInvoker implements AutoCloseable {

    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();

    private final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "CellWars-AI-" + THREAD_NUMBER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    AISelectionResult select(CellAI ai, Grid grid) {
        Future<Location> future = executor.submit(new Callable<Location>() {
            @Override
            public Location call() {
                return ai.select(grid);
            }
        });

        try {
            Location location = future.get(CellWarsConfig.AI_MOVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (location == null) {
                return AISelectionResult.nullLocation();
            }
            return AISelectionResult.ok(location);
        }
        catch (TimeoutException e) {
            future.cancel(true);
            return AISelectionResult.timeout();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return AISelectionResult.exception(e);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return AISelectionResult.exception(cause);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
