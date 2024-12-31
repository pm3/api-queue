package eu.aston;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import eu.aston.utils.SuperTimer;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class AppFactory {

    @Singleton
    public SuperTimer superTimer(){
        Executor executor = Executors.newVirtualThreadPerTaskExecutor();
        return new SuperTimer(executor);
    }
}
