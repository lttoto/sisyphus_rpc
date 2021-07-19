package com.lt.sisyphus.rpc.registry.zookeeper.client;

/**
 * 	ConnectionState
 *	客户端连接zookeeper服务器的连接状态
 */
public enum ConnectionState {
    CONNECTED, SUSPENDED, RECONNECTED, LOST, READ_ONLY;
}
