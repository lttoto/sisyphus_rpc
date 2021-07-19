package com.lt.sisyphus.rpc.config.provider;

import com.lt.sisyphus.rpc.config.provider.ProviderConfig;
import com.lt.sisyphus.rpc.registry.RpcRegistryProviderService;
import com.lt.sisyphus.rpc.server.RpcServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/*
* Spring整合中的ServerConfig
* */
@Slf4j
public class RpcSpringExporter implements Serializable {

    private static final long serialVersionUID = -608234247819271106L;

    private String host;

    protected int port;

    private transient volatile boolean exported = false;

    private RpcServer rpcServer = null;

    private RpcRegistryProviderService rpcRegistryProviderService;

    public RpcSpringExporter() {
    }

    public void exporter(List<ProviderConfig> providerConfigs) {
        for (ProviderConfig providerConfig : providerConfigs) {
            exporter(providerConfig);
        }
    }

    public void exporter(ProviderConfig providerConfig) {
        if (rpcServer == null && !exported) {
            try {
                // 若没有host，默认使用本机的ip
                if (!StringUtils.hasText(host)) {
                    host = InetAddress.getLocalHost().getHostAddress();
                }
                String serverAddress = host + ":" + port;
                rpcServer = new RpcServer(serverAddress);
            } catch (InterruptedException | UnknownHostException e) {
                log.error("RpcServerConfig exporter exception: ", e);
            }
            this.exported = true;
        }
        providerConfig.setServerAddress(rpcServer.getServerAddress());
        rpcServer.registerProcessor(providerConfig);

        // 如果设置了RpcRegistry
        if (rpcRegistryProviderService != null) {
            try {
                rpcRegistryProviderService.registry(providerConfig);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public boolean isExported() {
        return exported;
    }

    public RpcRegistryProviderService getRpcRegistryProviderService() {
        return rpcRegistryProviderService;
    }

    public void setRpcRegistryProviderService(RpcRegistryProviderService rpcRegistryProviderService) {
        this.rpcRegistryProviderService = rpcRegistryProviderService;
    }
}
