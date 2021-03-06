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
        // ??????scan???pacakage????????????????????????
        Set<String> resolvedPackagesToScan = resolvePackagesToScan(packagesToScan);
        if (!CollectionUtils.isEmpty(resolvedPackagesToScan)) {
            // ???scan?????????????????????????????????SisyphusServiceBean
            registerSisyphusServiceBeans(resolvedPackagesToScan, beanDefinitionRegistry);
        } else {
            log.warn("packagesToScan is empty , no service export , SisyphusServiceBean registry will be ignored!");
        }
    }

    /*
    * ??????SisyphusServiceBean
    * */
    private void registerSisyphusServiceBeans(Set<String> packagesToScan, BeanDefinitionRegistry registry) {
        // ??????ClassPathBeanDefinitionScanner
        ClassPathBeanDefinitionScanner scanner =
                new ClassPathBeanDefinitionScanner(registry, false);
        scanner.setEnvironment(environment);
        scanner.setResourceLoader(resourceLoader);
        // beanName?????????
        BeanNameGenerator beanNameGenerator = new AnnotationBeanNameGenerator();
        scanner.setBeanNameGenerator(beanNameGenerator);
        // ???????????????????????????
        scanner.addIncludeFilter(new AnnotationTypeFilter(SisyphusService.class));

        // ????????????
        for (String packageToScan : packagesToScan) {
            // ????????????????????????????????????@SisyphusService???bean?????????beanFactory???
            scanner.scan(packageToScan);
            // ???????????????beanDefinitionHolders????????????ServiceBean???????????????beanFactory???
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
    * ????????????SisyphusServiceBean
    * */
    private void registerSisyphusServiceBean(BeanDefinitionHolder beanDefinitionHolder, BeanDefinitionRegistry registry,
                                     ClassPathBeanDefinitionScanner scanner) {
        // ??????beanDefinitionHolder?????????Class???
        BeanDefinition beanDefinition = beanDefinitionHolder.getBeanDefinition();
        String beanClassName = beanDefinition.getBeanClassName();
        Class<?> beanClass = resolveClassName(beanClassName, classLoader);
        // ??????????????????@SisyphusService??????
        Annotation sisyphusService = findMergedAnnotation(beanClass, SisyphusService.class);
        // ????????????????????????
        AnnotationAttributes sisyphusServiceAnnotationAttributes = getAnnotationAttributes(sisyphusService, false, false);
        // ?????????????????????
        Class<?> interfaceClass = resolveServiceInterfaceClass(sisyphusServiceAnnotationAttributes, beanClass);
        // ??????BDHolder??????beanName(???????????????Servicebean???Name)
        String annotatedServiceBeanName = beanDefinitionHolder.getBeanName();
        // ??????SisyphusServiceBean important?????????
        AbstractBeanDefinition serviceBeanDefinition =
                buildSisyphusServiceBeanDefinition(sisyphusServiceAnnotationAttributes, interfaceClass, annotatedServiceBeanName);
        // ???????????????SisyphusServiceBean???Name
        String beanName = generateSisyphusServiceBeanName(sisyphusServiceAnnotationAttributes, interfaceClass);
        // TODO ??????
        // ??????
        registry.registerBeanDefinition(beanName, serviceBeanDefinition);
    }

    private String generateSisyphusServiceBeanName(AnnotationAttributes serviceAnnotationAttributes, Class<?> interfaceClass) {
        SisyphusServiceBeanNameBuilder builder = create(interfaceClass, environment)
                .version(serviceAnnotationAttributes.getString("version"));
        return builder.build();
    }

    /*
    * ??????SisyphusServiceBean
    * */
    private AbstractBeanDefinition buildSisyphusServiceBeanDefinition(AnnotationAttributes serviceAnnotationAttributes,
                                                              Class<?> interfaceClass,
                                                              String annotatedServiceBeanName) {
        BeanDefinitionBuilder builder = rootBeanDefinition(SisyphusServiceBean.class);
        // ????????????
        builder.addPropertyValue("interfaceClass", interfaceClass.getName());
        // ???????????????????????????Service???beanName
        builder.addPropertyValue("refBeanName", annotatedServiceBeanName);
        // ??????host
        /*String host = serviceAnnotationAttributes.getString("host");
        if (StringUtils.hasText(host)) {
            // ????????????export???host??????????????????
            builder.addPropertyValue("host", serviceAnnotationAttributes.getString("host"));
        } else {
            // ?????????????????????????????????IP
            try {
                builder.addPropertyValue("host", InetAddress.getLocalHost().getHostAddress());
            } catch (Throwable t) {
                log.error("build SisyphusServiceBean error get local host error, t" + t.getMessage());
            }
        }
        // ??????Port
        builder.addPropertyValue("port",
                Integer.valueOf(serviceAnnotationAttributes.getNumber("port")));*/

        // ??????????????????RpcSpringExporter
        if (StringUtils.hasText(serviceAnnotationAttributes.getString("exporter"))) {
            // ????????????exporter
            builder.addPropertyValue("rpcSpringExporterName",
                    serviceAnnotationAttributes.getString("exporter"));
        }

        return builder.getBeanDefinition();
    }

    /*
    * ??????scan????????????????????????
    * */
    private Set<BeanDefinitionHolder> findServiceBeanDefinitionHolders(
            ClassPathBeanDefinitionScanner scanner, String packageToScan, BeanDefinitionRegistry registry,
            BeanNameGenerator beanNameGenerator) {
        Set<BeanDefinition> beanDefinitions = scanner.findCandidateComponents(packageToScan);
        Set<BeanDefinitionHolder> beanDefinitionHolders = new LinkedHashSet<>(beanDefinitions.size());
        // TODO ??????????????????BeanDefinitionHolder?????????????????????BeanDefinition?????????????????????????????????beanName
        for (BeanDefinition beanDefinition : beanDefinitions) {
            String beanName = beanNameGenerator.generateBeanName(beanDefinition, registry);
            BeanDefinitionHolder beanDefinitionHolder = new BeanDefinitionHolder(beanDefinition, beanName);
            beanDefinitionHolders.add(beanDefinitionHolder);
        }
        return beanDefinitionHolders;
    }

    /*
    * ??????????????????????????????
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
