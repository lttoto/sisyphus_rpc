package com.lt.sisyphus.rpc.codec;

import lombok.Data;

import java.io.Serializable;

@Data
public class RpcResponse implements Serializable {
    private static final long serialVersionUID = 9104873796323944200L;

    private String requestId;

    private Object result;

    private Throwable throwable;
}
