package io.github.areebgillani.boost;

import io.github.areebgillani.aspects.*;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.reflections.Reflections;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Supplier;

public class Booster {
    Logger logger = LoggerFactory.getLogger(Booster.class);
    public Router router;
    public HashMap<String, Object> controllerInstanceMap = new HashMap<>();
    public HashMap<String, Annotation[][]> methodBluePrint = new HashMap<>();
    String basePackage;
    Vertx vertx;
    JsonObject config;

    public Booster(Vertx vertx, Router router, JsonObject config) {
        this.vertx = vertx;
        this.router = router;
        this.config = config;
    }

    public void boost(String basePackage) throws Exception {
        this.basePackage = basePackage;
        deployControllers();
        deployServices();
    }

    private Object[] postParams(Method m, RoutingContext context) {
        Object[] params = new Object[m.getParameterCount()];
        Parameter[] declaredParams = m.getParameters();
        for (int i = 0; i < params.length; i++) {
            Class<?> type = declaredParams[i].getType();
            if (type.equals(RoutingContext.class)) {
                params[i] = context;
            } else {
                params[i] = context.body().asJsonObject();
            }

        }
        return params;
    }

    private Object[] getParams(Method m, RoutingContext context) {
        Object[] params = new Object[m.getParameterCount()];
        Parameter[] declaredParams = m.getParameters();
        Annotation[][] declaredParamAnnotations = methodBluePrint
                .computeIfAbsent(m.getName() + m.getClass().getName(),
                        k -> m.getParameterAnnotations());
        for (int i = 0; i < params.length; i++) {
            Class<?> type = declaredParams[i].getType();
            String value = context.request().getParam(((RequestParam) declaredParamAnnotations[i][0]).value());
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
            } else if (type.equals(RoutingContext.class)) {
                params[i] = context;
            } else {
                params[i] = Json.decodeValue(value, declaredParams[i].getType());
            }
        }
        return params;
    }

    private void deployControllers() throws Exception {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> controllers = reflections.getTypesAnnotatedWith(RestController.class);
        for (Class<?> controller : controllers) {
            Object controllerInstance = controller.getConstructor().newInstance();
            controllerInstanceMap.put(controller.getName(), controllerInstance);
            for (Method method : controller.getMethods()) {
                for (Annotation annotation : method.getAnnotations()) {
                    if (annotation instanceof PostMapping map) {
                        router.route(HttpMethod.POST, map.value())
                                .handler(context -> {
                                    Object cn = controllerInstanceMap.get(controller.getName());
                                    try {
                                        io.github.areebgillani.boost.ResponseHandler.success(context, method.invoke(cn, postParams(method, context)), method.getReturnType());
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                    } else if (annotation instanceof GetMapping map) {
                        router.route(HttpMethod.GET, map.value())
                                .handler(context -> {
                                    Object cn = controllerInstanceMap.get(controller.getName());
                                    try {
                                        io.github.areebgillani.boost.ResponseHandler.success(context, method.invoke(cn, getParams(method, context)), method.getReturnType());
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                    }
                }
            }
            for (Route r : router.getRoutes()) {
                if (r.getPath() != null)
                    logger.info("Endpoint: " + r.getPath() + " " + r.methods());
            }
            vertx.deployVerticle((Verticle) controllerInstance, new DeploymentOptions()
                    .setConfig(config));
        }
    }

    private void deployServices() throws Exception {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> services = reflections.getTypesAnnotatedWith(Service.class);
        Set<Class<?>> repos = reflections.getTypesAnnotatedWith(Repository.class);
        ;
        JsonObject workers = config.getJsonObject("workers");
        for (Class<?> service : services) {
            Supplier<Verticle> myService = () -> {
                try {
                    Object serviceInstance = service.getConstructor().newInstance();
                    for (Field field : service.getDeclaredFields()) {
                        for (Annotation annotation : field.getAnnotations()) {
                            if (annotation instanceof Autowired) {
                                Class<?> instanceVar = Class.forName(field.getType().getName());
                                if (!repos.isEmpty()) {
                                    if (repos.contains(instanceVar)) {
                                        String value = "Primary";
                                        for (Annotation instanceVarAnnotation : instanceVar.getAnnotations()) {
                                            if (instanceVarAnnotation instanceof Repository map) {
                                                value = map.value();
                                                break;
                                            }
                                        }
                                        field.set(serviceInstance, instanceVar.getConstructor(String.class, JsonObject.class).newInstance(value, config));
                                    }
                                } else {
                                    field.set(serviceInstance, instanceVar.getConstructor().newInstance());
                                }
                            }
                        }
                    }
                    return (Verticle) serviceInstance;
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            };
            String workerName = getWorkerName(service);
            deployWorkers(config, myService, workerName, workers.getJsonObject(workerName));
        }
    }

    private String getWorkerName(Class<?> service) {
        for (Annotation annotation : service.getAnnotations()) {
            if (annotation instanceof Service serv)
                return serv.value();
        }
        return "default";
    }

    private void deployWorkers(JsonObject config, Supplier<Verticle> serviceSupplier, String workerName, JsonObject workerConfig) throws Exception {
        vertx.deployVerticle(serviceSupplier, new DeploymentOptions()
                .setConfig(config)
                .setWorkerPoolName(workerName)
                .setWorkerPoolSize(workerConfig.getInteger("poolSize", 20))
                .setInstances(workerConfig.getInteger("instance", 5))
                .setWorker(true), res -> {
            if (res.succeeded())
                logger.info("Worker Deployed Successfully");
            else
                logger.error("Deployment Failed " + res.cause());
        });
    }
}
