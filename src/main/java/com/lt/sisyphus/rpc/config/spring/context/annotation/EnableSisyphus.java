package com.lt.sisyphus.rpc.config.spring.context.annotation;

import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@SisyphusComponentScan
public @interface EnableSisyphus {

    @AliasFor(annotation = SisyphusComponentScan.class, attribute = "basePackages")
    String[] scanBasePackages() default {};

    @AliasFor(annotation = SisyphusComponentScan.class, attribute = "basePackageClasses")
    Class<?>[] scanBasePackageClasses() default {};

    // TODO 支持配置文件
}
