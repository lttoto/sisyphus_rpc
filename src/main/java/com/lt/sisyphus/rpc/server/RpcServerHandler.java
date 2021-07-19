package com.lt.sisyphus.rpc.server;

import com.lt.sisyphus.rpc.codec.RpcRequest;
import com.lt.sisyphus.rpc.codec.RpcResponse;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastMethod;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RpcServerHandler extends SimpleChannelInboundHandler<RpcRequest> {

    public Map<String, Object> handlerMap;

    private ThreadPoolExecutor executor = new ThreadPoolExecutor(16, 16, 600L,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(65536));

    public RpcServerHandler(Map<String, Object> handlerMap) {
        this.handlerMap = handlerMap;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest msg) throws Exception {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                RpcResponse rpcResponse = new RpcResponse();
                rpcResponse.setRequestId(msg.getRequestId());
                try {
                    Object result = handler(msg);
                    rpcResponse.setResult(result);
                } catch (Throwable t) {
                    rpcResponse.setThrowable(t);
                    log.error("rpc server handle request Throwable: " + t);
                }
                ctx.writeAndFlush(rpcResponse).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            // afterRpcHook

                        }
                    }
                });
            }
        });
    }

    private Object handler(RpcRequest request) throws InvocationTargetException {
        // 1、解析rpcRequest
        String className = request.getClassName();
        // 2、从handlerMap中获取对应的bean
        Object serviceRef = handlerMap.get(className);
        Class<?> serviceClass = serviceRef.getClass();
        String methodName = request.getMethod();
        Class<?>[] paramterTypes = request.getParamterTypes();
        Object[] paramters = request.getParamters();
        // 3、反射，执行逻辑
        // TODO JDK reflect
        // Cglib
        FastClass serviceFastClass = FastClass.create(serviceClass);
        FastMethod serviceFastMethod = serviceFastClass.getMethod(methodName, paramterTypes);
        // 4、返回结果
        return serviceFastMethod.invoke(serviceRef, paramters);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //super.exceptionCaught(ctx, cause);
        log.error("server caught Throwable : " + cause);
        ctx.close();
    }
}
