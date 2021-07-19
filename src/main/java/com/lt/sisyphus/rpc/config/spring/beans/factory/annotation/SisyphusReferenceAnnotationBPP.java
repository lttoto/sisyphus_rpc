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
    * 具体缓存队列
    * */
    private final ConcurrentMap<String, AnnotatedInjectionMetadata> injectionMetadataCache =
            new ConcurrentHashMap<String, AnnotatedInjectionMetadata>(CACHE_SIZE);

    /*
    * 具体创建的Proxy的bean缓存，保证一定创建的RPC Proxy不需要再次创建
    * */
    private final ConcurrentMap<String, Object> injectedObjectsCache = new ConcurrentHashMap<>(CACHE_SIZE);

    /*
    * 缓存referenceBean
    * */
    private final ConcurrentMap<String, SisyphusReferenceBean<?>> referenceBeanCache =
            new ConcurrentHashMap<>(CACHE_SIZE);

    private final Class<? extends Annotation> annotation;
    /*
     * @SisyphusReference注入的BPP需要在@Autowired之前，@Autowired的BPP是AutowiredAnnotationBeanPostProcessor，order=Ordered.LOWEST_PRECEDENCE - 2
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
    * step 1 找到注册的Metadata
    * */
    @Override
    public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
        if (beanType != null) {
            InjectionMetadata metadata = findInjectionMetadata(beanName, beanType, null);
            // TODO 判断bd是否在externallyManagedConfigMembers中，具体没啥用（后面看一下）
            metadata.checkConfigMembers(beanDefinition);
        }
    }

    /*
    * Step2 实例化后执行的方法，进行metadata的具体inject
    * */
    @Override
    public PropertyValues postProcessPropertyValues(
            PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName) throws BeanCreationException {
        // 重新找一下bean是否有需要的标签（理论上能在injectionMetadataCache中找到）
        InjectionMetadata metadata = findInjectionMetadata(beanName, bean.getClass(), pvs);
        try {
            // 调用InjectionMetadata的inject方法，这里的metadata是内部类AnnotatedFieldElement，所以会调用AnnotatedFieldElement重写的inject方法
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
    * 具体找的过程，使用队列缓存已经加载过的类！！！
    * */
    private InjectionMetadata findInjectionMetadata(String beanName, Class<?> clazz, PropertyValues pvs) {
        String cacheKey = (StringUtils.hasLength(beanName) ? beanName : clazz.getName());
        AnnotatedInjectionMetadata metadata = this.injectionMetadataCache.get(cacheKey);
        if (InjectionMetadata.needsRefresh(metadata, clazz)) {
            // 简单锁队列
            synchronized (this.injectionMetadataCache) {
                // 双重检查
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
    * 封装metadata
    * */
    private AnnotatedInjectionMetadata buildAnnotatedMetadata(final Class<?> beanClass) {
        List<AnnotatedFieldElement> fieldElements = findFieldAnnotationMetadata(beanClass);
        return new AnnotatedInjectionMetadata(beanClass, fieldElements);
    }

    /*
    * 寻找bean中的注解的依赖对象@SisyphusReference
    * */
    private List<AnnotatedFieldElement> findFieldAnnotationMetadata(final Class<?> beanClass) {

        final List<AnnotatedFieldElement> elements = new ArrayList<>();
        // 遍历beanClass的属性
        ReflectionUtils.doWithFields(beanClass, field -> {
            // 判断beanClass中的field，是否有@SisyphusReference；有就获取一下对应的标签属性！！！
            AnnotationAttributes attributes = AnnotationUtils.getMergedAttributes(field, this.annotation, getEnvironment(), true);
            // 为空表示这个field没有被@SisyphusReference修饰（若@SisyphusReference中没有任何属性，那么这边返回的是size=0的attributes）
            if (attributes != null) {
                // @SisyphusReference不能修饰static静态方法
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
    * 表示被标签注释的类（使用是特指被@SisyphusReference注释的类）
    * TODO 暂时@SisyphusReference不接受注释方法Method，只能注释属性
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
            // 被@SisyphusReferenc修饰的类的具体类型
            Class<?> injectedType = field.getType();
            // 获取具体被@SisyphusReferenc修饰的对象（bean）！！！
            Object injectedObject = getInjectedObject(attributes, bean, beanName, injectedType, this);
            // 反射属性可写性（一般都是private修饰的）
            ReflectionUtils.makeAccessible(field);
            // 调用属性的set方法将将具体的Rpc对象（这边应该是一个Proxy代理）赋值到具体类的属性上
            field.set(bean, injectedObject);
        }

    }

    /*
    * 获取依赖的bean
    * */
    private Object getInjectedObject(AnnotationAttributes attributes, Object bean, String beanName, Class<?> injectedType,
                                       InjectionMetadata.InjectedElement injectedElement) throws Exception {
        String cacheKey = buildInjectedObjectCacheKey(attributes, bean, beanName, injectedType, injectedElement);

        Object injectedObject = injectedObjectsCache.get(cacheKey);
        // 判断缓存中是否存在
        if (injectedObject == null) {
            // 不存在，正式创建这个依赖的RPC对象，一般是一个RPC Proxy！！！
            injectedObject = doGetInjectedBean(attributes, bean, beanName, injectedType, injectedElement);
            // Customized inject-object if necessary
            injectedObjectsCache.putIfAbsent(cacheKey, injectedObject);
        }

        return injectedObject;
    }

    /*
    * 获取ReferenceBean，如果这个ReferencBean不存在，创建一个！！！
    * 实际的RPC创建的过程
    * TODO 目前不考虑@SisyphusReference  调用本地的@SisyphusService服务
    * */
    protected Object doGetInjectedBean(AnnotationAttributes attributes, Object bean, String beanName, Class<?> injectedType,
                                       InjectionMetadata.InjectedElement injectedElement) throws Exception {
        // 处理ReferenceBeanName/**/
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
        // 创建ReferenceBean
        SisyphusReferenceBean sisyphusReferenceBean = buildReferenceBeanIfAbsent(referenceBeanName, attributes, injectedType);
        // 注册ReferenceBean
        registerReferenceBean(referenceBeanName, sisyphusReferenceBean);
        // TODO injectedFieldReferenceBeanCache 暂时不缓存
        // injectedFieldReferenceBeanCache.put(injectedElement, sisyphusReferenceBean);
        // String test = environment.getProperty("sisyphus.registry.zookeeper.address");
        // 创RPC代理
        return sisyphusReferenceBean.get();
    }

    /*
    * 创建ReferenceBean
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
    * Spring注册ReferenceBean
    * */
    private void registerReferenceBean(String referenceBeanName, SisyphusReferenceBean referenceBean) {
        ConfigurableListableBeanFactory beanFactory = getBeanFactory();
        if (!beanFactory.containsBean(referenceBeanName)) {
            beanFactory.registerSingleton(referenceBeanName, referenceBean);
        }
    }

    /*
    * 封装注入对象的缓存Key = RefenceBeanName + source + attribute
    * */
    private String buildInjectedObjectCacheKey(AnnotationAttributes attributes, Object bean, String beanName,
                                                 Class<?> injectedType, InjectionMetadata.InjectedElement injectedElement) {
        return buildReferencedBeanName(attributes, injectedType) +
                "#source=" + (injectedElement.getMember()) +
                "#attributes=" + AnnotationUtils.resolvePlaceholders(attributes, getEnvironment());
    }

    /*
    * 封装ReferenceBeanName
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
