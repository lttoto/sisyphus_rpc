package com.lt.sisyphus.rpc.invoke.provider.test;

import com.lt.sisyphus.rpc.config.provider.RpcSpringExporter;
import com.lt.sisyphus.rpc.config.spring.context.annotation.EnableSisyphus;
import com.lt.sisyphus.rpc.registry.RpcRegistryProviderService;
import com.lt.sisyphus.rpc.registry.zookeeper.SisyphusZookeeperRegistryProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

public class ProviderSpringStarter {
    public static void main(String[] args) throws IOException {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SisyphusConfig.class);
        context.start();
        ProviderDemoImpl providerDemo = context.getBean(ProviderDemoImpl.class);
        System.out.println(providerDemo.test());
        System.in.read();
    }

    @Configuration
    @EnableSisyphus(scanBasePackages = "com.lt.sisyphus.rpc.invoke.provider.test")
    @ComponentScan(basePackages = "com.lt.sisyphus.rpc.invoke.provider.test")
    static class SisyphusConfig {

        @Bean
        public RpcRegistryProviderService rpcRegistryProviderService() throws Exception {
            SisyphusZookeeperRegistryProvider sisyphusZookeeperRegistryProvider = new SisyphusZookeeperRegistryProvider();
            sisyphusZookeeperRegistryProvider.setAddress("127.0.0.1:2181");
            sisyphusZookeeperRegistryProvider.setConnectionTimeout(3000);
            sisyphusZookeeperRegistryProvider.init();
            return sisyphusZookeeperRegistryProvider;
        }
        @Bean
        public RpcSpringExporter rpcSpringExporter(RpcRegistryProviderService rpcRegistryProviderService) {
            RpcSpringExporter rpcSpringExporter = new RpcSpringExporter();
            rpcSpringExporter.setPort(8765);
            rpcSpringExporter.setRpcRegistryProviderService(rpcRegistryProviderService);
            return rpcSpringExporter;
        }
    }
}
