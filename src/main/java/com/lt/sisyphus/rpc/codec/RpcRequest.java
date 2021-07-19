package com.lt.sisyphus.rpc.codec;

import lombok.Data;

import java.io.Serializable;

@Data
public class RpcRequest implements Serializable {
    private static final long serialVersionUID = 1232998106829441483L;

    private String requestId;

    private String className;

    private String method;

    private Class<?>[] paramterTypes;

    private Object[] paramters;
}
