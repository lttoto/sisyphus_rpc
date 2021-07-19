package com.lt.sisyphus.rpc.client;


import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class RpcConnectManager {

    //private static volatile RpcConnectManager RPC_CONNECT_MANAGER = new RpcConnectManager();
    private static final String ADDRESS_SPLIT_REGEX = ",";
    private static final String IP_PORT_SPLIT_REGEX = ":";
    private long connectTimeoutMills = 6000;

    private volatile boolean isRunning = true;

    private AtomicInteger handlerIdx = new AtomicInteger(0);

    // 缓存连接处理器<连接地址，处理器>
    private Map<InetSocketAddress, RpcClientHandler>
            cachedHandlerMap = new ConcurrentHashMap<InetSocketAddress, RpcClientHandler>();

    // 所有的任务执行器列表
    private CopyOnWriteArrayList<RpcClientHandler> connectedHandlerList = new CopyOnWriteArrayList<RpcClientHandler>();

    // 异步提交连接线程池
    private ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(16, 16,
            60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(65536));

    // Netty工作线程
    private EventLoopGroup eventLoopGroup = new NioEventLoopGroup(4);

    // 可重入锁
    private ReentrantLock connectedLock = new ReentrantLock();
    private Condition connectedCondition = connectedLock.newCondition();

    public RpcConnectManager() {}

    /*public static RpcConnectManager getInstance() {
        return RPC_CONNECT_MANAGER;
    }*/

    // 1、解析地址
    // 2、异步连接线程池真正发起连接，连接失败监听，连接成功监听
    // 3、对于连接进来的资源做缓存 updateConnectedServer

    /*
    * 发起连接
    * */
    public void connect(final String serverAddress) {
        // 解析地址
        List<String> allServerAddress = Arrays.asList(serverAddress.split(ADDRESS_SPLIT_REGEX));
        // 缓存Server
        updateConnectedServer(allServerAddress);
    }

    // 更新缓存信息，并异步发起连接
    public void updateConnectedServer(List<String> allServerAddress) {
        // 判空
        if (CollectionUtils.isEmpty(allServerAddress)) {
            log.error("no available server address!!!!");
            // 清除所有的缓存信息
            clearConnected();
        }

        // 连接地址数据结构
        HashSet<InetSocketAddress> addressSet = new HashSet<InetSocketAddress>();
        // 1、循环遍历serverAddress；确定连接地址
        for (String serverAddress : allServerAddress) {
            // 解析具体地址
            String[] array = serverAddress.split(IP_PORT_SPLIT_REGEX);
            if (array.length == 2) {
                String ip = array[0];
                int port = Integer.parseInt(array[1]);
                // 创建远程channel
                final InetSocketAddress remoteAddress = new InetSocketAddress(ip, port);
                // 缓存远程channel
                addressSet.add(remoteAddress);
            }
        }
        // 2、调用建立连接方法；发起远程连接操作
        for (InetSocketAddress serverAddress : addressSet) {
            // 若缓存中不存在对应连接
            if (!cachedHandlerMap.keySet().contains(serverAddress)) {
                // 发起异步连接
                connectAsync(serverAddress);
            }
        }
        // 3、清理缓存中非allServerAddress的地址
        for (RpcClientHandler rpcClientHandler : connectedHandlerList) {
            SocketAddress socketAddress = rpcClientHandler.getServerAddress();
            if (!addressSet.contains(socketAddress)) {
                log.info("remove invalid server node, socketAddress = " + socketAddress);
                RpcClientHandler handler = cachedHandlerMap.get(socketAddress);
                if (handler != null) {
                    handler.close();
                }
                cachedHandlerMap.remove(socketAddress);
                connectedHandlerList.remove(socketAddress);
            }
        }
    }

    /*
    * 异步发起连接
    * */
    private void connectAsync(InetSocketAddress serverAddress) {
        threadPoolExecutor.submit(new Runnable() {
            @Override
            public void run() {
                Bootstrap b = new Bootstrap();
                b.group(eventLoopGroup)
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.TCP_NODELAY, true)
                        .handler(new RpcClientInitializer());
                connect(b, serverAddress);
            }
        });
    }

    /*
    * 真实连接方法
    * */
    private void connect(final Bootstrap b, InetSocketAddress serverAddress) {
        // 1、真正的建立连接
        final ChannelFuture channelFuture = b.connect(serverAddress);

        // 2、连接失败时添加监听 清除资源后进行发起重连操作
        channelFuture.channel().closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                log.info("channelFuture.channel close operationComplete, serverAddress = " + serverAddress);
                future.channel().eventLoop().schedule(new Runnable() {
                    @Override
                    public void run() {
                        log.warn(" connect fail, to reconnect!!! ");
                        clearConnected();
                        connect(b, serverAddress);
                    }
                }, 3, TimeUnit.SECONDS);
            }
        });
        // 3、连接成功的时候添加监听 把真正的连接加入缓存
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    log.info("successfully connect server address, serverAddress = " + serverAddress);
                    RpcClientHandler handler = future.channel().pipeline().get(RpcClientHandler.class);
                    addHandler(handler);
                }
            }
        });
    }

    /*
    * 连接失败时，及时的释放资源，清空缓存
    * */
    private void clearConnected() {
        for (final RpcClientHandler rpcClientHandler : connectedHandlerList) {
            // 通过rpcClientHandler找到对应的InetSocketAddress
            SocketAddress serverAddress = rpcClientHandler.getServerAddress();
            RpcClientHandler handler = cachedHandlerMap.get(serverAddress);
            // 删除缓存handler
            if (handler != null) {
                handler.close();
                cachedHandlerMap.remove(serverAddress);
            }
        }
        connectedHandlerList.clear();
    }

    /*
    * 添加handler到制定的缓存
    * */
    private void addHandler(RpcClientHandler handler) {
        connectedHandlerList.add(handler);
        cachedHandlerMap.put((InetSocketAddress) handler.getChannel().remoteAddress(), handler);
        // 唤醒可用的业务执行器
        signalAvailableHandler();
    }

    /*
    * 唤醒另外一段的线程 告知有新的接入
    * */
    private void signalAvailableHandler() {
        connectedLock.lock();
        try {
            connectedCondition.signalAll();
        } finally {
            connectedLock.unlock();
        }
    }

    /*
    * 等待新连接接入方法
    * */
    private boolean waitingForAvailableHandler() throws InterruptedException {
        connectedLock.lock();
        try {
            return connectedCondition.await(this.connectTimeoutMills, TimeUnit.MILLISECONDS);
        } finally {
            connectedLock.unlock();
        }
    }

    /*
    * 选择一个实际的业务处理器
    * */
    public RpcClientHandler chooseHandler() {

        // 判断现在是否存在可用的连接，若没有，等待至存在可用的连接
        CopyOnWriteArrayList<RpcClientHandler> handlers = (CopyOnWriteArrayList<RpcClientHandler>) this.connectedHandlerList.clone();
        int size = handlers.size();
        while (isRunning && size <= 0 ) {
            try {
                boolean available = waitingForAvailableHandler();
                if (available) {
                    handlers = (CopyOnWriteArrayList<RpcClientHandler>) this.connectedHandlerList.clone();
                    size = handlers.size();
                }
            } catch (InterruptedException e) {
                log.error("waiting for available node is InterruptedException");
                throw new RuntimeException("no connect any server!", e);
            }
        }

        if (!isRunning) {
            // 取模异常的问题
            return null;
        }
        // 选择策略(取模)
        // TODO 后期增加不同的策略
        return handlers.get((handlerIdx.getAndAdd(1) + size) % size);
    }

    /*
    * 关闭操作
    * */
    public void stop() {
        // 标志位
        isRunning = false;
        // 清空连接
        for (RpcClientHandler rpcClientHandler : connectedHandlerList) {
            rpcClientHandler.close();
        }
        // 调用唤醒操作，解决阻塞线程清除
        signalAvailableHandler();
        threadPoolExecutor.shutdown();
        eventLoopGroup.shutdownGracefully();
    }

    /*
    * 重连方法
    * */
    public void reconnect(final RpcClientHandler handler, final SocketAddress socketAddress) {
        // 释放当前资源
        if (handler != null) {
            handler.close();
            connectedHandlerList.remove(handler);
            cachedHandlerMap.remove(socketAddress);
        }
        // 发起重连
        connectAsync((InetSocketAddress) socketAddress);
    }

}
