package io.github.areebgillani.boost.pojos;

import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;

import java.util.function.Supplier;

public class ServiceWorker {
    JsonObject globalConfig;
    Supplier<Verticle> serviceSupplier;
    String workerName;
    JsonObject workerConfig;

    public ServiceWorker(JsonObject globalConfig, Supplier<Verticle> serviceSupplier, String workerName, JsonObject workerConfig) {
        this.globalConfig = globalConfig;
        this.serviceSupplier = serviceSupplier;
        this.workerName = workerName;
        this.workerConfig = workerConfig;
    }

    public JsonObject getGlobalConfig() {
        return globalConfig;
    }

    public void setGlobalConfig(JsonObject globalConfig) {
        this.globalConfig = globalConfig;
    }

    public Supplier<Verticle> getServiceSupplier() {
        return serviceSupplier;
    }

    public void setServiceSupplier(Supplier<Verticle> serviceSupplier) {
        this.serviceSupplier = serviceSupplier;
    }

    public String getWorkerName() {
        return workerName;
    }

    public void setWorkerName(String workerName) {
        this.workerName = workerName;
    }

    public JsonObject getWorkerConfig() {
        return workerConfig;
    }

    public void setWorkerConfig(JsonObject workerConfig) {
        this.workerConfig = workerConfig;
    }
}
