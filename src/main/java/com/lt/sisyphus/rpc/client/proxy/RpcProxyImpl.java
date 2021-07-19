package com.lt.sisyphus.rpc.client.proxy;

import com.lt.sisyphus.rpc.client.RpcClientHandler;
import com.lt.sisyphus.rpc.client.RpcConnectManager;
import com.lt.sisyphus.rpc.client.RpcFuture;
import com.lt.sisyphus.rpc.codec.RpcRequest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RpcProxyImpl<T> implements InvocationHandler, RpcAsyncProxy {

    private Class<T> clazz;

    private long timeout;

    private RpcConnectManager rpcConnectManager;

    public RpcProxyImpl(Class<T> clazz, long timeout, RpcConnectManager rpcConnectManager) {
        this.clazz = clazz;
        this.timeout = timeout;
        this.rpcConnectManager = rpcConnectManager;
    }


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 1、设置请求对象
        RpcRequest rpcRequest = new RpcRequest();
        rpcRequest.setRequestId(UUID.randomUUID().toString());
        rpcRequest.setClassName(method.getDeclaringClass().getName());
        rpcRequest.setMethod(method.getName());
        rpcRequest.setParamterTypes(method.getParameterTypes());
        rpcRequest.setParamters(args);
        // 2、选择合适的RpcClientHandler
        RpcClientHandler rpcClientHandler = this.rpcConnectManager.chooseHandler();
        // 3、发送请求
        RpcFuture future = rpcClientHandler.sendRequest(rpcRequest);
        // 4、收结果
        return future.get(timeout, TimeUnit.SECONDS);
    }

    @Override
    public RpcFuture call(String funcName, Object... args) {
        // 1、设置请求对象
        RpcRequest rpcRequest = new RpcRequest();
        rpcRequest.setRequestId(UUID.randomUUID().toString());
        rpcRequest.setClassName(this.clazz.getName());
        rpcRequest.setMethod(funcName);
        // TODO 偷懒，应该通过class反射找到具体的方式，获取具体的方法列表
        Class<?>[] paramterTypes = new Class[args.length];
        for (int i  = 0;i < args.length;i++) {
            paramterTypes[i] = getClassType(args[i]);
        }
        rpcRequest.setParamterTypes(paramterTypes);
        rpcRequest.setParamters(args);
        // 2、选择合适的RpcClientHandler
        RpcClientHandler rpcClientHandler = this.rpcConnectManager.chooseHandler();
        // 3、发送请求
        RpcFuture future = rpcClientHandler.sendRequest(rpcRequest);
        return future;
    }

    private Class<?> getClassType(Object obj) {
        Class<?> classType = obj.getClass();
        String typeName = classType.getName();
        if (typeName.equals("java.lang.Integer")) {
            return Integer.TYPE;
        } else if (typeName.equals("java.lang.Long")) {
            return Long.TYPE;
        } else if (typeName.equals("java.lang.Float")) {
            return Float.TYPE;
        } else if (typeName.equals("java.lang.Double")) {
            return Double.TYPE;
        } else if (typeName.equals("java.lang.Character")) {
            return Character.TYPE;
        } else if (typeName.equals("java.lang.Boolean")) {
            return Boolean.TYPE;
        } else if (typeName.equals("java.lang.Short")) {
            return Short.TYPE;
        } else if (typeName.equals("java.lang.Byte")) {
            return Byte.TYPE;
        }
        return classType;
    }
}
