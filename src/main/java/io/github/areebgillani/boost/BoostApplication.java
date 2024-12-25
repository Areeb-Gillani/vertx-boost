package io.github.areebgillani.boost;

import io.github.areebgillani.boost.pojos.EndPointController;
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
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

public class BoostApplication extends AbstractVerticle {
    Logger logger = LoggerFactory.getLogger(this.getClass());
    private Vertx localVertx;
    private Vertx clusteredVertx;
    private JsonObject config;
    protected static BoostApplication instance;
    private static boolean isClusteredMode = false;
    private static String configPath = "config.json";
    private boolean hasConfig = false;
    private boolean isMonitorable = false;
    private HashMap<String, List<EndPointController>> controllers;
    private Booster booster;
    public static BoostApplication getInstance() {
        return instance;
    }
    private final Promise<BoostApplication> promise = Promise.promise();
    public static boolean printRoutes=true;

    @Override
    public void start() throws Exception {
        super.start();
        localVertx = Vertx.vertx();
        instance = this;
        localVertx.executeBlocking(()->{
            deployApplication(configPath, isClusteredMode);
            return promise;
        });
    }

    public void init(Vertx vertx, String configPath) throws InterruptedException {
        this.localVertx = vertx;
        loadConfig(configPath);
        booster = new Booster(localVertx, config);
        hasConfig = true;
    }

    public void init(Vertx vertx, JsonObject config) {
        this.localVertx = vertx;
        this.config = config;
    }

    private void loadConfig(String folderPath) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        ConfigRetriever.create(localVertx, initRetrieverConfig(folderPath)).getConfig().onComplete(ar -> {
            if (ar.failed()) {
                latch.countDown();
                logger.error("Error in config loading!");
            } else {
                config = ar.result();
                latch.countDown();
                logger.info("Config loaded successfully!");
            }
        });
        latch.await();
    }

    public void deployApplication(String folderPath, Boolean isClustered) throws Exception {
        init(localVertx, folderPath);
        VertxOptions vertxOptions = enableMonitoringOption(getVertxOptions());
        localVertx = Vertx.vertx(vertxOptions);
        booster.boost(this.getClass().getPackage().getName());
        if(isClustered)
            clusteredVertx = VertxClusterUtils.initClusterVertx(config, vertxOptions);
        booster.getServiceWorkerList().forEach(service->{
            try {
                deployServices(service.getGlobalConfig(), service.getServiceSupplier(), service.getWorkerName(), service.getWorkerConfig());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        deployHTTPServer();
        promise.complete(this);
    }

    private void deployHTTPServer() {
        if (config.containsKey("server")) {
            JsonObject serverConfig = config.getJsonObject("server");
            if (serverConfig.containsKey("http")) {
                JsonObject httpConfig = serverConfig.getJsonObject("http");
                if(httpConfig.getBoolean("enable", true)) {
                    Supplier<Verticle> httpServer = HttpServerVerticle::new;
                    deployHttpService(httpServer, httpConfig.getInteger("instance", 1), httpConfig.getInteger("port", 8080));
                }
            }
        }
    }

    private VertxOptions enableMonitoringOption(VertxOptions vertxOptions) {
        JsonObject metricsConfig = config.getJsonObject("metrics");
        if(metricsConfig!=null && metricsConfig.getBoolean("enabled")){
            MicrometerMetricsOptions metricsOptions = new MicrometerMetricsOptions()
                    .setEnabled(true);
            if(metricsConfig.getString("tool").equals("prometheus")){
                metricsOptions.setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true));
                isMonitorable = true;
            }
            vertxOptions.setMetricsOptions(metricsOptions);
        }
        return vertxOptions;
    }

    public ConfigRetrieverOptions initRetrieverConfig(String folderPath) {
        return new ConfigRetrieverOptions()
                .addStore(new ConfigStoreOptions()
                        .setType("file")
                        .setOptional(true)
                        .setConfig(new JsonObject().put("path", folderPath == null || folderPath.isEmpty() ? "config.json" : folderPath)))
                .addStore(new ConfigStoreOptions().setType("sys"));
    }
    private void deployServices(JsonObject config, Supplier<Verticle> serviceSupplier, String workerName, JsonObject workerConfig) {
        vertx.deployVerticle(serviceSupplier, new DeploymentOptions()
                .setConfig(config)
                .setWorkerPoolName(workerName)
                .setWorkerPoolSize(workerConfig.getInteger("poolSize", 20))
                .setInstances(workerConfig.getInteger("instance", 5))
                .setThreadingModel(switch (workerConfig.getString("type", "W")) {
                    case "EL": yield ThreadingModel.EVENT_LOOP;
                    case "VT": yield ThreadingModel.VIRTUAL_THREAD;
                    default: yield ThreadingModel.WORKER;
                }), res -> {
            if (res.succeeded())
                logger.info(workerName+" successfully deployed.");
            else
                logger.error(workerName+" deployment failed." + res.cause());
        });
    }
    private void deployHttpService( Supplier<Verticle> serviceSupplier, int instances, int port) {
        logger.info("Initializing Application Server...");
        vertx.deployVerticle(serviceSupplier, new DeploymentOptions()
                .setConfig(config)
                .setInstances(instances)
                .setThreadingModel(ThreadingModel.EVENT_LOOP), res -> {
            if (res.succeeded())
                logger.info("HTTP service instance successfully deployed at port ["+port+"]");
            else
                logger.error("HTTP service instance deployment failed." + res.cause());
        });
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
        LinkedHashSet<String> params = new LinkedHashSet<>(List.of(new String[]{"run", clazz.getCanonicalName(), "--launcher-class=" + clazz.getCanonicalName()}));
        params.addAll(List.of(args));
        new Launcher().dispatch(params.toArray(new String[0]));
    }
    public static void run(Class<? extends BoostApplication> clazz, String[] args, String configPath, boolean isClusteredMode) {
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
        return hasConfig;
    }

    public boolean isMonitorable() {
        return isMonitorable;
    }

    public Booster getBooster() {
        return booster;
    }
}
