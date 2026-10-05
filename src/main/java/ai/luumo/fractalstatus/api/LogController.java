package ai.luumo.fractalstatus.api;

import ai.luumo.fractalstatus.log.LogEntry;
import ai.luumo.fractalstatus.log.LogStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Central log access.
 *
 * <ul>
 *   <li>{@code GET /api/logs} - snapshot query, filterable by node-path prefix.</li>
 *   <li>{@code GET /api/logs/stream} - live SSE stream of matching entries.</li>
 * </ul>
 *
 * <p>The optional {@code path} parameter filters by path prefix, so
 * {@code path=root.servers} returns everything beneath that subtree.
 */
@RestController
@RequestMapping("/api/logs")
public class LogController {

    private static final long STREAM_TIMEOUT_MS = 0L; // no timeout

    private final LogStore logStore;

    public LogController(LogStore logStore) {
        this.logStore = logStore;
    }

    @GetMapping
    public List<LogEntry> query(@RequestParam(required = false) String path,
                                @RequestParam(defaultValue = "200") int limit) {
        return logStore.query(path, limit);
    }

    /** Max log entries buffered per SSE client before the oldest are dropped. */
    private static final int STREAM_BUFFER = 2000;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String path) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        LinkedBlockingQueue<LogEntry> queue = new LinkedBlockingQueue<>(STREAM_BUFFER);

        // The subscriber callback runs on the check threads: it only enqueues
        // (never blocks on the slow network send), dropping the oldest buffered
        // entry if this client is falling behind.
        LogStore.Subscription subscription = logStore.subscribe(path, entry -> {
            synchronized (queue) {
                while (!queue.offer(entry)) {
                    queue.poll();
                }
            }
        });

        // A single dedicated thread drains the queue and does the SSE writes, so
        // sends are serialized (thread-safe) and a slow client can't block or
        // disconnect-cascade the monitoring threads.
        Thread sender = Thread.ofVirtual()
                .name("sse-log-sender")
                .start(() -> {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            LogEntry entry = queue.take();
                            emitter.send(SseEmitter.event()
                                    .id(Long.toString(entry.seq()))
                                    .name("log")
                                    .data(entry));
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (Exception ex) {
                        emitter.completeWithError(ex);
                    }
                });

        Runnable cleanup = () -> {
            subscription.cancel();
            sender.interrupt();
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }
}
