package com.lt.sisyphus.rpc.registry;

import com.lt.sisyphus.rpc.config.provider.ProviderConfig;

public interface RpcRegistryProviderService {

    // 向注册中心注册服务
    public void registry(ProviderConfig providerConfig) throws Exception;
}
