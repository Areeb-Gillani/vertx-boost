package io.github.areebgillani.boost;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

public abstract class AbstractService extends AbstractVerticle {
    Logger logger = LoggerFactory.getLogger(AbstractService.class);
    EventBus eventBus;
    @Override
    public void start() {
        eventBus = Vertx.currentContext().owner().eventBus();
        bindTopics();
    }
    public abstract void bindTopics();

}
