package com.lt.sisyphus.rpc.invoke.consumer.test;

import com.lt.sisyphus.rpc.config.annotation.SisyphusReference;
import org.springframework.stereotype.Service;

@Service
public class ConsumerDemoImpl {
    @SisyphusReference
    private HelloService helloService;
    @SisyphusReference
    private TestService testService;


    public String test() {
        return helloService.hello("lttoto");
    }

    public String test1() {return testService.test();}
}
