package io.github.areebgillani.boost;

import io.github.areebgillani.boost.aspects.*;
import io.github.areebgillani.boost.pojos.EndPointController;
import io.github.areebgillani.boost.pojos.ServiceUnit;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Core class that scans for annotated controllers and services,
 * registers endpoints, and manages dependency injection.
 */
public class Booster {
    private final Logger logger = LoggerFactory.getLogger(Booster.class);
    private final ConcurrentHashMap<String, Object> controllerInstanceMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<EndPointController>> endPointControllerMap = new ConcurrentHashMap<>();
    private final List<ServiceUnit> serviceUnitList = new ArrayList<>();
    private String basePackage;
    private final Vertx vertx;
    private final JsonObject config;

    public Booster(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.config = config;
    }

    /**
     * Scans the base package for controllers and services.
     */
    public void boost(String basePackage) throws Exception {
        this.basePackage = basePackage;
        scanControllers();
        scanServices();
    }

    private void scanControllers() throws Exception {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> controllers = reflections.getTypesAnnotatedWith(RestController.class);

        for (Class<?> controller : controllers) {
            // Validate that controller extends AbstractVerticle
            if (!AbstractVerticle.class.isAssignableFrom(controller)) {
                logger.warn("Controller " + controller.getName() + " does not extend AbstractVerticle. Skipping.");
                continue;
            }

            try {
                Object controllerInstance = controller.getConstructor().newInstance();
                controllerInstanceMap.put(controller.getName(), controllerInstance);
                registerControllerMethods(controller, controllerInstance);

                vertx.deployVerticle((Verticle) controllerInstance, new DeploymentOptions().setConfig(config));
                logger.info("Registered controller: " + controller.getSimpleName());
            } catch (NoSuchMethodException e) {
                logger.error("Controller " + controller.getName() + " must have a no-args constructor", e);
            } catch (Exception e) {
                logger.error("Failed to instantiate controller " + controller.getName(), e);
            }
        }
    }

    /**
     * Registers all annotated methods from a controller.
     */
    private void registerControllerMethods(Class<?> controller, Object controllerInstance) {
        for (Method method : controller.getMethods()) {
            for (Annotation annotation : method.getAnnotations()) {
                String httpMethod = null;
                String path = null;

                if (annotation instanceof PostMapping map) {
                    httpMethod = "POST";
                    path = map.value();
                } else if (annotation instanceof GetMapping map) {
                    httpMethod = "GET";
                    path = map.value();
                } else if (annotation instanceof PutMapping map) {
                    httpMethod = "PUT";
                    path = map.value();
                } else if (annotation instanceof DeleteMapping map) {
                    httpMethod = "DELETE";
                    path = map.value();
                } else if (annotation instanceof PatchMapping map) {
                    httpMethod = "PATCH";
                    path = map.value();
                }

                if (httpMethod != null && path != null) {
                    endPointControllerMap.computeIfAbsent(httpMethod, k -> new ArrayList<>())
                            .add(new EndPointController(path, controller.getName(), method));
                    logger.info("Registered endpoint: " + httpMethod + " " + path + " -> " +
                            controller.getSimpleName() + "." + method.getName() + "()");
                }
            }
        }
    }

    private void scanServices() {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> services = reflections.getTypesAnnotatedWith(Service.class);
        Set<Class<?>> repos = reflections.getTypesAnnotatedWith(Repository.class);

        // Get service units config with null safety
        JsonObject serviceUnits = config.getJsonObject("ServiceUnits");
        if (serviceUnits == null) {
            serviceUnits = new JsonObject();
            logger.warn("No 'ServiceUnits' configuration found. Using defaults for all services.");
        }

        final JsonObject finalServiceUnits = serviceUnits;

        for (Class<?> service : services) {
            // Validate that service extends AbstractVerticle
            if (!AbstractVerticle.class.isAssignableFrom(service)) {
                logger.warn("Service " + service.getName() + " does not extend AbstractVerticle. Skipping.");
                continue;
            }

            Supplier<Verticle> myService = () -> {
                try {
                    Object serviceInstance = service.getConstructor().newInstance();
                    injectDependencies(serviceInstance, repos);
                    return (Verticle) serviceInstance;
                } catch (Exception e) {
                    logger.error("Failed to instantiate service " + service.getName(), e);
                    throw new RuntimeException(e);
                }
            };

            String serviceUnitName = getServiceUnitName(service);
            JsonObject serviceUnitConfig = finalServiceUnits.getJsonObject(serviceUnitName);

            // Use default config if not specified
            if (serviceUnitConfig == null) {
                serviceUnitConfig = new JsonObject()
                        .put("instance", 1)
                        .put("poolSize", 20)
                        .put("type", "W");
                logger.info("Using default configuration for service: " + serviceUnitName);
            }

            serviceUnitList.add(new ServiceUnit(config, myService, serviceUnitName, serviceUnitConfig));
            logger.info("Registered service: " + serviceUnitName);
        }
    }

