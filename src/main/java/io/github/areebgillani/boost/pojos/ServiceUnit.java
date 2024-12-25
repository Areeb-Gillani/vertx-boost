package io.github.areebgillani.boost.pojos;

import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;

import java.util.function.Supplier;

public class ServiceUnit {
    JsonObject globalConfig;
    Supplier<Verticle> serviceSupplier;
    String serviceUnitName;
    JsonObject serviceUnitConfig;

    public ServiceUnit(JsonObject globalConfig, Supplier<Verticle> serviceSupplier, String serviceUnitName, JsonObject serviceUnitConfig) {
        this.globalConfig = globalConfig;
        this.serviceSupplier = serviceSupplier;
        this.serviceUnitName = serviceUnitName;
        this.serviceUnitConfig = serviceUnitConfig;
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

    public String getServiceUnitName() {
        return serviceUnitName;
    }

    public void setServiceUnitName(String serviceUnitName) {
        this.serviceUnitName = serviceUnitName;
    }

    public JsonObject getServiceUnitConfig() {
        return serviceUnitConfig;
    }

    public void setServiceUnitConfig(JsonObject serviceUnitConfig) {
        this.serviceUnitConfig = serviceUnitConfig;
    }
}
