package com.lt.sisyphus.rpc.config.spring.context.annotation;

import com.lt.sisyphus.rpc.config.annotation.SisyphusReference;
import com.lt.sisyphus.rpc.config.spring.beans.factory.annotation.SisyphusReferenceAnnotationBPP;
import com.lt.sisyphus.rpc.config.spring.beans.factory.annotation.SisyphusServiceAnnotationBPP;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.*;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.springframework.beans.factory.support.BeanDefinitionBuilder.rootBeanDefinition;

public class SisyphusComponentScanRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry beanDefinitionRegistry) {

        // 获取SisyphusComponentScan标签中的scan包名
        Set<String> packagesToScan = getPackagesToScan(annotationMetadata);
        // 注册@SisyphusService的BeanDefinitionRegistryPostProcessor-（SisyphusServiceAnnotationBPP）
        registerSisyphusServiceAnnotationBeanPostProcessor(packagesToScan, beanDefinitionRegistry);
        // 注册@SisyphusReference的BeanDefinitionRegistryPostProcessor-（SisyphusServiceAnnotationBPP）
        registerSisyphusReferenceAnnotationBeanPostProcessor(beanDefinitionRegistry);
    }

    private void registerSisyphusServiceAnnotationBeanPostProcessor(Set<String> packagesToScan, BeanDefinitionRegistry registry) {
        BeanDefinitionBuilder builder = rootBeanDefinition(SisyphusServiceAnnotationBPP.class);
        builder.addConstructorArgValue(packagesToScan);
        builder.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();
        BeanDefinitionReaderUtils.registerWithGeneratedName(beanDefinition, registry);

    }

    private void registerSisyphusReferenceAnnotationBeanPostProcessor(BeanDefinitionRegistry registry) {
        // Register @Reference Annotation Bean Processor
        String beanName = SisyphusReferenceAnnotationBPP.BEAN_NAME;
        if (!registry.containsBeanDefinition(beanName)) {
            RootBeanDefinition beanDefinition = new RootBeanDefinition(SisyphusReferenceAnnotationBPP.class);
            beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
            // TODO 验证一下这样操作SisyphusReference是否可以初始化SisyphusReference标签
            // ConstructorArgumentValues.ValueHolder valueHolder =
                    new ConstructorArgumentValues.ValueHolder(SisyphusReference.class, Annotation.class.getName());
            // ConstructorArgumentValues argumentValues = beanDefinition.getConstructorArgumentValues();
            // argumentValues.addIndexedArgumentValue(argumentValues.getArgumentCount(), valueHolder);
            registry.registerBeanDefinition(beanName, beanDefinition);
        }
    }

    /*
    * 处理scan的包名
    * */
    private Set<String> getPackagesToScan(AnnotationMetadata metadata) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                metadata.getAnnotationAttributes(SisyphusComponentScan.class.getName()));
        String[] basePackages = attributes.getStringArray("basePackages");
        Class<?>[] basePackageClasses = attributes.getClassArray("basePackageClasses");
        // String[] value = attributes.getStringArray("value");
        // Appends value array attributes
        Set<String> packagesToScan = new LinkedHashSet<String>(Arrays.asList(basePackages));
        // packagesToScan.addAll(Arrays.asList(basePackages));
        for (Class<?> basePackageClass : basePackageClasses) {
            packagesToScan.add(ClassUtils.getPackageName(basePackageClass));
        }
        if (packagesToScan.isEmpty()) {
            return Collections.singleton(ClassUtils.getPackageName(metadata.getClassName()));
        }
        return packagesToScan;
    }
}