    /**
     * Injects dependencies into a service instance.
     * Walks the class hierarchy to inject @Autowired fields from parent classes.
     */
    private void injectDependencies(Object serviceInstance, Set<Class<?>> repos)
            throws IllegalAccessException, InstantiationException, InvocationTargetException, NoSuchMethodException, ClassNotFoundException {

        // Walk the class hierarchy to include inherited fields
        Class<?> currentClass = serviceInstance.getClass();
        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    field.setAccessible(true);
                    Class<?> fieldType = Class.forName(field.getType().getName());

                    if (repos.contains(fieldType)) {
                        // It's a repository - inject with config
                        String connectionName = getRepositoryConnectionName(fieldType);
                        Object repoInstance = fieldType.getConstructor(String.class, JsonObject.class)
                                .newInstance(connectionName, config);
                        field.set(serviceInstance, repoInstance);
                        logger.info("Injected repository: " + fieldType.getSimpleName() + " with connection: " + connectionName);
                    } else {
                        // Regular class - try no-args constructor
                        try {
                            Object instance = fieldType.getConstructor().newInstance();
                            field.set(serviceInstance, instance);
                            logger.info("Injected dependency: " + fieldType.getSimpleName());
                        } catch (NoSuchMethodException e) {
                            logger.warn("Cannot inject " + fieldType.getName() + ": no default constructor");
                        }
                    }
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    /**
     * Gets the connection name from a @Repository annotation.
     */
    private String getRepositoryConnectionName(Class<?> repoClass) {
        Repository annotation = repoClass.getAnnotation(Repository.class);
        return annotation != null ? annotation.value() : "Primary";
    }

    private String getServiceUnitName(Class<?> service) {
        Service annotation = service.getAnnotation(Service.class);
        if (annotation != null && !annotation.value().isEmpty()) {
            return annotation.value();
        }
        return "default-" + service.getSimpleName();
    }

    public HashMap<String, List<EndPointController>> getEndPointControllerMap() {
        return new HashMap<>(endPointControllerMap);
    }

    public HashMap<String, Object> getControllerInstanceMap() {
        return new HashMap<>(controllerInstanceMap);
    }

    public List<ServiceUnit> getServiceUnitList() {
        return serviceUnitList;
    }

    /**
     * Dynamically registers a new controller at runtime.
     * Useful for hot-loading compiled classes.
     */
    public void registerDynamicController(Class<?> controllerClass) throws Exception {
        if (!controllerClass.isAnnotationPresent(RestController.class)) {
            throw new IllegalArgumentException("Class must have @RestController annotation");
        }
        if (!AbstractVerticle.class.isAssignableFrom(controllerClass)) {
            throw new IllegalArgumentException("Controller must extend AbstractVerticle");
        }

        Object controllerInstance = controllerClass.getConstructor().newInstance();
        controllerInstanceMap.put(controllerClass.getName(), controllerInstance);
        registerControllerMethods(controllerClass, controllerInstance);

        vertx.deployVerticle((Verticle) controllerInstance, new DeploymentOptions().setConfig(config));
        logger.info("Dynamically registered controller: " + controllerClass.getSimpleName());
    }

    /**
     * Removes a dynamically registered controller.
     */
    public void unregisterController(String className) {
        controllerInstanceMap.remove(className);
        // Note: Routes need to be removed from router separately
        logger.info("Unregistered controller: " + className);
    }
}
