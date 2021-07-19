package com.lt.sisyphus.rpc.config.consumer;

import com.lt.sisyphus.rpc.client.RpcClient;
import com.lt.sisyphus.rpc.config.RpcConfigAbstract;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Data
public class ConsumerConfig<T> extends RpcConfigAbstract {

    protected volatile List<String> urls;

    protected RpcClient rpcClient;

    private volatile transient T proxyInstance;

    public void initRpcClient() {
        this.rpcClient = new RpcClient();
        urls.forEach(url -> {
            this.rpcClient.initClient(url, 3000);
        });
    }

    protected Class<?> getProxyClass() {
        if (proxyClass != null) {
            return proxyClass;
        }
        try {
            if (StringUtils.isNotBlank(interfaceClass)) {
                this.proxyClass = Class.forName(interfaceClass);
            } else {
                throw new Exception("consumer.interfaceId, null, interfaceId must be not null");
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return proxyClass;
    }
}
