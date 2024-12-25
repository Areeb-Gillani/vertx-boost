package io.github.areebgillani.boost;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

public abstract class AbstractService extends AbstractVerticle {
    public Logger logger = LoggerFactory.getLogger(this.getClass());
    public EventBus eventBus;
    public EventBus clusteredEventBus;
    @Override
    public void start() {
        eventBus = BoostApplication.getInstance().getVertx().eventBus();
        if(BoostApplication.getInstance().getClusteredVertx()!=null)
            clusteredEventBus = BoostApplication.getInstance().getClusteredVertx().eventBus();
        bindTopics();
    }
    public abstract void bindTopics();

}
