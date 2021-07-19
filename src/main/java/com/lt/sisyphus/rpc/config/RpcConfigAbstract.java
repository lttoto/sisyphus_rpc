package com.lt.sisyphus.rpc.config;

import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class RpcConfigAbstract  implements Serializable {

    private static final long serialVersionUID = -5297553461587225223L;

    private AtomicInteger generator = new AtomicInteger(0);

    protected String id;

    protected String interfaceClass = null;

    // 服务的调用方（Consumer属性）
    protected Class<?> proxyClass = null;

    public String getId() {
        if (StringUtils.isBlank(id)) {
            id = "sysyphus-cfg-gen-" + generator.getAndIncrement();
        }
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setInterfaceClass(String interfaceClass) {
        this.interfaceClass = interfaceClass;
    }

    public String getInterfaceClass() {
        return this.interfaceClass;
    }
}
