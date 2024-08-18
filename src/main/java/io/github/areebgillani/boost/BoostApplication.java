package io.github.areebgillani.boost;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Launcher;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class BoostApplication extends AbstractVerticle {
    Logger logger = LoggerFactory.getLogger(BoostApplication.class);
    Vertx vertx;
    JsonObject config;
    Router router;
    protected static BoostApplication instance;
    public static BoostApplication getInstance(){
        return instance;
    }

    @Override
    public void start() throws Exception {
        super.start();
        vertx = Vertx.vertx();
        router = Router.router(vertx);
        instance = this;
    }

    public void init(Vertx vertx, Router router, String configPath) throws InterruptedException {
        this.vertx = vertx;
        this.router = router;
        loadConfig(configPath);
        deploy();
    }
    public void init(Vertx vertx, Router router, JsonObject config) {
        this.vertx = vertx;
        this.router = router;
        this.config = config;
        deploy();
    }
    public void loadConfig(String folderPath) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        ConfigRetriever.create(vertx, initRetrieverConfig(folderPath)).getConfig().onComplete(ar -> {
            if (ar.failed()) {
                latch.countDown();
                logger.error("Error in config loading...!");
            } else {
                config = ar.result();
                latch.countDown();
                logger.info("Config loaded successfully..!");
            }
        });
        latch.await();
    }

    public void run(String folderPath, boolean isRestful) throws InterruptedException {
        init(vertx, router, folderPath);
        if(isRestful) {
            logger.info("Initializing Vertx Application Server...");
            vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(config.getInteger("http.port", 8080))
                    .onSuccess(server -> logger.info(("Server started at port [" + server.actualPort() + "]. ")))
                    .onFailure(failed -> logger.info(failed.getMessage()));
        }

    }
    public static void run(Class<? extends BoostApplication> clazz, String[] args) {
        LinkedHashSet<String> params = new LinkedHashSet<>(List.of(new String[]{"run", clazz.getCanonicalName(), "--launcher-class=" + clazz.getCanonicalName()}));
        params.addAll(List.of(args));
        new Launcher().dispatch(params.toArray(new String[0]));
    }

    private void deploy() {
        if (config != null) {
            Booster booster = new Booster(vertx, router, config);
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
                        .setConfig(new JsonObject().put("path", folderPath==null||folderPath.isEmpty()?"config.json":folderPath)))
                .addStore(new ConfigStoreOptions().setType("sys"));
    }

    @Override
    public Vertx getVertx() {
        return vertx;
    }

    public JsonObject getConfig() {
        return config;
    }
}
