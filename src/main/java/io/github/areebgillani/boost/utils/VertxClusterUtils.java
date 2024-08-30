package io.github.areebgillani.boost.utils;

import io.github.areebgillani.boost.BoosterBus;
import io.github.areebgillani.boost.cache.hazelcast.BoosterHazelcastClusterManager;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class VertxClusterUtils {
    public static Vertx initClusterVertx(JsonObject config, VertxOptions vertxOptions) throws InterruptedException {
        AtomicReference<Vertx> vertx = new AtomicReference<>();
        vertxOptions.setEventBusOptions(BoosterBus.getInstance().getClusterEventBusOptions(config.getJsonObject("cluster").getString("host")));
        vertxOptions.setClusterManager(BoosterHazelcastClusterManager.getInstance().getClusterManager(config));
        CountDownLatch latch = new CountDownLatch(1);
        Vertx.clusteredVertx(vertxOptions, res -> {
            vertx.set(res.succeeded()?res.result():null);
            latch.countDown();
        });
        latch.await();
        return vertx.get();
    }

}
