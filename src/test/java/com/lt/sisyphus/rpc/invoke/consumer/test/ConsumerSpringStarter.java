package com.lt.sisyphus.rpc.invoke.consumer.test;

import com.lt.sisyphus.rpc.config.spring.context.annotation.EnableSisyphus;
import com.lt.sisyphus.rpc.registry.RpcRegistryConsumerService;
import com.lt.sisyphus.rpc.registry.zookeeper.SisyphusZookeeperRegistryConsumer;
import lombok.Data;
import org.springframework.context.annotation.*;

import java.io.IOException;

public class ConsumerSpringStarter {
    public static void main(String[] args) throws IOException {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ConsumerSpringStarter.SisyphusConfig.class);
        context.start();
        ConsumerDemoImpl consumerDemo = context.getBean(ConsumerDemoImpl.class);
        System.out.println(consumerDemo.test());
        System.out.println(consumerDemo.test1());
        System.in.read();
    }

    @Configuration
    @EnableSisyphus(scanBasePackages = "com.lt.sisyphus.rpc.invoke.consumer.test")
    @ComponentScan(basePackages = "com.lt.sisyphus.rpc.invoke.consumer.test")
    @PropertySource("classpath:sisyphus-consumer.properties")
    static class SisyphusConfig {

        /*@Bean
        public RpcRegistryConsumerService rpcRegistryConsumerService() throws Exception {
            SisyphusZookeeperRegistryConsumer sisyphusZookeeperRegistryConsumer = new SisyphusZookeeperRegistryConsumer();
            sisyphusZookeeperRegistryConsumer.setAddress("127.0.0.1:2181");
            sisyphusZookeeperRegistryConsumer.init();
            return sisyphusZookeeperRegistryConsumer;
        }*/
    }
}
