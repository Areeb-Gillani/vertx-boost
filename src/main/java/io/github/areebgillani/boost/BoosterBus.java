package io.github.areebgillani.boost;

import io.github.areebgillani.boost.utils.CustomCodec;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

import java.util.*;

public class BoosterBus {
    private EventBus localEventBus;
    private EventBus clusteredEventBus;
    private static BoosterBus instance;
    private static final Logger logger = LoggerFactory.getLogger(BoosterBus.class);
    private BoosterBus(){
    }
    public static BoosterBus getInstance(){
        if(instance==null)
            instance = new BoosterBus();
        return instance;
    }
    public EventBusOptions getClusterEventBusOptions(String hostIp){
        return new EventBusOptions()
                .setClusterPublicHost(hostIp)
                .setHost(hostIp);
    }
    public void addCodecForClusteredEventBus(EventBus eventBus, Set<Class<?>> typeList){
        addDefaultCodecs(typeList);
        for (Class type : typeList) {
            logger.info("Registering codec: " + type.getName());
            eventBus.registerDefaultCodec(type, new CustomCodec<Object>(type));
        }
    }

    private void addDefaultCodecs(Set<Class<?>> typeList) {
        typeList.add(LinkedList.class);
        typeList.add(HashMap.class);
        typeList.add(Set.class);
        typeList.add(HashSet.class);
        typeList.add(ArrayList.class);
        typeList.add(Hashtable.class);
        typeList.add(List.class);
    }

    public EventBus getLocalEventBus() {
        return localEventBus;
    }

    public EventBus getClusteredEventBus() {
        return clusteredEventBus;
    }
}
