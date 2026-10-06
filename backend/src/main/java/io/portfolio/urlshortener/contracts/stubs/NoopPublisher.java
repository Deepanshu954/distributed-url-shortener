package io.portfolio.urlshortener.contracts.stubs;

import io.portfolio.urlshortener.contracts.ClickEvent;
import io.portfolio.urlshortener.contracts.EventPublisher;
import io.portfolio.urlshortener.contracts.LinkEvent;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/**
 * Asynchronous in-memory event publisher for native zero-docker execution.
 * Utilizes a non-blocking micro-batch buffer for high-throughput click ingestion,
 * decoupling the hot redirect path from analytical database writes.
 */
@Component
public class NoopPublisher implements EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(NoopPublisher.class);
    private static final int BATCH_SIZE = 100;

    private final io.portfolio.urlshortener.events.ClickConsumer clickConsumer;
    private final io.portfolio.urlshortener.events.LinkEventConsumer linkEventConsumer;

    private final ConcurrentLinkedQueue<ClickEvent> clickBuffer = new ConcurrentLinkedQueue<>();
    private final ExecutorService linkWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "async-link-worker");
        t.setDaemon(true);
        return t;
    });

    private final ScheduledExecutorService batchFlusher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "telemetry-batch-flusher");
        t.setDaemon(true);
        return t;
    });

    public NoopPublisher(
            org.springframework.beans.factory.ObjectProvider<io.portfolio.urlshortener.events.ClickConsumer> clickConsumer,
            org.springframework.beans.factory.ObjectProvider<io.portfolio.urlshortener.events.LinkEventConsumer> linkEventConsumer) {
        this.clickConsumer = clickConsumer.getIfAvailable();
        this.linkEventConsumer = linkEventConsumer.getIfAvailable();

        // Flush buffered click telemetry every 250ms
        this.batchFlusher.scheduleWithFixedDelay(this::flushBatch, 250, 250, TimeUnit.MILLISECONDS);
    }

    @Override
    public void publishClick(ClickEvent event) {
        if (clickConsumer != null && event != null) {
            clickBuffer.offer(event);
            if (clickBuffer.size() >= BATCH_SIZE) {
                linkWorker.submit(this::flushBatch);
            }
        }
    }

    @Override
    public void publishLinkEvent(LinkEvent event) {
        if (linkEventConsumer != null && event != null) {
            linkWorker.submit(() -> {
                try {
                    linkEventConsumer.consume(event);
                } catch (Exception e) {
                    log.debug("Async link consumer swallow: {}", e.getMessage());
                }
            });
        }
    }

    private void flushBatch() {
        if (clickConsumer == null || clickBuffer.isEmpty()) {
            return;
        }

        int processed = 0;
        ClickEvent event;
        while (processed < BATCH_SIZE && (event = clickBuffer.poll()) != null) {
            try {
                clickConsumer.consume(event);
            } catch (Exception e) {
                log.debug("Batch click consumer swallow: {}", e.getMessage());
            }
            processed++;
        }
    }

    @PreDestroy
    public void close() {
        // Drain any remaining events before shutdown
        while (!clickBuffer.isEmpty()) {
            flushBatch();
        }
        batchFlusher.shutdown();
        linkWorker.shutdown();
    }
}
