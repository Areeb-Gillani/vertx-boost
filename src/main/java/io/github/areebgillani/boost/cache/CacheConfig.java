package io.github.areebgillani.boost.cache;

import io.vertx.core.json.JsonObject;


public interface CacheConfig<T> {
    T getOptions(JsonObject config);
}

