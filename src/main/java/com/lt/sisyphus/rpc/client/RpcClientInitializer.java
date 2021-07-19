package com.lt.sisyphus.rpc.client;

import com.lt.sisyphus.rpc.codec.RpcDecoder;
import com.lt.sisyphus.rpc.codec.RpcEncoder;
import com.lt.sisyphus.rpc.codec.RpcRequest;
import com.lt.sisyphus.rpc.codec.RpcResponse;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

public class RpcClientInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {

        ChannelPipeline cp = ch.pipeline();
        // 编解码Handler
        cp.addLast(new RpcEncoder(RpcRequest.class));
        cp.addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 0));
        cp.addLast(new RpcDecoder(RpcResponse.class));
        // 添加实际的业务处理器RpcClientHandler
        cp.addLast(new RpcClientHandler());
    }
}
