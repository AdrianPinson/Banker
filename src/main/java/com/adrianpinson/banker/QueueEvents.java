package com.adrianpinson.banker;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.annotation.PreDestroy;

/** Revision-only notifications. Database polling also observes commits from another app process. */
@Component
@EnableScheduling
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class QueueEvents {
    private final BankRepository repo;
    private final Map<SseEmitter, Long> clients = new ConcurrentHashMap<>();
    public QueueEvents(BankRepository repo) { this.repo = repo; }
    public synchronized SseEmitter subscribe() {
        if (clients.size() >= 64) throw new BankException(503, "LIVE_LIMIT", "Live updates are busy. Periodic refresh remains available.");
        // A reconnect rechecks authentication at least every 25 seconds.
        SseEmitter emitter = new SseEmitter(25_000L);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> { clients.remove(emitter); emitter.complete(); });
        emitter.onError(error -> clients.remove(emitter));
        clients.put(emitter, -1L);
        send(emitter, repo.revision());
        return emitter;
    }
    @Scheduled(fixedDelay = 1000)
    public void changed() {
        if (clients.isEmpty()) return;
        try {
            long revision = repo.revision();
            clients.forEach((client, previous) -> { if (previous != revision) send(client, revision); });
        } catch (RuntimeException unavailable) {
            clients.keySet().forEach(client -> { clients.remove(client); client.completeWithError(unavailable); });
        }
    }
    private void send(SseEmitter client, long revision) {
        try {
            client.send(SseEmitter.event().name("revision").id(Long.toString(revision)).data(Map.of("revision", revision)));
            clients.replace(client, revision);
        } catch (IOException | IllegalStateException closed) { clients.remove(client); client.complete(); }
    }
    @PreDestroy public void close() { clients.keySet().forEach(SseEmitter::complete); clients.clear(); }
    @RestController
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public static class Controller {
        private final QueueEvents events;
        public Controller(QueueEvents events) { this.events = events; }
        @GetMapping(value = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter events(jakarta.servlet.http.HttpServletResponse response) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Accel-Buffering", "no");
            return events.subscribe();
        }
    }
}
