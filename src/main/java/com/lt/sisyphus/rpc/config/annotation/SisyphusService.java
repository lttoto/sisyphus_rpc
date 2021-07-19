package com.lt.sisyphus.rpc.config.annotation;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
public @interface SisyphusService {

    // 当有多个标签时，指定需要的
    Class<?> interfaceClass() default void.class;
    // 当有多个标签时，指定需要的
    String interfaceName() default "";
    // 指定version
    String version() default "";
    // 指定该Service使用的Exporter
    String exporter() default "";
}
