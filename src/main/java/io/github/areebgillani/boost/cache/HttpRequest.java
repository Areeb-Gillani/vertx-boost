package io.github.areebgillani.boost.cache;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.ext.web.RoutingContext;

public class HttpRequest {
    RoutingContext routingContext;
    Handler<AsyncResult<Message<Object>>> responseHandler = response->{
        if(response.succeeded()){
            routingContext.json(response.result().body());
        }else
            routingContext.json(response.cause());
    };

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
