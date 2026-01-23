package io.github.areebgillani.boost;

import io.github.areebgillani.boost.aspects.PathVariable;
import io.github.areebgillani.boost.aspects.RequestParam;
import io.github.areebgillani.boost.pojos.BoostResponseTemplate;
import io.github.areebgillani.boost.pojos.EndPointController;
import io.github.areebgillani.boost.utils.HttpRequest;
import io.github.areebgillani.boost.utils.MethodRecord;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SSLOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.micrometer.PrometheusScrapingHandler;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP Server Verticle that handles routing and request processing.
 * Supports GET, POST, PUT, DELETE, and PATCH HTTP methods.
 */
public class HttpServerVerticle extends AbstractVerticle {
    private Router router;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private JsonObject config;
    private Booster booster;
    private final ConcurrentHashMap<String, MethodRecord> methodBluePrint = new ConcurrentHashMap<>();

    @Override
    public void start() {
        if (BoostApplication.getInstance().isHasConfig()) {
            config = BoostApplication.getInstance().getConfig();
        }
        router = Router.router(vertx);
        booster = BoostApplication.getInstance().getBooster();
        initDefaultRoutingConfig();
        registerRoutes();
        startHttpServer();
        printRegisteredRoutes();
    }

    private void printRegisteredRoutes() {
        if (BoostApplication.printRoutes) {
            BoostApplication.printRoutes = false;
            for (Route r : router.getRoutes()) {
                if (r.getPath() != null) {
                    logger.info("Endpoint: " + r.getPath() + " " + r.methods());
                }
            }
        }
    }

    public void startHttpServer() {
        JsonObject serverConfig = config.getJsonObject("server");
        if (serverConfig == null) {
            logger.error("Missing 'server' configuration");
            return;
        }

        JsonObject httpConfig = serverConfig.getJsonObject("http");
        if (httpConfig == null) {
            logger.error("Missing 'server.http' configuration");
            return;
        }

        HttpServer httpServer = vertx.createHttpServer(getHttpOptions()).requestHandler(router);

        if (httpConfig.containsKey("SSL")) {
            httpServer.updateSSLOptions(new SSLOptions(httpConfig.getJsonObject("SSL")));
        }

        int port = httpConfig.getInteger("port", 8080);
        httpServer.listen(port)
                .onSuccess(server -> {
                    if (httpConfig.getInteger("instance", 1) > 1) {
                        logger.info("Shared Server instance started at port [" + server.actualPort() + "]");
                    }
                })
                .onFailure(failed -> logger.error("Server starting failure: " + failed.getMessage(), failed));
    }

    private void initDefaultRoutingConfig() {
        router.route().handler(CorsHandler.create());
        if (BoostApplication.getInstance().isMonitorable()) {
            router.route("/metrics").handler(PrometheusScrapingHandler.create());
        }
        router.route().handler(BodyHandler.create());

        // Global error handlers
        router.errorHandler(404, ctx -> ResponseHandler.error404(ctx,
                new BoostResponseTemplate(404, null, null, "Url not found", null)));
        router.errorHandler(500, ctx -> {
            Throwable failure = ctx.failure();
            logger.error("Internal server error", failure);
            ResponseHandler.error(ctx, new BoostResponseTemplate(500, "Internal Server Error",
                    failure != null ? failure.getMessage() : "Unknown error"));
        });
    }

    public HttpServerOptions getHttpOptions() {
        return new HttpServerOptions()
                .setTcpFastOpen(true)
                .setTcpQuickAck(true)
                .setTcpNoDelay(true);
    }

    private void registerRoutes() {
        registerMethodRoutes("POST", HttpMethod.POST);
        registerMethodRoutes("GET", HttpMethod.GET);
        registerMethodRoutes("PUT", HttpMethod.PUT);
        registerMethodRoutes("DELETE", HttpMethod.DELETE);
        registerMethodRoutes("PATCH", HttpMethod.PATCH);
    }

    private void registerMethodRoutes(String methodName, HttpMethod httpMethod) {
        List<EndPointController> endpoints = booster.getEndPointControllerMap().get(methodName);
        if (endpoints == null || endpoints.isEmpty()) {
            return;
        }

        for (EndPointController endPoint : endpoints) {
            router.route(httpMethod, endPoint.getEndPoint())
                    .handler(context -> handleRequest(endPoint, context, httpMethod));
        }
    }

