package io.github.areebgillani.boost.utils;

import io.github.areebgillani.boost.cache.hazelcast.BoosterHazelcastClusterManager;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;

/**
 * Utility class for initializing clustered Vert.x instances.
 */
public class VertxClusterUtils {
    private static final Logger logger = LoggerFactory.getLogger(VertxClusterUtils.class);

    private VertxClusterUtils() {
        // Private constructor
    }

    /**
     * Initializes a clustered Vert.x instance.
     *
     * @param config Application configuration
     * @param vertxOptions Vert.x options to use
     * @return Clustered Vertx instance, or null if initialization fails
     */
    public static Vertx initClusterVertx(JsonObject config, VertxOptions vertxOptions) {
        JsonObject clusterConfig = config.getJsonObject("cluster");
        if (clusterConfig == null) {
            logger.warn("No 'cluster' configuration found - skipping cluster initialization");
            return null;
        }

        String host = clusterConfig.getString("host");
        if (host == null || host.isEmpty()) {
            logger.error("Cluster 'host' is required in configuration");
            return null;
        }

        try {
            vertxOptions.setEventBusOptions(BoosterEventBusUtils.getClusterEventBusOptions(host));
            vertxOptions.setClusterManager(BoosterHazelcastClusterManager.getInstance().getClusterManager(config));

            // Use CompletableFuture for synchronous initialization
            return Vertx.clusteredVertx(vertxOptions)
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get();

        } catch (Exception e) {
            logger.error("Failed to initialize clustered Vert.x: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Initializes a clustered Vert.x instance asynchronously.
     *
     * @param config Application configuration
     * @param vertxOptions Vert.x options to use
     * @return Future containing the clustered Vertx instance
     */
    public static Future<Vertx> initClusterVertxAsync(JsonObject config, VertxOptions vertxOptions) {
        Promise<Vertx> promise = Promise.promise();

        JsonObject clusterConfig = config.getJsonObject("cluster");
        if (clusterConfig == null) {
            promise.fail("No 'cluster' configuration found");
            return promise.future();
        }

        String host = clusterConfig.getString("host");
        if (host == null || host.isEmpty()) {
            promise.fail("Cluster 'host' is required in configuration");
            return promise.future();
        }

        try {
            vertxOptions.setEventBusOptions(BoosterEventBusUtils.getClusterEventBusOptions(host));
            vertxOptions.setClusterManager(BoosterHazelcastClusterManager.getInstance().getClusterManager(config));

            Vertx.clusteredVertx(vertxOptions)
                    .onSuccess(vertx -> {
                        logger.info("Clustered Vert.x initialized successfully");
                        promise.complete(vertx);
                    })
                    .onFailure(err -> {
                        logger.error("Failed to initialize clustered Vert.x", err);
                        promise.fail(err);
                    });

        } catch (Exception e) {
            logger.error("Error setting up cluster options", e);
            promise.fail(e);
        }

        return promise.future();
    }
}
