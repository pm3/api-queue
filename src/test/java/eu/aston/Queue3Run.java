package eu.aston;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;

public class Queue3Run {

    private static final HttpClient httpClient = HttpClient.newBuilder().build();

    public static void main(String[] args) {
        try{
            Queue3Run queue3Run = new Queue3Run();
            Executor executor = Executors.newThreadPerTaskExecutor(Thread::new);
            executor.execute(queue3Run::worker);

            Thread.sleep(300);
            for(int i=0; i<3; i++){
                queue3Run.send();
                Thread.sleep(100);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    int counter = 0;

    public void send() throws Exception{
        String json = "["+(++counter)+"]";
        HttpRequest r = HttpRequest
                .newBuilder()
                .uri(new URI("http://localhost:8080/queue/sum"))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .build();
        HttpResponse<String> resp = httpClient.send(r, HttpResponse.BodyHandlers.ofString());
        System.out.println(Thread.currentThread().getName()+" send /queue/sum "+resp.statusCode());
        //resp.headers().map().forEach((k,v)-> System.out.println("header: "+k+": "+v.getFirst()));
    }

    public void worker(){
        while (!Thread.interrupted()){
            try{
                workerStep();
            }catch (Exception e){
                System.out.println("exception "+e.getMessage());
                try{
                    Thread.sleep(1000);
                }catch (Exception e2){}
            }
        }
    }

    public void workerStep() throws Exception {
        //System.out.println("*** call worker");
        HttpRequest r = HttpRequest.newBuilder()
                                   .GET()
                                   .uri(new URI("http://localhost:8080/.queue/worker?path=/sum&workerId=w1"))
                                   .header("X-Api-Key", "123")
                                   .build();
        HttpResponse<byte[]> resp = httpClient.send(r, HttpResponse.BodyHandlers.ofByteArray());
        System.out.println(Thread.currentThread().getName()+" "+r.uri()+" "+resp.statusCode()+" "+new String(resp.body()));
        //resp.headers().map().forEach((k,v)-> System.out.println(k+": "+v.getFirst()));
    }

}
