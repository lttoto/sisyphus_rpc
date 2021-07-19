package com.lt.sisyphus.rpc.client;

import com.lt.sisyphus.rpc.client.proxy.RpcAsyncProxy;
import com.lt.sisyphus.rpc.client.proxy.RpcProxyImpl;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RpcClient {

    private String serverAddress;

    private long timeout;

    private final Map<Class<?>, Object> syncProxyInstanceMap = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> asyncProxyInstanceMap = new ConcurrentHashMap<>();

    private RpcConnectManager rpcConnectManager;

    public void initClient(String serverAddress, long timeout) {
        this.serverAddress = serverAddress;
        this.timeout = timeout;
        this.rpcConnectManager = new RpcConnectManager();
        connect();
    }

    private void connect() {
        this.rpcConnectManager.connect(serverAddress);
    }

    private void stop() {
        this.rpcConnectManager.stop();
    }

    public void updateConnectedServer(List<String> serverAddress) {
        this.rpcConnectManager.updateConnectedServer(serverAddress);
    }

    /*
    * 同步代理
    * */
    public <T> T invokeSync(Class<T> interfaceClass) {
        if (syncProxyInstanceMap.containsKey(interfaceClass)) {
            return (T) syncProxyInstanceMap.get(interfaceClass);
        } else {
            Object proxy = Proxy.newProxyInstance(interfaceClass.getClassLoader(),
                    new Class<?>[]{interfaceClass},
                    new RpcProxyImpl<>(interfaceClass, timeout, rpcConnectManager));
            syncProxyInstanceMap.put(interfaceClass, proxy);
            return (T) proxy;
        }
    }

    /*
    * 异步代理
    * */
    public <T> RpcAsyncProxy invokeASync(Class<T> interfaceClass) {
        if (asyncProxyInstanceMap.containsKey(interfaceClass)) {
            return (RpcAsyncProxy) asyncProxyInstanceMap.get(interfaceClass);
        } else {
            RpcProxyImpl<T> asyncProxyImpl = new RpcProxyImpl<>(interfaceClass, timeout, rpcConnectManager);
            asyncProxyInstanceMap.put(interfaceClass, asyncProxyImpl);
            return asyncProxyImpl;
        }
    }
}
