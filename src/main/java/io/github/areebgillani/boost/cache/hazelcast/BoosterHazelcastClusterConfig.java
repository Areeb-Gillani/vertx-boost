package io.github.areebgillani.boost.cache.hazelcast;


import com.hazelcast.config.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.github.areebgillani.boost.cache.CacheConfig;

public class BoosterHazelcastClusterConfig implements CacheConfig<Config> {
    private static BoosterHazelcastClusterConfig instance;
    private JsonObject clusterConfig;
    private Config hazelcastConfig;
    public static BoosterHazelcastClusterConfig getInstance(){
        if(instance==null)
            instance = new BoosterHazelcastClusterConfig();
        return instance;
    }
    @Override
    public Config getOptions(JsonObject config) {
        this.clusterConfig = config.getJsonObject("cluster");
        if(clusterConfig!=null&&!clusterConfig.isEmpty()) {
            hazelcastConfig = new Config();
            hazelcastConfig.getCPSubsystemConfig().setCPMemberCount(clusterConfig.getInteger("cpMemberCount"));
            setNetworkConfig();
            hazelcastConfig.getManagementCenterConfig().setScriptingEnabled(true);
        }
        return hazelcastConfig;
    }

    private void setNetworkConfig() {
        NetworkConfig network = hazelcastConfig.getNetworkConfig();
        network.setJoin(setHazelcastNetwork(network));
        hazelcastConfig.setNetworkConfig(network);
        hazelcastConfig.getQueueConfig("configs").setBackupCount(0).setAsyncBackupCount(0);
    }

    private JoinConfig setHazelcastNetwork(NetworkConfig networkConfig) {
        JoinConfig networkToJoin = networkConfig.getJoin();
        networkToJoin.getMulticastConfig().setEnabled(clusterConfig.getBoolean("multicast"));
        switch (clusterConfig.getString("type")){
            case "TCP" -> networkToJoin.setTcpIpConfig(setNetworkMembers(networkToJoin));
            case "AWS" -> setAWSConfig(networkToJoin);
            case "AZURE" -> setAzureConfig(networkToJoin);
        }
        return networkToJoin;
    }

    private void setAzureConfig(JoinConfig networkToJoin) {
        //TODO implementation for Azure
    }

    private void setAWSConfig(JoinConfig networkToJoin) {
        //TODO implementation for AWS
    }

    private TcpIpConfig setNetworkMembers(JoinConfig networkToJoin) {
        TcpIpConfig networkConfig = networkToJoin.getTcpIpConfig();
        JsonArray ips = clusterConfig.getJsonArray("ips");
        for (Object ip : ips) {
            networkConfig.addMember((String) ip).setEnabled(true);
        }
        return networkConfig;
    }

}
