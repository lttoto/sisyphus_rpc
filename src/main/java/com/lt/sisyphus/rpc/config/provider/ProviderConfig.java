package com.lt.sisyphus.rpc.config.provider;

import com.lt.sisyphus.rpc.config.RpcConfigAbstract;

/*
* 接口名称
* 程序对象
* */
public class ProviderConfig extends RpcConfigAbstract {

    private static final long serialVersionUID = -2648277373037741765L;

    protected Object ref;

    protected String serverAddress;

    protected String weight = "1";

    public Object getRef() {
        return ref;
    }

    public void setRef(Object ref) {
        this.ref = ref;
    }


    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public String getWeight() {
        return weight;
    }

    public void setWeight(String weight) {
        this.weight = weight;
    }
}
