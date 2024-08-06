package io.github.areebgillani.boost;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

import java.util.concurrent.CountDownLatch;

public class BoostApplication extends AbstractVerticle {
    Logger logger = LoggerFactory.getLogger(BoostApplication.class);
    Vertx vertx;
    JsonObject config;
    Router router;

    @Override
    public void start() throws Exception {
        super.start();
        vertx = Vertx.vertx();
        router = Router.router(vertx);
        loadConfig(null);
        deploy();
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

    public void run() {
        logger.info("Initializing Vertx Application Server...");
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(config().getInteger("http.port", 8080))
                .onSuccess(server -> logger.info(("Server started at port [" + server.actualPort()+"]. ")))
                .onFailure(failed -> logger.info(failed.getMessage()));

    }

    private void deploy() {
        if (config != null) {
            vertx.deployVerticle(this, new DeploymentOptions().setConfig(config))
                    .onSuccess(resp -> {
                        Booster booster = new Booster(vertx, router, config);
                        try {
                            booster.boost(this.getClass().getPackage().getName());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .onFailure(failed -> System.out.println(failed.getMessage()));
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
}
