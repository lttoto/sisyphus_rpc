package com.lt.sisyphus.rpc.invoke.provider.test;

import com.lt.sisyphus.rpc.config.provider.ProviderConfig;
import com.lt.sisyphus.rpc.config.provider.RpcServerConfig;

import java.util.ArrayList;
import java.util.List;

public class ProviderStarter {
    public static void main(String[] args) {
        // 服务端启动
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 配置ProviderConfig
                    ProviderConfig providerConfig = new ProviderConfig();
                    providerConfig.setInterfaceClass("com.lt.sisyphus.rpc.invoke.consumer.test.HelloService");
                    HelloServiceImpl helloService = HelloServiceImpl.class.newInstance();
                    providerConfig.setRef(helloService);

                    // 把所有的providerConfig加入到集合
                    List<ProviderConfig> providerConfigs = new ArrayList<>();
                    providerConfigs.add(providerConfig);

                    RpcServerConfig rpcServerConfig = new RpcServerConfig(providerConfigs);
                    rpcServerConfig.setPort(8765);
                    rpcServerConfig.exporter();
                } catch (Exception e) {
                    e.getStackTrace();
                }
            }
        }).start();
    }
}
