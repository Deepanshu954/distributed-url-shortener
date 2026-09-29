package io.portfolio.urlshortener.shortener;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("stress")
class SnowflakeExtremeBenchmarkTest {

    @Test
    void benchmarkOneMillionSnowflakeIds_concurrent() throws Exception {
        final int totalIds = 1_000_000;
        final int threads = Math.max(8, Runtime.getRuntime().availableProcessors());
        final int idsPerThread = totalIds / threads;

        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(42);

        // Pre-allocate to detect collisions efficiently without OOM:
        // A ConcurrentHashMap or Long open-addressing set for 1 million IDs
        Set<Long> mintedIds = ConcurrentHashMap.newKeySet(totalIds);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        LongAdder failures = new LongAdder();

        ExecutorService executor = Executors.newFixedThreadPool(threads);

        long startWallTime = System.currentTimeMillis();

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < idsPerThread; i++) {
                        long id = generator.nextId();
                        if (!mintedIds.add(id)) {
                            failures.increment();
                        }
                    }
                } catch (Exception e) {
                    failures.increment();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release the floodgates
        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        long elapsedMs = System.currentTimeMillis() - startWallTime;
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(failures.sum()).isEqualTo(0);
        assertThat(mintedIds.size()).isEqualTo(totalIds);

        double idsPerSec = (totalIds * 1000.0) / elapsedMs;
        System.out.printf(
            "\n[SNOWFLAKE 1M STRESS BENCHMARK]\n" +
            "  Total Minted:     %,d IDs\n" +
            "  Concurrency:      %d worker threads\n" +
            "  Wall-clock Time:  %d ms (%.2f s)\n" +
            "  Throughput:       %,.0f IDs/sec\n" +
            "  Collision Rate:   0.000%% (0 duplicates)\n\n",
            totalIds, threads, elapsedMs, elapsedMs / 1000.0, idsPerSec
        );
    }

    @Test
    void simulateClockDriftBeyondTolerance_triggersDestructiveSafetyHalt() {
        long base = SnowflakeIdGenerator.EPOCH_MILLIS + 100_000;
        AtomicInteger offset = new AtomicInteger(0);
        SnowflakeIdGenerator driftGen = new SnowflakeIdGenerator(1, () -> base + offset.get());

        // Mint at base + 0
        long id1 = driftGen.nextId();
        assertThat(id1).isPositive();

        // Clock moves backward by 6ms (exceeding BACKWARD_TOLERANCE_MS=5ms)
        offset.set(-6);

        assertThatThrownBy(driftGen::nextId)
                .isInstanceOf(ClockMovedBackwardsException.class)
                .hasMessageContaining("clock moved backwards by 6ms");
    }
}
