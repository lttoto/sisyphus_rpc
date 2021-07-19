package com.lt.sisyphus.rpc.server;

import com.lt.sisyphus.rpc.codec.RpcDecoder;
import com.lt.sisyphus.rpc.codec.RpcEncoder;
import com.lt.sisyphus.rpc.codec.RpcRequest;
import com.lt.sisyphus.rpc.codec.RpcResponse;
import com.lt.sisyphus.rpc.config.provider.ProviderConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RpcServer {

    private String serverAddress;

    private EventLoopGroup bossGroup = new NioEventLoopGroup();
    private EventLoopGroup workGroup = new NioEventLoopGroup();

    private volatile Map<String, Object> handlerMap = new HashMap<String, Object>();

    public RpcServer(String serverAddress) throws InterruptedException {
        this.serverAddress = serverAddress;
        this.start();
    }

    public void start() throws InterruptedException {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline cp = ch.pipeline();
                        // 编解码Handler
                        cp.addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4,
                                0, 0));
                        cp.addLast(new RpcDecoder(RpcRequest.class));
                        cp.addLast(new RpcEncoder(RpcResponse.class));
                        // 添加实际的业务处理器RpcClientHandler
                        cp.addLast(new RpcServerHandler(handlerMap));
                    }
                });

        String[] array = serverAddress.split(":");
        String host = array[0];
        int port = Integer.parseInt(array[1]);

        ChannelFuture channelFuture = serverBootstrap.bind(host, port).sync();
        // 异步
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    log.info("server success bing to " + serverAddress);
                } else {
                    log.info("server fail bing to " + serverAddress);
                    throw new Exception("server start fail, cause:" + future.cause());
                }
            }
        });

        // 同步 TODO 与异步二选一（未来成为配置项）
        try {
            channelFuture.await(5000, TimeUnit.MILLISECONDS);
            if (channelFuture.isSuccess()) {
                log.info("start sysphus rpc success bing to " + serverAddress);
            }
        } catch (InterruptedException e) {
            log.error("start sysphus rpc occur Interrupted ex" + e);
        }

    }

    public void registerProcessor(ProviderConfig providerConfig) {
        handlerMap.put(providerConfig.getInterfaceClass(), providerConfig.getRef());
    }

    public void close() {
        bossGroup.shutdownGracefully();
        workGroup.shutdownGracefully();
    }

    public String getServerAddress() {
        return serverAddress;
    }
}
