package io.github.areebgillani.boost.utils;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

import java.util.*;

public class BoosterEventBusUtils {
    private static BoosterEventBusUtils instance;
    private static final Logger logger = LoggerFactory.getLogger(BoosterEventBusUtils.class);
    public static EventBusOptions getClusterEventBusOptions(String hostIp){
        return new EventBusOptions()
                .setClusterPublicHost(hostIp)
                .setHost(hostIp);
    }
    public static void addCodecForClusteredEventBus(EventBus eventBus, Set<Class<?>> typeList){
        addDefaultCodecs(typeList);
        for (Class type : typeList) {
            logger.info("Registering codec: " + type.getName());
            eventBus.registerDefaultCodec(type, new CustomCodec<Object>(type));
        }
    }

    private static void addDefaultCodecs(Set<Class<?>> typeList) {
        typeList.add(LinkedList.class);
        typeList.add(HashMap.class);
        typeList.add(Set.class);
        typeList.add(HashSet.class);
        typeList.add(ArrayList.class);
        typeList.add(Hashtable.class);
        typeList.add(List.class);
    }
}
