package com.lt.sisyphus.rpc.invoke.provider.test;

import com.lt.sisyphus.rpc.config.annotation.SisyphusService;
import com.lt.sisyphus.rpc.invoke.consumer.test.TestService;

@SisyphusService
public class TestServiceImpl implements TestService {
    @Override
    public String test() {
        return "test";
    }
}
