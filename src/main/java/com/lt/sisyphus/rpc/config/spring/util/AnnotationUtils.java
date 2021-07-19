package com.lt.sisyphus.rpc.config.spring.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.PropertyResolver;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.lang.String.valueOf;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import static org.springframework.core.annotation.AnnotatedElementUtils.getMergedAnnotation;
import static org.springframework.core.annotation.AnnotationAttributes.fromMap;
import static org.springframework.core.annotation.AnnotationUtils.getAnnotationAttributes;
import static org.springframework.core.annotation.AnnotationUtils.getDefaultValue;
import static org.springframework.util.ClassUtils.getAllInterfacesForClass;
import static org.springframework.util.ClassUtils.resolveClassName;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.ObjectUtils.containsElement;
import static org.springframework.util.ObjectUtils.nullSafeEquals;
import static org.springframework.util.StringUtils.hasText;
import static org.springframework.util.StringUtils.trimWhitespace;

@Slf4j
public class AnnotationUtils {

    /*
    * 处理@SisyhusService标签中有interface和没有interface情况下，对应注入的Interface类型
    * */
    public static Class<?> resolveServiceInterfaceClass(AnnotationAttributes attributes, Class<?> defaultInterfaceClass)
            throws IllegalArgumentException {
        // 获取ClassLoader
        ClassLoader classLoader = defaultInterfaceClass.getClassLoader();
        // 获取@SisyphusService标签中的interfaceClass属性
        Class<?> interfaceClass = getAttribute(attributes, "interfaceClass");

        // @SisyphusService标签中没有interfaceClass属性
        if (void.class.equals(interfaceClass)) {
            interfaceClass = null;
            // 获取@SisyphusService标签中的interfaceName属性
            String interfaceClassName = getAttribute(attributes, "interfaceName");

            if (hasText(interfaceClassName)) {
                // @SisyphusService标签中有interfaceName属性
                if (ClassUtils.isPresent(interfaceClassName, classLoader)) {
                    interfaceClass = resolveClassName(interfaceClassName, classLoader);
                }
            } else {
                // @SisyphusService标签中没有interfaceName属性
                Class<?>[] allInterfaces = getAllInterfacesForClass(defaultInterfaceClass);
                if (allInterfaces.length > 0) {
                    interfaceClass = allInterfaces[0];
                }
            }
        }

        // 指定的类名必须可以被classLoad加载
        Assert.notNull(interfaceClass,
                "@SisyphusService interfaceClass() or interfaceName() or interface class must be present!");
        // interfaceClass必须是一个接口
        Assert.isTrue(interfaceClass.isInterface(),
                "The annotated type must be an interface!");

        return interfaceClass;
    }

    /*
    * 解析接口名字
    * */
    public static String resolveInterfaceName(AnnotationAttributes attributes, Class<?> defaultInterfaceClass) {
        Boolean generic = getAttribute(attributes, "generic");
        if (generic != null && generic) {
            // it's a generic reference
            String interfaceClassName = getAttribute(attributes, "interfaceName");
            Assert.hasText(interfaceClassName,
                    "@Reference interfaceName() must be present when reference a generic service!");
            return interfaceClassName;
        }
        return resolveServiceInterfaceClass(attributes, defaultInterfaceClass).getName();
    }

    /*
    * 获取标签上对应的属性
    * */
    public static <T> T getAttribute(AnnotationAttributes attributes, String name) {
        return (T) attributes.get(name);
    }

    /*
    * 判断属性或者方法上是否有BPP中设置的标签SisyphusReference
    * 获取SisyphusReference中的标签属性
    * */
    public static AnnotationAttributes getMergedAttributes(AnnotatedElement annotatedElement,
                                                           Class<? extends Annotation> annotationType,
                                                           PropertyResolver propertyResolver,
                                                           boolean ignoreDefaultValue,
                                                           String... ignoreAttributeNames) {
        Annotation annotation = getMergedAnnotation(annotatedElement, annotationType);
        return annotation == null ? null : fromMap(getAttributes(annotation, propertyResolver, ignoreDefaultValue, ignoreAttributeNames));
    }

    /*
    * 获取标签上的属性
    * */
    public static Map<String, Object> getAttributes(Annotation annotation, PropertyResolver propertyResolver,
                                                    boolean ignoreDefaultValue, String... ignoreAttributeNames) {
        // 判空
        if (annotation == null) {
            return emptyMap();
        }

        Map<String, Object> attributes = getAnnotationAttributes(annotation);

        // 实际标签上的属性
        Map<String, Object> actualAttributes = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {

            String attributeName = entry.getKey();
            Object attributeValue = entry.getValue();

            // ignore default attribute value
            if (ignoreDefaultValue && nullSafeEquals(attributeValue, getDefaultValue(annotation, attributeName))) {
                continue;
            }
            actualAttributes.put(attributeName, attributeValue);
        }

        // 处理属性中的占位符
        return resolvePlaceholders(actualAttributes, propertyResolver, ignoreAttributeNames);
    }


    /*
    * 处理占位符
    * */
    public static Map<String, Object> resolvePlaceholders(Map<String, Object> sourceAnnotationAttributes,
                                                          PropertyResolver propertyResolver,
                                                          String... ignoreAttributeNames) {
        // 判空
        if (isEmpty(sourceAnnotationAttributes)) {
            return emptyMap();
        }
        // 具体解析占位符后的标签属性
        Map<String, Object> resolvedAnnotationAttributes = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : sourceAnnotationAttributes.entrySet()) {

            String attributeName = entry.getKey();

            // ignore attribute name to skip（是否存在需要忽略的标签属性）
            if (containsElement(ignoreAttributeNames, attributeName)) {
                continue;
            }

            Object attributeValue = entry.getValue();

            if (attributeValue instanceof String) {
                // 如果标签属性的值是一个String，进行占位符处理
                attributeValue = resolvePlaceholders(valueOf(attributeValue), propertyResolver);
            } else if (attributeValue instanceof String[]) {
                // 如果标签属性的值是个String[]数组，逐一进行占位符处理
                String[] values = (String[]) attributeValue;
                for (int i = 0; i < values.length; i++) {
                    values[i] = resolvePlaceholders(values[i], propertyResolver);
                }
                attributeValue = values;
            }
            resolvedAnnotationAttributes.put(attributeName, attributeValue);
        }
        return unmodifiableMap(resolvedAnnotationAttributes);
    }

    /*
    * 具体处理占位符的方法
    * */
    private static String resolvePlaceholders(String attributeValue /*待处理的属性*/, PropertyResolver propertyResolver  /*占位符处理器*/ ) {
        String resolvedValue = attributeValue;
        if (propertyResolver != null) {
            resolvedValue = propertyResolver.resolvePlaceholders(resolvedValue);
            resolvedValue = trimWhitespace(resolvedValue);
        }
        return resolvedValue;
    }
}
