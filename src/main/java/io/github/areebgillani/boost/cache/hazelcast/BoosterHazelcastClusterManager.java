package io.github.areebgillani.boost.cache.hazelcast;


import io.vertx.core.json.JsonObject;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class BoosterHazelcastClusterManager {
    private static BoosterHazelcastClusterManager instance;
    private HazelcastClusterManager clusterManager;
    public static BoosterHazelcastClusterManager getInstance(){
        if(instance==null)
            instance = new BoosterHazelcastClusterManager();
        return instance;
    }
    public HazelcastClusterManager getClusterManager(JsonObject config){
        if(clusterManager==null)
            clusterManager = new HazelcastClusterManager(BoosterHazelcastClusterConfig.getInstance().getOptions(config));
        return clusterManager;
    }
}
