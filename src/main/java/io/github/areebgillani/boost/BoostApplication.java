package io.github.areebgillani.boost;

import io.github.areebgillani.boost.utils.VertxClusterUtils;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Launcher;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SSLOptions;
import io.vertx.ext.web.Router;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.PrometheusScrapingHandler;
import io.vertx.micrometer.VertxPrometheusOptions;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class BoostApplication extends AbstractVerticle {
    Logger logger = LoggerFactory.getLogger(BoostApplication.class);
    private Vertx localVertx;
    private Vertx clusteredVertx;
    private JsonObject config;
    private Router router;
    protected static BoostApplication instance;

    public static BoostApplication getInstance() {
        return instance;
    }

    @Override
    public void start() throws Exception {
        super.start();
        localVertx = Vertx.vertx();
        router = Router.router(localVertx);
        instance = this;
    }

    public void init(Vertx vertx, Router router, String configPath) throws InterruptedException {
        this.localVertx = vertx;
        this.router = router;
        loadConfig(configPath);
    }

    public void init(Vertx vertx, Router router, JsonObject config) {
        this.localVertx = vertx;
        this.router = router;
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

    public void deployApplication(String folderPath, Boolean isClustered) throws InterruptedException {
        init(localVertx, router, folderPath);
        VertxOptions vertxOptions = enableMonitoringOption(getVertxOptions());
        localVertx = Vertx.vertx(vertxOptions);
        if(isClustered)
            clusteredVertx = VertxClusterUtils.initClusterVertx(config, vertxOptions);
        deploy(isClustered);
        if (config.containsKey("server")) {
            JsonObject serverConfig = config.getJsonObject("server");
            if (serverConfig.containsKey("http")) {
                JsonObject httpConfig = serverConfig.getJsonObject("http");
                if(httpConfig.getBoolean("enable", true)) {
                    logger.info("Initializing Vertx Application Server...");
                    HttpServer httpServer = localVertx.createHttpServer().requestHandler(router);
                    if(httpConfig.containsKey("SSL"))
                        httpServer.updateSSLOptions(new SSLOptions(httpConfig.getJsonObject("SSL")));
                    httpServer.listen(httpConfig.getInteger("port", 8080))
                            .onSuccess(server -> logger.info(("Server started at port [" + server.actualPort() + "]. ")))
                            .onFailure(failed -> logger.info(failed.getMessage()));
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
                router.route("/metrics").handler(PrometheusScrapingHandler.create());
            }
            vertxOptions.setMetricsOptions(metricsOptions);
        }
        return vertxOptions;
    }

    public static void run(Class<? extends BoostApplication> clazz, String[] args) {
        LinkedHashSet<String> params = new LinkedHashSet<>(List.of(new String[]{"run", clazz.getCanonicalName(), "--launcher-class=" + clazz.getCanonicalName()}));
        params.addAll(List.of(args));
        new Launcher().dispatch(params.toArray(new String[0]));
    }

    private void deploy(boolean isClustered) {
        if (config != null) {
            Booster booster = new Booster(localVertx, router, config);
            try {
                booster.boost(this.getClass().getPackage().getName());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else
            logger.info("Deployment config not found.");
    }

    public ConfigRetrieverOptions initRetrieverConfig(String folderPath) {
        return new ConfigRetrieverOptions()
                .addStore(new ConfigStoreOptions()
                        .setType("file")
                        .setOptional(true)
                        .setConfig(new JsonObject().put("path", folderPath == null || folderPath.isEmpty() ? "config.json" : folderPath)))
                .addStore(new ConfigStoreOptions().setType("sys"));
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
}
