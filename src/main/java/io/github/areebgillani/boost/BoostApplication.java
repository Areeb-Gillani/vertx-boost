package io.github.areebgillani.boost;

import io.github.areebgillani.boost.pojos.EndPointController;
import io.github.areebgillani.boost.pojos.ServiceUnit;
import io.github.areebgillani.boost.utils.VertxClusterUtils;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.*;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Main application class for Boost framework.
 * Extend this class and call run() to start your application.
 */
public class BoostApplication extends AbstractVerticle {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private Vertx localVertx;
    private Vertx clusteredVertx;
    private JsonObject config;
    protected static volatile BoostApplication instance;
    private static volatile boolean isClusteredMode = false;
    private static volatile String configPath = "config.json";
    private final AtomicBoolean hasConfig = new AtomicBoolean(false);
    private final AtomicBoolean isMonitorable = new AtomicBoolean(false);
    private HashMap<String, List<EndPointController>> controllers;
    private Booster booster;
    private final Promise<BoostApplication> promise = Promise.promise();
    public static volatile boolean printRoutes = true;

    public static BoostApplication getInstance() {
        return instance;
    }

    @Override
    public void start() throws Exception {
        super.start();
        localVertx = Vertx.vertx();
        instance = this;
        localVertx.executeBlocking(() -> {
            try {
                deployApplication(configPath, isClusteredMode);
            } catch (Exception e) {
                logger.error("Failed to deploy application", e);
                promise.fail(e);
            }
            return promise.future();
        });
    }

    public void init(Vertx vertx, String configPath) {
        this.localVertx = vertx;
        loadConfig(configPath)
                .onSuccess(cfg -> {
                    this.config = cfg;
                    this.booster = new Booster(localVertx, config);
                    hasConfig.set(true);
                    logger.info("Configuration loaded successfully");
                })
                .onFailure(err -> logger.error("Failed to load configuration", err));
    }

    public void init(Vertx vertx, JsonObject config) {
        this.localVertx = vertx;
        this.config = config;
        this.booster = new Booster(localVertx, config);
        hasConfig.set(true);
    }

    /**
     * Loads configuration asynchronously using Vert.x config retriever.
     */
    private Future<JsonObject> loadConfig(String folderPath) {
        Promise<JsonObject> configPromise = Promise.promise();
        ConfigRetriever.create(localVertx, initRetrieverConfig(folderPath))
                .getConfig()
                .onSuccess(cfg -> {
                    this.config = cfg;
                    hasConfig.set(true);
                    configPromise.complete(cfg);
                })
                .onFailure(err -> {
                    logger.error("Error loading config: " + err.getMessage(), err);
                    // Use empty config as fallback
                    this.config = new JsonObject();
                    configPromise.fail(err);
                });
        return configPromise.future();
    }

    /**
     * Loads configuration synchronously (blocking) - use with caution.
     */
    private void loadConfigSync(String folderPath) {
        try {
            JsonObject cfg = ConfigRetriever.create(localVertx, initRetrieverConfig(folderPath))
                    .getConfig()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get();
            this.config = cfg;
            hasConfig.set(true);
            logger.info("Config loaded successfully!");
        } catch (Exception e) {
            logger.error("Error loading config", e);
            this.config = new JsonObject();
        }
    }

    /**
     * Deploy the application with the specified config file.
     * Uses non-clustered mode by default.
     * @param folderPath Path to the configuration file
     */
    public void deployApplication(String folderPath) throws Exception {
        deployApplication(folderPath, false);
    }

    /**
     * Apply configuration overrides from environment variables and system properties.
     * Precedence: System Properties (command line -D) > Environment Variables > Config File
     * Supports hierarchical property notation: -Da.b.c=xyz -> {"a":{"b":{"c":"xyz"}}}
     */
    private void applyConfigurationOverrides() {
        // First, apply environment variable overrides
        System.getenv().forEach((key, value) -> {
            if (key.contains(".")) {
                // Convert dot notation to hierarchical structure
                mergeHierarchicalProperty(config, key, value, "Environment Variable");
            }
        });

        // Then, apply system property overrides (highest priority)
        System.getProperties().forEach((key, value) -> {
            String propKey = key.toString();
            String propValue = value.toString();
            if (propKey.contains(".")) {
                // Convert dot notation to hierarchical structure
                mergeHierarchicalProperty(config, propKey, propValue, "System Property");
            }
        });
    }

