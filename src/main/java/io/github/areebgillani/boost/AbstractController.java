package io.github.areebgillani.boost;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

public class AbstractController extends AbstractVerticle {
    public Logger logger = LoggerFactory.getLogger(this.getClass());
    public EventBus eventBus;
    @Override
    public void start() {
        eventBus = BoostApplication.getInstance().getVertx().eventBus();
    }
}
