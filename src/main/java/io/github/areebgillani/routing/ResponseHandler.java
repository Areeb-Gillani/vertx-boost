package io.github.areebgillani.routing;

import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;

public class ResponseHandler {
    public static void success(RoutingContext context, Object resp, Class<?> returnType){
        if(!returnType.getName().equals("void"))
            successSync(context, resp);
    }
    public static void successSync(RoutingContext context, Object resp){
        context.response().setChunked(true)
                .putHeader("Content-Type", "text/plain")
                .end(Json.encode(resp));
    }
    public void error(RoutingContext context, Object resp){
        context.response().setChunked(true)
                .setStatusCode(500)
                .putHeader("Content-Type", "text/plain")
                .end(Json.encode(resp));
    }
    public void error404(RoutingContext context, Object resp){
        context.response().setChunked(true)
                .setStatusCode(404)
                .putHeader("Content-Type", "text/plain")
                .end(Json.encode(resp));
    }
}
