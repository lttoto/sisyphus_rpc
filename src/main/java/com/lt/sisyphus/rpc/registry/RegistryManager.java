package com.lt.sisyphus.rpc.registry;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class RegistryManager {


    private static volatile RegistryManager RPC_REGISTRY_MANAGER = new RegistryManager();

    private static Map<String, RpcRegistryConsumerService> registryConsumerMap = new HashMap<>();

    public RegistryManager() {

    }

    public static RegistryManager getRegistryManagerInstance() {
        return RPC_REGISTRY_MANAGER;
    }

    public void putRpcRegistryConsumerService(String name, RpcRegistryConsumerService rpcRegistryConsumerService) {
        RPC_REGISTRY_MANAGER.getRegistryConsumerMap().put(name, rpcRegistryConsumerService);
    }

    public RpcRegistryConsumerService getRpcRegistryConsumerService(String name) {
        return RPC_REGISTRY_MANAGER.getRegistryConsumerMap().get(name);
    }

    public Map<String, RpcRegistryConsumerService> getRegistryConsumerMap() {
        return registryConsumerMap;
    }

}
