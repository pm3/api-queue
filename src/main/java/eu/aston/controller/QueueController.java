package eu.aston.controller;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import eu.aston.header.HeaderConverter;
import eu.aston.queue.QueueEvent;
import eu.aston.queue.QueueStat;
import eu.aston.queue.QueueStore;
import eu.aston.queue.Worker;
import eu.aston.utils.ID;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class QueueController {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueController.class);

    private final QueueStore queueStore;
    private final String workerApiKey;

    public QueueController(QueueStore queueStore, @Value("${app.workerApiKey}") String workerApiKey) {
        this.queueStore = queueStore;
        this.workerApiKey = workerApiKey!=null && !workerApiKey.isEmpty() ? workerApiKey : null;
    }

    @Post(value = "/queue/{path:.*}", processes = MediaType.ALL)
    public HttpResponse<Object> send(HttpRequest<byte[]> request, @PathVariable("path") String path){
        QueueEvent event = new QueueEvent();
        event.setId(request.getHeaders().get(HeaderConverter.H_ID));
        if(event.getId()==null) event.setId(ID.newId());
        event.setMethod(request.getMethodName());
        event.setPath("/"+path);
        event.setHeaders(HeaderConverter.eventRequest(request.getHeaders(), event.getId()));
        event.setBody(request.getBody().orElse(null));
        LOGGER.info("queue /{} {}", path, event.getId());
        queueStore.addEvent(event);
        return HttpResponse.status(201, "accepted").header("fw-event-id", event.getId());
    }

    @Get(value = "/.queue/worker")
    public CompletableFuture<HttpResponse<?>> workerConsume(HttpRequest<byte[]> request, @QueryValue String path, @QueryValue String workerId) {
        if(workerApiKey!=null && !Objects.equals(workerApiKey, request.getHeaders().getFirst("X-Api-Key").orElse(null))){
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "require X-Api-Key");
        }
        CompletableFuture<HttpResponse<?>> future = new CompletableFuture<>();
        Worker worker = new Worker(workerId, path, 5, future);
        LOGGER.debug("worker {} {}", path, workerId);
        queueStore.workerQueue(worker);
        return future;
    }

    @Get(value = "/.queue/stats")
    public List<QueueStat> stats(){
        return queueStore.stats();
    }
}
