package com.lt.sisyphus.rpc.invoke.provider.test;

import com.lt.sisyphus.rpc.config.annotation.SisyphusService;
import com.lt.sisyphus.rpc.invoke.consumer.test.HelloService;
import com.lt.sisyphus.rpc.invoke.consumer.test.User;

@SisyphusService
public class HelloServiceImpl implements HelloService {
    @Override
    public String hello(String name) {
        return "hello!" + name;
    }

    @Override
    public String hello(User user) {
        return "hello!" + user.getName();
    }
}
