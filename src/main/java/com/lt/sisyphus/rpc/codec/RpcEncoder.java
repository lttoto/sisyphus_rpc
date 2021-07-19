package com.lt.sisyphus.rpc.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/*
* 编码器
* */
public class RpcEncoder extends MessageToByteEncoder<Object> {

    private Class<?> genericClass;

    public RpcEncoder(Class<?> genericClass) {
        this.genericClass = genericClass;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        if (genericClass.isInstance(msg)) {
            byte[] data = Serialization.serialize(msg);
            // 消息头
            // TODO 可以添加其他信息
            out.writeInt(data.length);
            // 消息体
            out.writeBytes(data);
        }
    }
}
