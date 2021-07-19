package com.lt.sisyphus.rpc.config.spring;

import com.lt.sisyphus.rpc.client.RpcClient;
import com.lt.sisyphus.rpc.config.consumer.ConsumerConfig;
import com.lt.sisyphus.rpc.exception.RpcException;
import com.lt.sisyphus.rpc.registry.RegistryManager;
import com.lt.sisyphus.rpc.registry.RpcRegistryConsumerService;
import com.lt.sisyphus.rpc.registry.zookeeper.SisyphusZookeeperRegistryConsumer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;


@Slf4j
@Data
public class SisyphusReferenceBean<T> implements FactoryBean, InitializingBean,
        DisposableBean, ApplicationContextAware, ApplicationListener {

    private String url;

    private RpcClient rpcClient;

    private String registryName;

    private RpcRegistryConsumerService rpcRegistryConsumerService;

    private Class<T> referencedType;

    private ConfigurableListableBeanFactory beanFactory;

    private ApplicationContext applicationContext;

    private Environment environment;

    public SisyphusReferenceBean(String url, Class<T> referencedType, String registryName, Environment environment) {
        this.url = url;
        this.referencedType = referencedType;
        this.registryName = registryName;
        this.environment = environment;
    }

    @Override
    public void destroy() throws Exception {

    }

    public synchronized T get() throws Exception {
        // 判断是否从registry中获取url还是直连
        // 优先级直连 > 注册中心
        if (!StringUtils.hasText(url)) {
            // 创建对应的registryBean
            // TODO 未来支持多中心，现在支持单类中心
            String registryAddressParam = environment.getProperty("sisyphus.registry.address");
            // 判断具体的注册中心类型
            String registryType = registryAddressParam.split("://")[0];
            String registryAddress = registryAddressParam.split("://")[1];
            if (org.apache.commons.lang3.StringUtils.equals(registryType, "zookeeper")) {
                this.rpcRegistryConsumerService =
                        RegistryManager.getRegistryManagerInstance().getRpcRegistryConsumerService("zookeeper");
                if (rpcRegistryConsumerService == null) {
                    SisyphusZookeeperRegistryConsumer sisyphusZookeeperRegistryConsumer = new SisyphusZookeeperRegistryConsumer();
                    sisyphusZookeeperRegistryConsumer.setAddress(registryAddress);
                    sisyphusZookeeperRegistryConsumer.setConnectionTimeout(3000);
                    sisyphusZookeeperRegistryConsumer.init();
                    this.rpcRegistryConsumerService = sisyphusZookeeperRegistryConsumer;
                    sisyphusZookeeperRegistryConsumer.refreshCache(referencedType.getName());
                    RegistryManager.getRegistryManagerInstance()
                            .putRpcRegistryConsumerService("zookeeper", sisyphusZookeeperRegistryConsumer);
                }
            }

            // 获取对应的ConsumerConfig
            ConsumerConfig consumerConfig = rpcRegistryConsumerService.getConsumer(referencedType);
            // 判空
            if (consumerConfig == null) {
                log.error("get provider information in registry error");
                throw new RpcException("get provider information in registry error");
            }
            return consumerConfig.getRpcClient().invokeSync(referencedType);
        } else {
            this.rpcClient = new RpcClient();
            rpcClient.initClient(url, 3000);
            return rpcClient.invokeSync(referencedType);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
    }

    @Override
    public Object getObject() throws Exception {
        return get();
    }

    @Override
    public Class<?> getObjectType() {
        return this.referencedType;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
