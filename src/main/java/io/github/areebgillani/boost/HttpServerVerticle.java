package io.github.areebgillani.boost;

import io.github.areebgillani.boost.aspects.RequestParam;
import io.github.areebgillani.boost.pojos.BoostResponseTemplate;
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

import java.lang.reflect.Method;
import java.util.HashMap;

public class HttpServerVerticle extends AbstractVerticle {
    Router router;
    Logger logger = LoggerFactory.getLogger(this.getClass());
    JsonObject config;
    Booster booster;
    private final HashMap<String, MethodRecord> methodBluePrint = new HashMap<>();
    @Override
    public void start() {
        vertx = BoostApplication.getInstance().getVertx();
        if(BoostApplication.getInstance().isHasConfig())
            config = BoostApplication.getInstance().getConfig();
        router = Router.router(vertx);
        booster = BoostApplication.getInstance().getBooster();
        initDefaultRoutingConfig();
        registerRoutes();
        startHttpServer();
        if (BoostApplication.printRoutes) {
            BoostApplication.printRoutes = false;
            for (Route r : router.getRoutes()) {
                if (r.getPath() != null)
                    logger.info(" Endpoint: " + r.getPath() + " " + r.methods());
            }
        }
    }

    public void startHttpServer(){
        JsonObject httpConfig = config.getJsonObject("server").getJsonObject("http");
        HttpServer httpServer = vertx.createHttpServer(getHttpOptions()).requestHandler(router);
        if(httpConfig.containsKey("SSL"))
            httpServer.updateSSLOptions(new SSLOptions(httpConfig.getJsonObject("SSL")));
        httpServer.listen(httpConfig.getInteger("port", 8080))
                .onSuccess(server -> {
                    if (httpConfig.getInteger("instance",1) > 1) {
                        logger.info(("Shared Server instance started at port [" + server.actualPort() + "]. "));
                    }
                })
                .onFailure(failed -> logger.info("Server starting failure: "+failed.getMessage()));
    }

    private void initDefaultRoutingConfig() {
        router.route().handler(CorsHandler.create());
        if(BoostApplication.getInstance().isMonitorable())
            router.route("/metrics").handler(PrometheusScrapingHandler.create());
        router.route().handler(BodyHandler.create());
        router.errorHandler(404, resp-> ResponseHandler.error404(resp, new BoostResponseTemplate(404, null, null, "Url not found", null)));
    }

    public HttpServerOptions getHttpOptions(){
        return new HttpServerOptions()
                .setTcpFastOpen(true)
                .setTcpQuickAck(true)
                .setTcpNoDelay(true);
    }
    private void registerRoutes(){
        if(booster.getEndPointControllerMap().get("POST")!=null) {
            booster.getEndPointControllerMap().get("POST").forEach(endPoint -> {
                router.route(HttpMethod.POST, endPoint.getEndPoint())
                        .handler(context -> {
                            try {
                                ResponseHandler.success(context,
                                        endPoint.getInstanceMethod().invoke(booster.getControllerInstanceMap().get(endPoint.getControllerName()), postParams(endPoint.getInstanceMethod(), context)),
                                        endPoint.getInstanceMethod().getReturnType());
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            });
        }
        if(booster.getEndPointControllerMap().get("GET")!=null) {
            booster.getEndPointControllerMap().get("GET").forEach(endPoint -> {
                router.route(HttpMethod.GET, endPoint.getEndPoint())
                        .handler(context -> {
                            try {
                                ResponseHandler.success(context,
                                        endPoint.getInstanceMethod().invoke(booster.getControllerInstanceMap().get(endPoint.getControllerName()), getParams(endPoint.getInstanceMethod(), context)),
                                        endPoint.getInstanceMethod().getReturnType());
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            });
        }

    }

    private Object[] postParams(Method m, RoutingContext context) {
        Object[] params = new Object[m.getParameterCount()];
        if (m.getParameterCount() > 0) {
            MethodRecord methodRecord = methodBluePrint.computeIfAbsent(m.getName() + m.getClass().getName(), k -> new MethodRecord(m.getParameters(), m.getParameterAnnotations()));
            for (int i = 0; i < params.length; i++) {
                Class<?> type = methodRecord.declaredParams()[i].getType();
                if (type.equals(RoutingContext.class)) {
                    params[i] = context;
                } else if (type.equals(HttpRequest.class)) {
                    params[i] = new HttpRequest(context);
                } else if (type.equals(String.class)) {
                    params[i] = context.body().asString();
                } else if(type.equals(JsonObject.class)) {
                    params[i] = context.body().asJsonObject();
                } else {
                    params[i] = Json.decodeValue(context.body().asString(), methodRecord.declaredParams()[i].getType());
                }
            }
        }
        return params;
    }

    private Object[] getParams(Method m, RoutingContext context) {
        Object[] params = new Object[m.getParameterCount()];
        if (m.getParameterCount() > 0) {
            MethodRecord methodRecord = methodBluePrint.computeIfAbsent(m.getName() + m.getClass().getName(), k -> new MethodRecord(m.getParameters(), m.getParameterAnnotations()));
            for (int i = 0; i < params.length; i++) {
                Class<?> type = methodRecord.declaredParams()[i].getType();
                if (type.equals(RoutingContext.class)) {
                    params[i] = context;
                } else if (type.equals(HttpRequest.class)) {
                    params[i] = new HttpRequest(context);
                } else {
                    String value = context.request().getParam(((RequestParam) methodRecord.declaredParamAnnotations()[i][0]).value());
                    if (type.equals(Integer.class)) {
                        params[i] = Integer.parseInt(value);
                    } else if (type.equals(Float.class)) {
                        params[i] = Float.parseFloat(value);
                    } else if (type.equals(Double.class)) {
                        params[i] = Double.parseDouble(value);
                    } else if (type.equals(Boolean.class)) {
                        params[i] = Boolean.parseBoolean(value);
                    } else if (type.equals(String.class)) {
                        params[i] = value;
                    } else if (type.equals(Character.class)) {
                        params[i] = value.charAt(0);
                    } else if (type.equals(Byte.class)) {
                        params[i] = Byte.parseByte(value);
                    } else {
                        params[i] = Json.decodeValue(value, methodRecord.declaredParams()[i].getType());
                    }
                }
            }
        }
        return params;
    }

}
