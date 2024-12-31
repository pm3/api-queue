package eu.aston.queue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import eu.aston.utils.SuperTimer;
import io.micronaut.http.HttpResponse;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QueueStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueStore.class);
    private final ConcurrentHashMap<String, QueueEvent> eventMap = new ConcurrentHashMap<>();
    private final TreeMap<String, WorkerGroup> workerTree = new TreeMap<>();
    private final SuperTimer superTimer;

    public QueueStore(SuperTimer superTimer) {
        this.superTimer = superTimer;
    }

    public WorkerGroup workerGroupByPath(String path) {
        synchronized (workerTree) {
            Map.Entry<String, WorkerGroup> entry = workerTree.floorEntry(path);
            if (entry != null && path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public void addEvent(QueueEvent event) {
        WorkerGroup workerGroup = workerGroupByPath(event.getPath());
        addEvent(workerGroup, event);
    }

    public void addEvent(WorkerGroup workerGroup, QueueEvent event) {
        eventMap.put(event.getId(), event);
        if (workerGroup != null) {
            boolean sent = nextWorker(workerGroup, (w) -> sendRemoteEvent(event, w));
            if (!sent){
                LOGGER.debug("waiting in queue {}", event.getId());
                workerGroup.events.add(event.getId());
            }
        } else {
            LOGGER.debug("event without worker {} {}", event.getPath(), event.getId());
            superTimer.schedule(120 * 1000L, event.getId(), eventMap::remove);
        }
    }

    private boolean nextWorker(WorkerGroup workerGroup, Consumer<CompletableFuture<HttpResponse<?>>> sender) {
        while (true) {
            Worker worker = null;
            try {
                worker = workerGroup.workers.poll(25, TimeUnit.MICROSECONDS);
            }catch (InterruptedException ignore){}
            if(worker==null) break;
            var w = worker.removeResponse();
            if (w != null) {
                try {
                    sender.accept(w);
                    return true;
                } catch (Exception e) {
                    LOGGER.debug("error write worker");
                }
            }
        }
        return false;
    }

    private void sendRemoteEvent(QueueEvent event, CompletableFuture<HttpResponse<?>> w) {
        eventMap.remove(event.getId());
        w.complete(HttpResponse.ok((Object) event.getBody()).headers(new HashMap<>(event.getHeaders())));
    }

    public void workerQueue(Worker worker) {
        String prefix = worker.getPrefix();
        WorkerGroup workerGroup;
        synchronized (workerTree) {
            workerGroup = workerTree.get(prefix);
            if(workerGroup==null){
                workerGroup = new WorkerGroup(prefix);
                workerTree.put(prefix, workerGroup);
                LOGGER.info("create new worker group {}", prefix);
                workerGroupAddEvents(workerGroup, new ArrayList<>(eventMap.values().stream().filter(e->e.getPath().startsWith(prefix)).toList()));
            }
        }
        workerGroup.eventCounter.incrementAndGet();
        workerGroup.lastWorker = System.currentTimeMillis();
        workerGroup.lastWorkerPing.put(worker.getId(), System.currentTimeMillis());
        QueueEvent event = null;
        while(!workerGroup.events.isEmpty() && event==null) {
            String eventId = workerGroup.events.poll();
            if(eventId!=null){
                event = eventMap.remove(eventId);
            }
        }
        if (event != null) {
            sendRemoteEvent(event, worker.removeResponse());
        } else {
            workerGroup.workers.add(worker);
            superTimer.schedule(worker.getTimeout() * 1000L, worker, this::timeoutWorker);
        }
    }

    private void workerGroupAddEvents(WorkerGroup workerGroup, List<QueueEvent> list) {
        list.sort((e1,e2)->(int)(e1.getCreated()-e2.getCreated()));
        for (QueueEvent event : list){
            boolean sent = nextWorker(workerGroup, (w) -> sendRemoteEvent(event, w));
            if (!sent) {
                workerGroup.events.add(event.getId());
            }
        }
    }

    private void timeoutWorker(Worker worker) {
        var writer = worker.removeResponse();
        if (writer != null) {
            try{
                writer.complete(HttpResponse.accepted());
                LOGGER.debug("worker timeout {}", worker.getPrefix());
            }catch (Exception ignore){}
        }
    }

    public List<QueueStat> stats() {
        Instant now = Instant.now();
        List<WorkerGroup> groups = new ArrayList<>(workerTree.values());
        return groups.stream().map(wg->createStat(wg, now)).toList();
    }

    private QueueStat createStat(WorkerGroup wg, Instant now) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(wg.lastWorkerPing.entrySet());
        long expired = System.currentTimeMillis()-120_000;
        for(Map.Entry<String, Long> e: entries){
            if(e.getValue()<expired) wg.lastWorkerPing.remove(e.getKey(), e.getValue());
        }
        Long oldestEvent = Optional.ofNullable(wg.events.peek()).map(eventMap::get).map(QueueEvent::getCreated).orElse(null);
        return new QueueStat(
                wg.prefix,
                wg.eventCounter.get(),
                wg.events.size(),
                oldestEvent!=null ? Instant.ofEpochMilli(oldestEvent) : null,
                Instant.ofEpochMilli(wg.lastWorker),
                wg.lastWorkerPing.size(),
                new ArrayList<>(wg.lastWorkerPing.keySet())
        );
    }

}
