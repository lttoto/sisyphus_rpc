package com.lt.sisyphus.rpc.client.proxy;

import com.lt.sisyphus.rpc.client.RpcFuture;

public interface RpcAsyncProxy {
    RpcFuture call(String funcName, Object... args);
}