    /**
     * Handles an incoming HTTP request with proper exception handling.
     */
    private void handleRequest(EndPointController endPoint, RoutingContext context, HttpMethod httpMethod) {
        try {
            Method method = endPoint.getInstanceMethod();
            Object controller = booster.getControllerInstanceMap().get(endPoint.getControllerName());

            if (controller == null) {
                logger.error("Controller not found: " + endPoint.getControllerName());
                ResponseHandler.error(context, new BoostResponseTemplate(500, "Controller not found"));
                return;
            }

            Object[] params = resolveParameters(method, context, httpMethod);
            Object result = method.invoke(controller, params);

            ResponseHandler.success(context, result, method.getReturnType());

        } catch (InvocationTargetException e) {
            // Exception thrown by the controller method itself
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            logger.error("Controller method error: " + cause.getMessage(), cause);
            ResponseHandler.error(context, new BoostResponseTemplate(500, "Error", cause));
        } catch (IllegalAccessException e) {
            logger.error("Cannot access controller method: " + e.getMessage(), e);
            ResponseHandler.error(context, new BoostResponseTemplate(500, "Access denied", e));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid arguments for controller method: " + e.getMessage(), e);
            ResponseHandler.error(context, new BoostResponseTemplate(400, "Bad Request", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error handling request: " + e.getMessage(), e);
            ResponseHandler.error(context, new BoostResponseTemplate(500, "Internal Server Error", e));
        }
    }

    /**
     * Resolves method parameters from the request context.
     */
    private Object[] resolveParameters(Method method, RoutingContext context, HttpMethod httpMethod) {
        int paramCount = method.getParameterCount();
        if (paramCount == 0) {
            return new Object[0];
        }

        Object[] params = new Object[paramCount];
        // Use declaring class name + method name + parameter types for unique cache key (handles overloading)
        String cacheKey = method.getDeclaringClass().getName() + "#" + method.getName() + java.util.Arrays.toString(method.getParameterTypes());
        MethodRecord methodRecord = methodBluePrint.computeIfAbsent(cacheKey,
                k -> new MethodRecord(method.getParameters(), method.getParameterAnnotations()));

        for (int i = 0; i < paramCount; i++) {
            params[i] = resolveParameter(methodRecord, i, context, httpMethod);
        }

        return params;
    }

    /**
     * Resolves a single parameter based on its type and annotations.
     */
    private Object resolveParameter(MethodRecord methodRecord, int index, RoutingContext context, HttpMethod httpMethod) {
        Class<?> type = methodRecord.declaredParams()[index].getType();
        Annotation[] annotations = methodRecord.declaredParamAnnotations()[index];

        // Handle special types first
        if (type.equals(RoutingContext.class)) {
            return context;
        }
        if (type.equals(HttpRequest.class)) {
            return new HttpRequest(context);
        }

        // Check for @PathVariable annotation
        PathVariable pathVariable = findAnnotation(annotations, PathVariable.class);
        if (pathVariable != null) {
            String paramValue = context.pathParam(pathVariable.value());
            return convertValue(paramValue, type);
        }

        // Check for @RequestParam annotation
        RequestParam requestParam = findAnnotation(annotations, RequestParam.class);
        if (requestParam != null) {
            String paramValue = context.request().getParam(requestParam.value());
            return convertValue(paramValue, type);
        }

        // For body-based methods (POST, PUT, PATCH), try to parse body
        if (httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT || httpMethod == HttpMethod.PATCH) {
            return resolveBodyParameter(type, context, methodRecord.declaredParams()[index].getType());
        }

        // For GET/DELETE without annotation, try query param with parameter name
        String paramName = methodRecord.declaredParams()[index].getName();
        String paramValue = context.request().getParam(paramName);
        if (paramValue != null) {
            return convertValue(paramValue, type);
        }

        return null;
    }

    /**
     * Resolves a parameter from the request body.
     */
    private Object resolveBodyParameter(Class<?> type, RoutingContext context, Class<?> targetType) {
        try {
            if (type.equals(String.class)) {
                return context.body().asString();
            }
            if (type.equals(JsonObject.class)) {
                return context.body().asJsonObject();
            }
            // Try to deserialize as JSON
            String body = context.body().asString();
            if (body != null && !body.isEmpty()) {
                return Json.decodeValue(body, targetType);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse body as " + type.getName() + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Finds an annotation of the specified type in the array.
     */
    @SuppressWarnings("unchecked")
    private <T extends Annotation> T findAnnotation(Annotation[] annotations, Class<T> annotationType) {
        if (annotations == null) {
            return null;
        }
        for (Annotation annotation : annotations) {
            if (annotationType.isInstance(annotation)) {
                return (T) annotation;
            }
        }
        return null;
    }

    /**
     * Converts a string value to the target type.
     */
    private Object convertValue(String value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        try {
            if (targetType.equals(String.class)) {
                return value;
            }
            if (targetType.equals(Integer.class) || targetType.equals(int.class)) {
                return Integer.parseInt(value);
            }
            if (targetType.equals(Long.class) || targetType.equals(long.class)) {
                return Long.parseLong(value);
            }
            if (targetType.equals(Double.class) || targetType.equals(double.class)) {
                return Double.parseDouble(value);
            }
            if (targetType.equals(Float.class) || targetType.equals(float.class)) {
                return Float.parseFloat(value);
            }
            if (targetType.equals(Boolean.class) || targetType.equals(boolean.class)) {
                return Boolean.parseBoolean(value);
            }
            if (targetType.equals(Character.class) || targetType.equals(char.class)) {
                return value.isEmpty() ? null : value.charAt(0);
            }
            if (targetType.equals(Byte.class) || targetType.equals(byte.class)) {
                return Byte.parseByte(value);
            }
            if (targetType.equals(Short.class) || targetType.equals(short.class)) {
                return Short.parseShort(value);
            }
            // Try JSON deserialization for complex types
            return Json.decodeValue(value, targetType);
        } catch (Exception e) {
            logger.warn("Failed to convert value '" + value + "' to " + targetType.getName());
            return null;
        }
    }
}
