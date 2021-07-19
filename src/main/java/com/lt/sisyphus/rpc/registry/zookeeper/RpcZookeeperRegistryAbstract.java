package com.lt.sisyphus.rpc.registry.zookeeper;

/*
* 定义一些基本的zookeeper注册中心的目录
* */
public abstract class RpcZookeeperRegistryAbstract {
    protected final String ROOT_PATH = "/sisyphus-rpc";
    protected final String ROOT_VALUE = "sisyphus-1.0.0";
    protected final String PROVIDERS_PATH = "/providers";
    protected final String CONSUMERS_PATH = "/consumers";
}
