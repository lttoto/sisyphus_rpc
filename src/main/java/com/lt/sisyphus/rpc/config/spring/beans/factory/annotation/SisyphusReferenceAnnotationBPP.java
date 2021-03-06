package com.lt.sisyphus.rpc.config.spring.beans.factory.annotation;

import com.lt.sisyphus.rpc.config.annotation.SisyphusReference;
import com.lt.sisyphus.rpc.config.spring.SisyphusReferenceBean;
import com.lt.sisyphus.rpc.config.spring.util.AnnotationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.*;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.lt.sisyphus.rpc.config.spring.util.AnnotationUtils.getAttribute;
import static org.springframework.util.StringUtils.hasText;

@Slf4j
public class SisyphusReferenceAnnotationBPP extends InstantiationAwareBeanPostProcessorAdapter
        implements MergedBeanDefinitionPostProcessor, PriorityOrdered,
        BeanFactoryAware, BeanClassLoaderAware, EnvironmentAware, DisposableBean,
        ApplicationContextAware, ApplicationListener {

    public static final String BEAN_NAME = "sisyphusReferenceAnnotationBPP";

    private final static int CACHE_SIZE = Integer.getInteger("", 32);

    private static final String SEPARATOR = ":";
    /*
    * ??????????????????
    * */
    private final ConcurrentMap<String, AnnotatedInjectionMetadata> injectionMetadataCache =
            new ConcurrentHashMap<String, AnnotatedInjectionMetadata>(CACHE_SIZE);

    /*
    * ???????????????Proxy???bean??????????????????????????????RPC Proxy?????????????????????
    * */
    private final ConcurrentMap<String, Object> injectedObjectsCache = new ConcurrentHashMap<>(CACHE_SIZE);

    /*
    * ??????referenceBean
    * */
    private final ConcurrentMap<String, SisyphusReferenceBean<?>> referenceBeanCache =
            new ConcurrentHashMap<>(CACHE_SIZE);

    private final Class<? extends Annotation> annotation;
    /*
     * @SisyphusReference?????????BPP?????????@Autowired?????????@Autowired???BPP???AutowiredAnnotationBeanPostProcessor???order=Ordered.LOWEST_PRECEDENCE - 2
     * */
    private int order = Ordered.LOWEST_PRECEDENCE - 3;

    private ApplicationContext applicationContext;

    private ConfigurableListableBeanFactory beanFactory;

    private Environment environment;

    private ClassLoader classLoader;

    public SisyphusReferenceAnnotationBPP() {
        this.annotation = SisyphusReference.class;
    }

    public SisyphusReferenceAnnotationBPP(Class<? extends Annotation> annotation) {
        Assert.notNull(annotation, "The argument of annotations' types must not empty");
        this.annotation = annotation;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent applicationEvent) {

    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        Assert.isInstanceOf(ConfigurableListableBeanFactory.class, beanFactory,
                "AnnotationInjectedBeanPostProcessor requires a ConfigurableListableBeanFactory");
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    protected ConfigurableListableBeanFactory getBeanFactory() {
        return beanFactory;
    }

    @Override
    public void destroy() throws Exception {
        for (Object object : injectedObjectsCache.values()) {
            if (object instanceof DisposableBean) {
                ((DisposableBean) object).destroy();
            }
        }
        injectionMetadataCache.clear();
        injectedObjectsCache.clear();
        referenceBeanCache.clear();
    }

    /*
    * step 1 ???????????????Metadata
    * */
    @Override
    public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
        if (beanType != null) {
            InjectionMetadata metadata = findInjectionMetadata(beanName, beanType, null);
            // TODO ??????bd?????????externallyManagedConfigMembers??????????????????????????????????????????
            metadata.checkConfigMembers(beanDefinition);
        }
    }

    /*
    * Step2 ????????????????????????????????????metadata?????????inject
    * */
    @Override
    public PropertyValues postProcessPropertyValues(
            PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName) throws BeanCreationException {
        // ???????????????bean??????????????????????????????????????????injectionMetadataCache????????????
        InjectionMetadata metadata = findInjectionMetadata(beanName, bean.getClass(), pvs);
        try {
            // ??????InjectionMetadata???inject??????????????????metadata????????????AnnotatedFieldElement??????????????????AnnotatedFieldElement?????????inject??????
            metadata.inject(bean, beanName, pvs);
        } catch (BeanCreationException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new BeanCreationException(beanName, "Injection of @" + this.annotation.getSimpleName()
                    + " dependencies is failed", ex);
        }
        return pvs;
    }

    /*
    * ?????????????????????????????????????????????????????????????????????
    * */
    private InjectionMetadata findInjectionMetadata(String beanName, Class<?> clazz, PropertyValues pvs) {
        String cacheKey = (StringUtils.hasLength(beanName) ? beanName : clazz.getName());
        AnnotatedInjectionMetadata metadata = this.injectionMetadataCache.get(cacheKey);
        if (InjectionMetadata.needsRefresh(metadata, clazz)) {
            // ???????????????
            synchronized (this.injectionMetadataCache) {
                // ????????????
                metadata = this.injectionMetadataCache.get(cacheKey);
                if (InjectionMetadata.needsRefresh(metadata, clazz)) {
                    if (metadata != null) {
                        metadata.clear(pvs);
                    }
                    try {
                        metadata = buildAnnotatedMetadata(clazz);
                        this.injectionMetadataCache.put(cacheKey, metadata);
                    } catch (NoClassDefFoundError err) {
                        throw new IllegalStateException("Failed to introspect object class [" + clazz.getName() +
                                "] for annotation metadata: could not find class that it depends on", err);
                    }
                }
            }
        }
        return metadata;
    }

    /*
    * ??????metadata
    * */
    private AnnotatedInjectionMetadata buildAnnotatedMetadata(final Class<?> beanClass) {
        List<AnnotatedFieldElement> fieldElements = findFieldAnnotationMetadata(beanClass);
        return new AnnotatedInjectionMetadata(beanClass, fieldElements);
    }

    /*
    * ??????bean???????????????????????????@SisyphusReference
    * */
    private List<AnnotatedFieldElement> findFieldAnnotationMetadata(final Class<?> beanClass) {

        final List<AnnotatedFieldElement> elements = new ArrayList<>();
        // ??????beanClass?????????
        ReflectionUtils.doWithFields(beanClass, field -> {
            // ??????beanClass??????field????????????@SisyphusReference???????????????????????????????????????????????????
            AnnotationAttributes attributes = AnnotationUtils.getMergedAttributes(field, this.annotation, getEnvironment(), true);
            // ??????????????????field?????????@SisyphusReference????????????@SisyphusReference????????????????????????????????????????????????size=0???attributes???
            if (attributes != null) {
                // @SisyphusReference????????????static????????????
                if (Modifier.isStatic(field.getModifiers())) {
                    log.warn("@" + this.annotation.getName() + " is not supported on static fields: " + field);
                    return;
                }
                elements.add(new AnnotatedFieldElement(field, attributes));
            }
        });

        return elements;
    }



    protected Environment getEnvironment() {
        return environment;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return order;
    }

    /*
    * ????????????????????????????????????????????????@SisyphusReference???????????????
    * TODO ??????@SisyphusReference?????????????????????Method?????????????????????
    * */
    private class AnnotatedInjectionMetadata extends InjectionMetadata {
        // private final Collection<AnnotatedMethodElement> methodElements;
        private final List<AnnotatedFieldElement> fieldElements;

        public AnnotatedInjectionMetadata(Class<?> targetClass, List<AnnotatedFieldElement> fieldElements) {
            // super(targetClass, combine(fieldElements, methodElements));
            super(targetClass, new ArrayList<>(fieldElements));
            this.fieldElements = fieldElements;
            // this.methodElements = methodElements;
        }

        public List<AnnotatedFieldElement> getFieldElements() {
            return fieldElements;
        }
    }

    /*private class AnnotatedMethodElement extends InjectionMetadata.InjectedElement {

    }*/

    public class AnnotatedFieldElement extends InjectionMetadata.InjectedElement {

        private final Field field;

        private final AnnotationAttributes attributes;

        private volatile Object bean;

        protected AnnotatedFieldElement(Field field, AnnotationAttributes attributes) {
            super(field, null);
            this.field = field;
            this.attributes = attributes;
        }

        @Override
        protected void inject(Object bean, String beanName, PropertyValues pvs) throws Throwable {
            // ???@SisyphusReferenc???????????????????????????
            Class<?> injectedType = field.getType();
            // ???????????????@SisyphusReferenc??????????????????bean????????????
            Object injectedObject = getInjectedObject(attributes, bean, beanName, injectedType, this);
            // ????????????????????????????????????private????????????
            ReflectionUtils.makeAccessible(field);
            // ???????????????set?????????????????????Rpc??????????????????????????????Proxy???????????????????????????????????????
            field.set(bean, injectedObject);
        }

    }

    /*
    * ???????????????bean
    * */
    private Object getInjectedObject(AnnotationAttributes attributes, Object bean, String beanName, Class<?> injectedType,
                                       InjectionMetadata.InjectedElement injectedElement) throws Exception {
        String cacheKey = buildInjectedObjectCacheKey(attributes, bean, beanName, injectedType, injectedElement);

        Object injectedObject = injectedObjectsCache.get(cacheKey);
        // ???????????????????????????
        if (injectedObject == null) {
            // ???????????????????????????????????????RPC????????????????????????RPC Proxy?????????
            injectedObject = doGetInjectedBean(attributes, bean, beanName, injectedType, injectedElement);
            // Customized inject-object if necessary
            injectedObjectsCache.putIfAbsent(cacheKey, injectedObject);
        }

        return injectedObject;
    }

    /*
    * ??????ReferenceBean???????????????ReferencBean?????????????????????????????????
    * ?????????RPC???????????????
    * TODO ???????????????@SisyphusReference  ???????????????@SisyphusService??????
    * */
    protected Object doGetInjectedBean(AnnotationAttributes attributes, Object bean, String beanName, Class<?> injectedType,
                                       InjectionMetadata.InjectedElement injectedElement) throws Exception {
        // ??????ReferenceBeanName/**/
        String referenceBeanName = getAttribute(attributes, "id");
        if (!hasText(referenceBeanName)) {
            StringBuilder beanNameBuilder = new StringBuilder("@Reference");
            if (!attributes.isEmpty()) {
                for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                    beanNameBuilder.append(entry.getKey())
                            .append('=')
                            .append(entry.getValue())
                            .append(',');
                }
                // replace the latest "," to be ")"
                beanNameBuilder.setCharAt(beanNameBuilder.lastIndexOf(","), ')');
            }
            beanNameBuilder.append(" ").append(injectedType.getName());
            referenceBeanName = beanNameBuilder.toString();
        }
        // ??????ReferenceBean
        SisyphusReferenceBean sisyphusReferenceBean = buildReferenceBeanIfAbsent(referenceBeanName, attributes, injectedType);
        // ??????ReferenceBean
        registerReferenceBean(referenceBeanName, sisyphusReferenceBean);
        // TODO injectedFieldReferenceBeanCache ???????????????
        // injectedFieldReferenceBeanCache.put(injectedElement, sisyphusReferenceBean);
        // String test = environment.getProperty("sisyphus.registry.zookeeper.address");
        // ???RPC??????
        return sisyphusReferenceBean.get();
    }

    /*
    * ??????ReferenceBean
    * */
    private SisyphusReferenceBean buildReferenceBeanIfAbsent(String referenceBeanName, AnnotationAttributes attributes,
                                                     Class<?> referencedType)
            throws Exception {
        SisyphusReferenceBean<?> referenceBean = referenceBeanCache.get(referenceBeanName);
        if (referenceBean == null) {
            String url = getAttribute(attributes,"url");
            String registryName = getAttribute(attributes, "registry");
            referenceBean = new SisyphusReferenceBean(url,referencedType, registryName, environment);
        }
        return referenceBean;
    }

    /*
    * Spring??????ReferenceBean
    * */
    private void registerReferenceBean(String referenceBeanName, SisyphusReferenceBean referenceBean) {
        ConfigurableListableBeanFactory beanFactory = getBeanFactory();
        if (!beanFactory.containsBean(referenceBeanName)) {
            beanFactory.registerSingleton(referenceBeanName, referenceBean);
        }
    }

    /*
    * ???????????????????????????Key = RefenceBeanName + source + attribute
    * */
    private String buildInjectedObjectCacheKey(AnnotationAttributes attributes, Object bean, String beanName,
                                                 Class<?> injectedType, InjectionMetadata.InjectedElement injectedElement) {
        return buildReferencedBeanName(attributes, injectedType) +
                "#source=" + (injectedElement.getMember()) +
                "#attributes=" + AnnotationUtils.resolvePlaceholders(attributes, getEnvironment());
    }

    /*
    * ??????ReferenceBeanName
    * */
    private String buildReferencedBeanName(AnnotationAttributes attributes, Class<?> serviceInterfaceType) {
        String group = getAttribute(attributes,"group");
        String version = getAttribute(attributes,"version");
        String interfaceClassName = serviceInterfaceType.getName();

        StringBuilder beanNameBuilder = new StringBuilder("ServiceBean");
        String rawBeanName = beanNameBuilder
                .append(SEPARATOR)
                .append(interfaceClassName)
                .append(SEPARATOR)
                .append(version)
                .append(SEPARATOR)
                .append(group)
                .toString();
        return environment.resolvePlaceholders(rawBeanName);
    }
}
