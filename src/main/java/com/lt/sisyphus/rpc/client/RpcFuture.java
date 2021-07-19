package com.lt.sisyphus.rpc.client;

import com.lt.sisyphus.rpc.codec.RpcRequest;
import com.lt.sisyphus.rpc.codec.RpcResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class RpcFuture implements Future<Object> {

    private RpcRequest rpcRequest;

    private RpcResponse rpcResponse;

    private long startTime;

    private static final long TIME_THRESHOLD = 5000;

    private Sync sync;

    private List<RpcCallback> pendingCallbacks = new ArrayList<RpcCallback>();

    private ReentrantLock callbackLock = new ReentrantLock();

    private ThreadPoolExecutor executor = new ThreadPoolExecutor(16, 16, 60,
            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(65536));

    public RpcFuture(RpcRequest rpcRequest) {
        this.rpcRequest = rpcRequest;
        this.startTime = System.currentTimeMillis();
        this.sync = new Sync();
    }

    /*
    * 实际的回调处理
    * */
    public void done(RpcResponse rpcResponse) {
        this.rpcResponse = rpcResponse;
        boolean isSuccess = sync.release(1);
        if (isSuccess) {
            // 调用回调函数
            invokeCallbacks();
        }
        // 整体rpc耗时
        long costTime = System.currentTimeMillis() - startTime;

        if (TIME_THRESHOLD < costTime) {
            log.warn("the rpc response time is too slow, request id = " + rpcRequest.getRequestId());
        }
    }

    private void invokeCallbacks() {
        callbackLock.lock();
        try {
            for (final RpcCallback rpcCallback : pendingCallbacks) {
                runCallback(rpcCallback);
            }
        } finally {
            callbackLock.unlock();
        }
    }

    private void runCallback(RpcCallback rpcCallback) {
        final RpcResponse response = this.rpcResponse;
        executor.submit(new Runnable() {
            @Override
            public void run() {
                if (response.getThrowable() == null) {
                    rpcCallback.success(response.getResult());
                } else {
                    rpcCallback.failure(response.getThrowable());
                }
            }
        });
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCancelled() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDone() {
        return sync.isDone();
    }

    @Override
    public Object get() throws InterruptedException, ExecutionException {
        sync.acquire(-1);
        if (this.rpcResponse != null) {
            return this.rpcResponse.getResult();
        }
        return null;
    }

    @Override
    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        boolean isSuccess = sync.tryAcquireNanos(-1, unit.toNanos(timeout));
        if (isSuccess) {
            if (this.rpcResponse != null) {
                return this.rpcResponse.getResult();
            } else {
                return null;
            }
        } else {
            throw new RuntimeException("timeout exection requestId:" + this.rpcRequest.getRequestId());
        }
    }

    class Sync extends AbstractQueuedSynchronizer {

        private static final long serialVersionUID = 1801974285278297020L;

        private final int done = 1;

        private final int pending = 0;


        @Override
        protected boolean tryAcquire(int arg) {
            return getState() == done;
        }

        @Override
        protected boolean tryRelease(int arg) {
            if (getState() == pending) {
                if (compareAndSetState(pending, done)) {
                    return true;
                }
            }
            return false;
        }

        public boolean isDone() {
            return getState() == done;
        }
    }

    public RpcFuture addCallback(RpcCallback rpcCallback) {
        callbackLock.lock();
        try {
            if (isDone()) {
                runCallback(rpcCallback);
            } else {
                this.pendingCallbacks.add(rpcCallback);
            }
        } finally {
            callbackLock.unlock();
        }
        return this;
    }
}
