package com.lt.sisyphus.rpc.client;

import com.lt.sisyphus.rpc.codec.RpcRequest;
import com.lt.sisyphus.rpc.codec.RpcResponse;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.Getter;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class RpcClientHandler extends SimpleChannelInboundHandler<RpcResponse> {

    private Channel channel;
    private SocketAddress serverAddress;

    private Map<String, RpcFuture> pendingRpcResponseTable = new ConcurrentHashMap<String, RpcFuture>();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse msg) throws Exception {
        // 1、获取requestId
        String requestId = msg.getRequestId();
        // 2、获取对应的rpcFuture
        RpcFuture rpcFuture = pendingRpcResponseTable.get(requestId);
        if (rpcFuture != null) {
            pendingRpcResponseTable.remove(requestId);
            // 执行rpc的done方法
            rpcFuture.done(msg);
        }
    }

    /*
    * 通道注册
    * */
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        this.channel = ctx.channel();
    }

    /*
    * 通道激活
    * */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.serverAddress = this.channel.remoteAddress();
    }

    /*
    * Netty提供主动关闭的连接的方法，发送一个EMPTY_BUFFER，Netty收到后就会关闭
    * */
    public void close() {
        channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

    /*
    * 异步发送Rpc请求
    * */
    public RpcFuture sendRequest(RpcRequest request) {
        RpcFuture rpcFuture = new RpcFuture(request);
        pendingRpcResponseTable.put(request.getRequestId(), rpcFuture);
        channel.writeAndFlush(request);
        return rpcFuture;
    }
}
