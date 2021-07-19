package com.lt.sisyphus.rpc.registry;

import com.lt.sisyphus.rpc.client.RpcClient;
import com.lt.sisyphus.rpc.config.consumer.ConsumerConfig;

public interface RpcRegistryConsumerService {

    public <T> ConsumerConfig getConsumer(Class<T> Class);
}
