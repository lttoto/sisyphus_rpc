package com.lt.sisyphus.rpc.config.provider;

import com.lt.sisyphus.rpc.config.annotation.SisyphusService;
import com.lt.sisyphus.rpc.server.RpcServer;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.List;

/*
* 服务器端启动配置类
* */
@Slf4j
public class RpcServerConfig implements Serializable {

    private static final long serialVersionUID = -37687995357681959L;

    private final String host = "127.0.0.1";

    protected int port;

    private List<ProviderConfig> providerConfigs;

    private RpcServer rpcServer = null;

    public RpcServerConfig(List<ProviderConfig> providerConfigs) {
        this.providerConfigs = providerConfigs;
    }

    public RpcServerConfig() {
    }

    public void exporter() {
        if (rpcServer == null) {
            try {
                rpcServer = new RpcServer(host + ":" + port);
            } catch (InterruptedException e) {
                log.error("RpcServerConfig exporter exception: ", e);
            }

            for (ProviderConfig providerConfig : providerConfigs) {
                rpcServer.registerProcessor(providerConfig);
            }
        }
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public List<ProviderConfig> getProviderConfigs() {
        return providerConfigs;
    }

    public void setProviderConfigs(List<ProviderConfig> providerConfigs) {
        this.providerConfigs = providerConfigs;
    }
}
