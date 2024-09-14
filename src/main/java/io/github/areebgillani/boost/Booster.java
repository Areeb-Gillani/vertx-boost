package io.github.areebgillani.boost;

import io.github.areebgillani.boost.aspects.*;
import io.github.areebgillani.boost.pojos.EndPointController;
import io.github.areebgillani.boost.pojos.ServiceWorker;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import org.reflections.Reflections;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class Booster {
    Logger logger = LoggerFactory.getLogger(Booster.class);
    private final HashMap<String, Object> controllerInstanceMap = new HashMap<>();
    private final HashMap<String, List<EndPointController>> endPointControllerMap = new HashMap<>();
    private final List<ServiceWorker> serviceWorkerList = new ArrayList<>();
    String basePackage;
    Vertx vertx;
    JsonObject config;

    public Booster(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.config = config;
    }

    public void boost(String basePackage) throws Exception {
        this.basePackage = basePackage;
        scanControllers();
        scanServices();
    }

    private void scanControllers() throws Exception {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> controllers = reflections.getTypesAnnotatedWith(RestController.class);
        for (Class<?> controller : controllers) {
            Object controllerInstance = controller.getConstructor().newInstance();
            controllerInstanceMap.put(controller.getName(), controllerInstance);
            for (Method method : controller.getMethods()) {
                for (Annotation annotation : method.getAnnotations()) {
                    if (annotation instanceof PostMapping map) {
                        endPointControllerMap.putIfAbsent("POST", new ArrayList<>());
                        endPointControllerMap.get("POST").add(new EndPointController(map.value(), controller.getName(), method));
                    } else if (annotation instanceof GetMapping map) {
                        endPointControllerMap.putIfAbsent("GET", new ArrayList<>());
                        endPointControllerMap.get("GET").add(new EndPointController(map.value(), controller.getName(), method));
                    }
                }
            }
            vertx.deployVerticle((Verticle) controllerInstance, new DeploymentOptions()
                    .setConfig(config));
        }
    }

    private void scanServices() throws Exception {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> services = reflections.getTypesAnnotatedWith(Service.class);
        Set<Class<?>> repos = reflections.getTypesAnnotatedWith(Repository.class);
        JsonObject workers = config.getJsonObject("workers");
        for (Class<?> service : services) {
            Supplier<Verticle> myService = () -> {
                try {
                    Object serviceInstance = service.getConstructor().newInstance();
                    for (Field field : service.getDeclaredFields()) {
                        for (Annotation annotation : field.getAnnotations()) {
                            initClassVariables(repos, serviceInstance, field, annotation);
                        }
                    }
                    return (Verticle) serviceInstance;
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            };
            String workerName = getWorkerName(service);
            serviceWorkerList.add(new ServiceWorker(config, myService, workerName, workers.getJsonObject(workerName)));
        }
    }

    private void initClassVariables(Set<Class<?>> repos, Object serviceInstance, Field field, Annotation annotation) throws ClassNotFoundException, IllegalAccessException, InstantiationException, InvocationTargetException, NoSuchMethodException {
        if (annotation instanceof Autowired) {
            field.setAccessible(true);
            Class<?> instanceVar = Class.forName(field.getType().getName());
            if (!repos.isEmpty()) {
                initRepositoryVariable(repos, serviceInstance, field, instanceVar);
            } else {
                field.set(serviceInstance, instanceVar.getConstructor().newInstance());
            }
        }
    }

    private void initRepositoryVariable(Set<Class<?>> repos, Object serviceInstance, Field field, Class<?> instanceVar) throws IllegalAccessException, InstantiationException, InvocationTargetException, NoSuchMethodException {
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
    }

    private String getWorkerName(Class<?> service) {
        for (Annotation annotation : service.getAnnotations()) {
            if (annotation instanceof Service serv)
                return serv.value();
        }
        return "default-" + service.getName();
    }

    public HashMap<String, List<EndPointController>> getEndPointControllerMap() {
        return endPointControllerMap;
    }

    public HashMap<String, Object> getControllerInstanceMap() {
        return controllerInstanceMap;
    }

    public List<ServiceWorker> getServiceWorkerList() {
        return serviceWorkerList;
    }
}
