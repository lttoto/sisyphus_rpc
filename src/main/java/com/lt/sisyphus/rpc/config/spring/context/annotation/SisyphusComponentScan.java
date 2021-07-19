package com.lt.sisyphus.rpc.config.spring.context.annotation;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SisyphusComponentScanRegistrar.class)
public @interface SisyphusComponentScan {

    String[] basePackages() default {};

    Class<?>[] basePackageClasses() default {};
}
