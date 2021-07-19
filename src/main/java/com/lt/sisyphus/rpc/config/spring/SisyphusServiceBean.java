package com.lt.sisyphus.rpc.config.spring;

import com.lt.sisyphus.rpc.config.provider.ProviderConfig;
import com.lt.sisyphus.rpc.config.provider.RpcSpringExporter;
import com.lt.sisyphus.rpc.exception.RpcException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.*;
import org.springframework.context.*;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.StringUtils;

@Slf4j
@Data
public class SisyphusServiceBean implements InitializingBean, DisposableBean,
        ApplicationContextAware, ApplicationListener<ContextRefreshedEvent>, BeanNameAware,
        ApplicationEventPublisherAware {

    private static final long serialVersionUID = 6749902775007263818L;

    private transient ApplicationContext applicationContext;

    private transient String beanName;

    private String refBeanName;

    private String interfaceClass;

    private String rpcSpringExporterName;

    private RpcSpringExporter rpcSpringExporter;

    private ApplicationEventPublisher applicationEventPublisher;

    public SisyphusServiceBean() {
        super();
    }

    @Override
    public void setBeanName(String name) {
        this.beanName = name;
    }

    @Override
    public void destroy() throws Exception {

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 找到该Service指定的Exporter
        // 判断是否有指定的exporter
        if (StringUtils.hasText(rpcSpringExporterName)) {
            // 有指定的Exporter,从applicationContext中获取对应的exporter bean
            this.rpcSpringExporter = (RpcSpringExporter) applicationContext.getBean(rpcSpringExporterName);
        } else {
            // 没有指定Exporter，使用默认的Exporter，即@Configuration中指定bean
            // 判空
            if (applicationContext.getBean(RpcSpringExporter.class) == null) {
                log.error("no default exporter , please config a default exporter");
                throw new RpcException("no default exporter , please config a default exporter");
            }

            this.rpcSpringExporter = applicationContext.getBean(RpcSpringExporter.class);
        }
        // 调用exporter方法
        rpcSpringExporter.exporter(this.assembleProviderConfig());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!rpcSpringExporter.isExported()) {
            log.warn("SispyhusServiceBean is not export, try export");
            rpcSpringExporter.exporter(this.assembleProviderConfig());
        }
    }

    private ProviderConfig assembleProviderConfig() {
        // 封装ProviderConfig
        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setInterfaceClass(interfaceClass);
        providerConfig.setRef(applicationContext.getBean(refBeanName));
        return providerConfig;
    }

}