    /**
     * Merge a dot-notation property into the config JsonObject hierarchically.
     * Example: "a.b.c" with value "xyz" creates/updates {"a":{"b":{"c":"xyz"}}}
     *
     * @param config The base configuration object to merge into
     * @param propertyPath The dot-notation property path (e.g., "a.b.c")
     * @param value The value to set
     * @param source The source of the override (for logging)
     */
    private void mergeHierarchicalProperty(JsonObject config, String propertyPath, String value, String source) {
        String[] parts = propertyPath.split("\\.");
        JsonObject current = config;

        // Navigate/create the hierarchy up to the parent of the final property
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (!current.containsKey(part)) {
                current.put(part, new JsonObject());
            } else if (!(current.getValue(part) instanceof JsonObject)) {
                // If the existing value is not a JsonObject, replace it with one
                logger.warn("Overriding non-object value at '" + part + "' to support nested property from " + source);
                current.put(part, new JsonObject());
            }
            current = current.getJsonObject(part);
        }

        // Set the final value, attempting to parse it as the appropriate type
        String finalKey = parts[parts.length - 1];
        Object parsedValue = parseValue(value);
        current.put(finalKey, parsedValue);
        logger.info("Config override applied from " + source + ": " + propertyPath + " = " + value);
    }

    /**
     * Parse a string value to its appropriate type (boolean, number, or string).
     *
     * @param value The string value to parse
     * @return The parsed value as Boolean, Integer, Double, or String
     */
    private Object parseValue(String value) {
        // Try boolean
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }

        // Try integer
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // Not an integer, continue
        }

        // Try double
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            // Not a double, continue
        }

        // Default to string
        return value;
    }

    public void deployApplication(String folderPath, Boolean isClustered) throws Exception {
        loadConfigSync(folderPath);
        
        if (config == null) {
            config = new JsonObject();
            logger.warn("No configuration loaded, using defaults");
        }

        VertxOptions vertxOptions = enableMonitoringOption(getVertxOptions());
        localVertx = Vertx.vertx(vertxOptions);
        booster = new Booster(localVertx, config);
        booster.boost(this.getClass().getPackage().getName());

        if (isClustered) {
            try {
                clusteredVertx = VertxClusterUtils.initClusterVertx(config, vertxOptions);
                if (clusteredVertx == null) {
                    logger.warn("Failed to initialize clustered Vert.x - running in standalone mode");
                }
            } catch (Exception e) {
                logger.error("Cluster initialization failed", e);
            }
        }

        deployServices();
        deployHTTPServer();
        promise.complete(this);
    }

    /**
     * Deploys all registered services.
     */
    private void deployServices() {
        for (ServiceUnit service : booster.getServiceUnitList()) {
            JsonObject serviceConfig = service.getServiceUnitConfig();
            if (serviceConfig == null) {
                serviceConfig = new JsonObject()
                        .put("instance", 1)
                        .put("poolSize", 20)
                        .put("type", "W");
            }
            deployService(service.getGlobalConfig(), service.getServiceSupplier(),
                    service.getServiceUnitName(), serviceConfig);
        }
    }

    private void deployHTTPServer() {
        JsonObject serverConfig = config.getJsonObject("server");
        if (serverConfig == null) {
            logger.info("No 'server' configuration found - HTTP server not started");
            return;
        }

        JsonObject httpConfig = serverConfig.getJsonObject("http");
        if (httpConfig == null) {
            logger.info("No 'server.http' configuration found - HTTP server not started");
            return;
        }

        if (!httpConfig.getBoolean("enable", true)) {
            logger.info("HTTP server disabled in configuration");
            return;
        }

        Supplier<Verticle> httpServer = HttpServerVerticle::new;
        int instances = httpConfig.getInteger("instance", 1);
        int port = httpConfig.getInteger("port", 8080);
        deployHttpService(httpServer, instances, port);
    }

    private VertxOptions enableMonitoringOption(VertxOptions vertxOptions) {
        JsonObject metricsConfig = config.getJsonObject("metrics");
        if (metricsConfig != null && metricsConfig.getBoolean("enabled", false)) {
            MicrometerMetricsOptions metricsOptions = new MicrometerMetricsOptions()
                    .setEnabled(true);
            String tool = metricsConfig.getString("tool", "");
            if ("prometheus".equalsIgnoreCase(tool)) {
                metricsOptions.setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true));
                isMonitorable.set(true);
                logger.info("Prometheus metrics enabled");
            }
            vertxOptions.setMetricsOptions(metricsOptions);
        }
        return vertxOptions;
    }

    public ConfigRetrieverOptions initRetrieverConfig(String folderPath) {
        String path = (folderPath == null || folderPath.isEmpty()) ? "config.json" : folderPath;
        return new ConfigRetrieverOptions()
                .addStore(new ConfigStoreOptions()
                        .setType("file")
                        .setOptional(true)
                        .setConfig(new JsonObject().put("path", path)))
                .addStore(new ConfigStoreOptions().setType("sys"));
    }

    private void deployService(JsonObject config, Supplier<Verticle> serviceSupplier,
                               String serviceUnitName, JsonObject serviceUnitConfig) {
        int poolSize = serviceUnitConfig.getInteger("poolSize", 20);
        int instances = serviceUnitConfig.getInteger("instance", 1);
        String type = serviceUnitConfig.getString("type", "W");

        ThreadingModel threadingModel = switch (type.toUpperCase()) {
            case "EL" -> ThreadingModel.EVENT_LOOP;
            case "VT" -> ThreadingModel.VIRTUAL_THREAD;
            default -> ThreadingModel.WORKER;
        };

        vertx.deployVerticle(serviceSupplier, new DeploymentOptions()
                        .setConfig(config)
                        .setWorkerPoolName(serviceUnitName)
                        .setWorkerPoolSize(poolSize)
                        .setInstances(instances)
                        .setThreadingModel(threadingModel))
                .onSuccess(id -> logger.info(serviceUnitName + " successfully deployed (id: " + id + ")"))
                .onFailure(err -> logger.error(serviceUnitName + " deployment failed: " + err.getMessage(), err));
    }

    private void deployHttpService(Supplier<Verticle> serviceSupplier, int instances, int port) {
        logger.info("Initializing HTTP Server on port " + port + " with " + instances + " instance(s)...");
        vertx.deployVerticle(serviceSupplier, new DeploymentOptions()
                        .setConfig(config)
                        .setInstances(instances)
                        .setThreadingModel(ThreadingModel.EVENT_LOOP))
                .onSuccess(id -> logger.info("HTTP server deployed successfully at port [" + port + "]"))
                .onFailure(err -> logger.error("HTTP server deployment failed: " + err.getMessage(), err));
    }

    @Override
    public Vertx getVertx() {
        return localVertx;
    }

    private VertxOptions getVertxOptions() {
        return new VertxOptions()
                .setEventLoopPoolSize(config.getInteger("eventLoopPoolSize", 5))
                .setInternalBlockingPoolSize(config.getInteger("internalBlockingPoolSize", 20))
                .setWorkerPoolSize(config.getInteger("workerPoolSize", 20));
    }

    public Vertx getClusteredVertx() {
        return clusteredVertx;
    }

    public JsonObject getConfig() {
        return config;
    }

    public static void run(Class<? extends BoostApplication> clazz, String[] args) {
        LinkedHashSet<String> params = new LinkedHashSet<>(List.of(
                "run", clazz.getCanonicalName(), "--launcher-class=" + clazz.getCanonicalName()));
        params.addAll(List.of(args));
        new Launcher().dispatch(params.toArray(new String[0]));
    }

    public static void run(Class<? extends BoostApplication> clazz, String[] args,
                           String configPath, boolean isClusteredMode) {
        BoostApplication.configPath = configPath;
        BoostApplication.isClusteredMode = isClusteredMode;
        run(clazz, args);
    }

    public static void run(Class<? extends BoostApplication> clazz, String[] args, String configPath) {
        BoostApplication.configPath = configPath;
        run(clazz, args);
    }

    public static void run(Class<? extends BoostApplication> clazz, String[] args, boolean isClusteredMode) {
        BoostApplication.isClusteredMode = isClusteredMode;
        run(clazz, args);
    }

    public boolean isHasConfig() {
        return hasConfig.get();
    }

    public boolean isMonitorable() {
        return isMonitorable.get();
    }

    public Booster getBooster() {
        return booster;
    }
}
