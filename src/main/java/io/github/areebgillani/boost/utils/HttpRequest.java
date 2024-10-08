package io.github.areebgillani.boost.utils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.ext.web.RoutingContext;

public class HttpRequest {
    RoutingContext routingContext;
    Handler<AsyncResult<Message<Object>>> responseHandler = response-> routingContext.json(response.succeeded()?response.result().body():response.cause());

    public HttpRequest(RoutingContext routingContext) {
        this.routingContext = routingContext;
    }

    public RoutingContext getRoutingContext() {
        return routingContext;
    }

    public Handler<AsyncResult<Message<Object>>> getResponseHandler() {
        return responseHandler;
    }
}
