package com.lt.sisyphus.rpc.config.spring.beans.factory.annotation;

import com.lt.sisyphus.rpc.config.annotation.SisyphusService;
import com.lt.sisyphus.rpc.config.spring.SisyphusServiceBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.lt.sisyphus.rpc.config.spring.beans.factory.annotation.SisyphusServiceBeanNameBuilder.create;
import static com.lt.sisyphus.rpc.config.spring.util.AnnotationUtils.resolveServiceInterfaceClass;
import static org.springframework.beans.factory.support.BeanDefinitionBuilder.rootBeanDefinition;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;
import static org.springframework.core.annotation.AnnotationUtils.getAnnotationAttributes;
import static org.springframework.util.ClassUtils.resolveClassName;

@Slf4j
public class SisyphusServiceAnnotationBPP implements BeanDefinitionRegistryPostProcessor, EnvironmentAware,
        ResourceLoaderAware, BeanClassLoaderAware {

    private final Set<String> packagesToScan;

    private Environment environment;

    private ResourceLoader resourceLoader;

    private ClassLoader classLoader;

    public SisyphusServiceAnnotationBPP(String... packagesToScan) {
        this(Arrays.asList(packagesToScan));
    }

    public SisyphusServiceAnnotationBPP(Collection<String> packagesToScan) {
        this(new LinkedHashSet<>(packagesToScan));
    }

    public SisyphusServiceAnnotationBPP(Set<String> packagesToScan) {
        this.packagesToScan = packagesToScan;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {
        // 解析scan的pacakage包名，处理占位符
        Set<String> resolvedPackagesToScan = resolvePackagesToScan(packagesToScan);
        if (!CollectionUtils.isEmpty(resolvedPackagesToScan)) {
            // 若scan的包不为空，注册对应的SisyphusServiceBean
            registerSisyphusServiceBeans(resolvedPackagesToScan, beanDefinitionRegistry);
        } else {
            log.warn("packagesToScan is empty , no service export , SisyphusServiceBean registry will be ignored!");
        }
    }

    /*
    * 注册SisyphusServiceBean
    * */
    private void registerSisyphusServiceBeans(Set<String> packagesToScan, BeanDefinitionRegistry registry) {
        // 封装ClassPathBeanDefinitionScanner
        ClassPathBeanDefinitionScanner scanner =
                new ClassPathBeanDefinitionScanner(registry, false);
        scanner.setEnvironment(environment);
        scanner.setResourceLoader(resourceLoader);
        // beanName生成器
        BeanNameGenerator beanNameGenerator = new AnnotationBeanNameGenerator();
        scanner.setBeanNameGenerator(beanNameGenerator);
        // 新增需要扫描的标签
        scanner.addIncludeFilter(new AnnotationTypeFilter(SisyphusService.class));

        // 扫描路径
        for (String packageToScan : packagesToScan) {
            // 扫描指定包中，先把实际的@SisyphusService的bean注册到beanFactory中
            scanner.scan(packageToScan);
            // 取出对应的beanDefinitionHolders，组成成ServiceBean，再注册到beanFactory中
            Set<BeanDefinitionHolder> beanDefinitionHolders =
                    findServiceBeanDefinitionHolders(scanner, packageToScan, registry, beanNameGenerator);

            if (!CollectionUtils.isEmpty(beanDefinitionHolders)) {
                for (BeanDefinitionHolder beanDefinitionHolder : beanDefinitionHolders) {
                    registerSisyphusServiceBean(beanDefinitionHolder, registry, scanner);
                }
                log.info(beanDefinitionHolders.size() + " Spring beans annotated @SisyphusService under package " + packageToScan);
            } else {
                log.warn("No Spring Bean annotating @SisyphusService was found under package " + packageToScan);
            }
        }
    }

    /*
    * 手动注册SisyphusServiceBean
    * */
    private void registerSisyphusServiceBean(BeanDefinitionHolder beanDefinitionHolder, BeanDefinitionRegistry registry,
                                     ClassPathBeanDefinitionScanner scanner) {
        // 解析beanDefinitionHolder对应的Class类
        BeanDefinition beanDefinition = beanDefinitionHolder.getBeanDefinition();
        String beanClassName = beanDefinition.getBeanClassName();
        Class<?> beanClass = resolveClassName(beanClassName, classLoader);
        // 找到该类上的@SisyphusService标签
        Annotation sisyphusService = findMergedAnnotation(beanClass, SisyphusService.class);
        // 获取标签上的内容
        AnnotationAttributes sisyphusServiceAnnotationAttributes = getAnnotationAttributes(sisyphusService, false, false);
        // 获取类上的接口
        Class<?> interfaceClass = resolveServiceInterfaceClass(sisyphusServiceAnnotationAttributes, beanClass);
        // 获取BDHolder上的beanName(原来的那个Servicebean的Name)
        String annotatedServiceBeanName = beanDefinitionHolder.getBeanName();
        // 封装SisyphusServiceBean important！！！
        AbstractBeanDefinition serviceBeanDefinition =
                buildSisyphusServiceBeanDefinition(sisyphusServiceAnnotationAttributes, interfaceClass, annotatedServiceBeanName);
        // 生成对应的SisyphusServiceBean的Name
        String beanName = generateSisyphusServiceBeanName(sisyphusServiceAnnotationAttributes, interfaceClass);
        // TODO 查重
        // 注入
        registry.registerBeanDefinition(beanName, serviceBeanDefinition);
    }

    private String generateSisyphusServiceBeanName(AnnotationAttributes serviceAnnotationAttributes, Class<?> interfaceClass) {
        SisyphusServiceBeanNameBuilder builder = create(interfaceClass, environment)
                .version(serviceAnnotationAttributes.getString("version"));
        return builder.build();
    }

    /*
    * 注册SisyphusServiceBean
    * */
    private AbstractBeanDefinition buildSisyphusServiceBeanDefinition(AnnotationAttributes serviceAnnotationAttributes,
                                                              Class<?> interfaceClass,
                                                              String annotatedServiceBeanName) {
        BeanDefinitionBuilder builder = rootBeanDefinition(SisyphusServiceBean.class);
        // 封装接口
        builder.addPropertyValue("interfaceClass", interfaceClass.getName());
        // 封装对应真正的代理Service的beanName
        builder.addPropertyValue("refBeanName", annotatedServiceBeanName);
        // 封装host
        /*String host = serviceAnnotationAttributes.getString("host");
        if (StringUtils.hasText(host)) {
            // 若指定了export的host，使用指定的
            builder.addPropertyValue("host", serviceAnnotationAttributes.getString("host"));
        } else {
            // 若没有指定，使用本机的IP
            try {
                builder.addPropertyValue("host", InetAddress.getLocalHost().getHostAddress());
            } catch (Throwable t) {
                log.error("build SisyphusServiceBean error get local host error, t" + t.getMessage());
            }
        }
        // 封装Port
        builder.addPropertyValue("port",
                Integer.valueOf(serviceAnnotationAttributes.getNumber("port")));*/

        // 是否有指定的RpcSpringExporter
        if (StringUtils.hasText(serviceAnnotationAttributes.getString("exporter"))) {
            // 有指定的exporter
            builder.addPropertyValue("rpcSpringExporterName",
                    serviceAnnotationAttributes.getString("exporter"));
        }

        return builder.getBeanDefinition();
    }

    /*
    * 获取scan包中的对应的标签
    * */
    private Set<BeanDefinitionHolder> findServiceBeanDefinitionHolders(
            ClassPathBeanDefinitionScanner scanner, String packageToScan, BeanDefinitionRegistry registry,
            BeanNameGenerator beanNameGenerator) {
        Set<BeanDefinition> beanDefinitions = scanner.findCandidateComponents(packageToScan);
        Set<BeanDefinitionHolder> beanDefinitionHolders = new LinkedHashSet<>(beanDefinitions.size());
        // TODO 为什么一定要BeanDefinitionHolder，而不是直接用BeanDefinition，在实际注册的时候生成beanName
        for (BeanDefinition beanDefinition : beanDefinitions) {
            String beanName = beanNameGenerator.generateBeanName(beanDefinition, registry);
            BeanDefinitionHolder beanDefinitionHolder = new BeanDefinitionHolder(beanDefinition, beanName);
            beanDefinitionHolders.add(beanDefinitionHolder);
        }
        return beanDefinitionHolders;
    }

    /*
    * 解析包名，替换占位符
    * */
    private Set<String> resolvePackagesToScan(Set<String> packagesToScan) {
        Set<String> resolvedPackagesToScan = new LinkedHashSet<String>(packagesToScan.size());
        for (String packageToScan : packagesToScan) {
            if (StringUtils.hasText(packageToScan)) {
                String resolvedPackageToScan = environment.resolvePlaceholders(packageToScan.trim());
                resolvedPackagesToScan.add(resolvedPackageToScan);
            }
        }
        return resolvedPackagesToScan;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}
