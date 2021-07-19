package com.lt.sisyphus.rpc.config.annotation;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Inherited
public @interface SisyphusReference {
    Class<?> interfaceClass() default void.class;
    String interfaceName() default "";
    // 指定version
    String version() default "";
    // 指定group
    String group() default "";
    // 指定对外暴露的地址
    String url() default "";
}
