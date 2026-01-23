package io.github.areebgillani.boost;

import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;

/**
 * Handles HTTP response generation with proper content types and status codes.
 */
public class ResponseHandler {

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    /**
     * Sends a successful response. Handles void return types.
     */
    public static void success(RoutingContext context, Object resp, Class<?> returnType) {
        if (context.response().ended()) {
            return; // Response already sent (e.g., by async handler)
        }

        if (returnType == Void.TYPE) {
            // For void methods, the controller is responsible for writing the response
            // (typically via RoutingContext in an async callback like EventBus reply).
            // Do NOT send any response here - let the controller handle it.
            return;
        }

        // Handle Future/async responses
        if (resp instanceof Future<?> future) {
            future.onSuccess(result -> successSync(context, result))
                    .onFailure(err -> error(context, new io.github.areebgillani.boost.pojos.BoostResponseTemplate(
                            500, "Async operation failed", err)));
            return;
        }

        successSync(context, resp);
    }

    /**
     * Sends a synchronous successful JSON response.
     */
    public static void successSync(RoutingContext context, Object resp) {
        if (context.response().ended()) {
            return;
        }

        context.response()
                .setStatusCode(200)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(resp != null ? Json.encode(resp) : "");
    }

    /**
     * Sends a successful response with a specific status code.
     */
    public static void success(RoutingContext context, Object resp, int statusCode) {
        if (context.response().ended()) {
            return;
        }

        context.response()
                .setStatusCode(statusCode)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(resp != null ? Json.encode(resp) : "");
    }

    /**
     * Sends an error response with status 500.
     */
    public static void error(RoutingContext context, Object resp) {
        if (context.response().ended()) {
            return;
        }

        context.response()
                .setStatusCode(500)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(Json.encode(resp));
    }

    /**
     * Sends an error response with a custom status code.
     */
    public static void error(RoutingContext context, Object resp, int statusCode) {
        if (context.response().ended()) {
            return;
        }

        context.response()
                .setStatusCode(statusCode)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(Json.encode(resp));
    }

    /**
     * Sends a 404 Not Found response.
     */
    public static void error404(RoutingContext context, Object resp) {
        if (context.response().ended()) {
            return;
        }

        context.response()
                .setStatusCode(404)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(Json.encode(resp));
    }

    /**
     * Sends a 400 Bad Request response.
     */
    public static void badRequest(RoutingContext context, Object resp) {
        if (context.response().ended()) {
            return;
        }

        context.response()
                .setStatusCode(400)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(Json.encode(resp));
    }

    /**
     * Sends a 401 Unauthorized response.
     */
    public static void unauthorized(RoutingContext context, Object resp) {
        if (context.response().ended()) {
            return;
        }

        context.response()
                .setStatusCode(401)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(Json.encode(resp));
    }

    /**
     * Sends a 403 Forbidden response.
     */
    public static void forbidden(RoutingContext context, Object resp) {
        if (context.response().ended()) {
            return;
        }

        context.response()
                .setStatusCode(403)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(Json.encode(resp));
    }
}
