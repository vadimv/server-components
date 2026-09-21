package rsp.actor.testkit;

import rsp.actor.ActorDefinition;
import rsp.actor.runtime.LocalActorSystem;

import java.time.Duration;

/** Owns one local runtime with manually controlled execution and time. */
public final class ActorTestKit {
    private final ManualActorExecutor executor = new ManualActorExecutor();
    private final ManualActorScheduler scheduler = new ManualActorScheduler();
    private final LocalActorSystem.Builder builder = LocalActorSystem.builder()
            .executor(executor)
            .scheduler(scheduler);

    public ActorTestKit register(ActorDefinition<?, ?> definition) {
        builder.register(definition);
        return this;
    }

    public LocalActorSystem start() {
        LocalActorSystem system = builder.build();
        system.start();
        return system;
    }

    public ManualActorExecutor executor() {
        return executor;
    }

    public ManualActorScheduler scheduler() {
        return scheduler;
    }

    public void runAll() {
        executor.runAll();
    }

    public void advance(Duration duration) {
        scheduler.advance(duration);
        executor.runAll();
    }

    public <M> ActorProbe<M> probe() {
        return new ActorProbe<>();
    }
}
