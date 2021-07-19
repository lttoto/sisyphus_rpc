package com.lt.sisyphus.rpc.invoke.provider.test;

import com.lt.sisyphus.rpc.invoke.consumer.test.HelloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProviderDemoImpl {

    @Autowired
    private HelloService helloService;

    public String test() {
        return helloService.hello("lt");
    }
}
