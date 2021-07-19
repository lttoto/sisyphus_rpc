package com.lt.sisyphus.rpc.config.spring.beans.factory.annotation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

import java.lang.annotation.Annotation;

@Slf4j
public abstract class AnnotationInjectedBPP extends
        InstantiationAwareBeanPostProcessorAdapter implements MergedBeanDefinitionPostProcessor, PriorityOrdered,
        BeanFactoryAware, BeanClassLoaderAware, EnvironmentAware, DisposableBean {


    private final Class<? extends Annotation>[] annotationTypes;
    /*
    * @SisyphusReference注入的BPP需要在@Autowired之前，@Autowired的BPP是AutowiredAnnotationBeanPostProcessor，order=Ordered.LOWEST_PRECEDENCE - 2
    * */
    private int order = Ordered.LOWEST_PRECEDENCE - 3;

    public AnnotationInjectedBPP(Class<? extends Annotation>... annotationTypes) {
        Assert.notEmpty(annotationTypes, "The argument of annotations' types must not empty");
        this.annotationTypes = annotationTypes;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {

    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void destroy() throws Exception {

    }

    @Override
    public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {

    }

    @Override
    public void setEnvironment(Environment environment) {

    }

    @Override
    public int getOrder() {
        return order;
    }

}
