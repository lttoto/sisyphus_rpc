package com.lt.sisyphus.rpc.config.spring.beans.factory.annotation;

import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import static com.lt.sisyphus.rpc.config.spring.util.AnnotationUtils.getAttribute;
import static com.lt.sisyphus.rpc.config.spring.util.AnnotationUtils.resolveInterfaceName;

public class SisyphusServiceBeanNameBuilder {

    private static final String SEPARATOR = ":";

    // Required
    private final String interfaceClassName;

    private final Environment environment;

    // Optional
    private String version;

    private SisyphusServiceBeanNameBuilder(Class<?> interfaceClass, Environment environment) {
        this(interfaceClass.getName(), environment);
    }

    private SisyphusServiceBeanNameBuilder(String interfaceClassName, Environment environment) {
        this.interfaceClassName = interfaceClassName;
        this.environment = environment;
    }

    private SisyphusServiceBeanNameBuilder(AnnotationAttributes attributes, Class<?> defaultInterfaceClass, Environment environment) {
        this(resolveInterfaceName(attributes, defaultInterfaceClass), environment);
        this.version(getAttribute(attributes,"version"));
    }

    public static SisyphusServiceBeanNameBuilder create(Class<?> interfaceClass, Environment environment) {
        return new SisyphusServiceBeanNameBuilder(interfaceClass, environment);
    }

    private static void append(StringBuilder builder, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(SEPARATOR).append(value);
        }
    }

    public SisyphusServiceBeanNameBuilder version(String version) {
        this.version = version;
        return this;
    }

    public String build() {
        StringBuilder beanNameBuilder = new StringBuilder("SisyphusServiceBean");
        // Required
        append(beanNameBuilder, interfaceClassName);
        // Optional
        append(beanNameBuilder, version);
        // Build and remove last ":"
        String rawBeanName = beanNameBuilder.toString();
        // Resolve placeholders
        return environment.resolvePlaceholders(rawBeanName);
    }
}
