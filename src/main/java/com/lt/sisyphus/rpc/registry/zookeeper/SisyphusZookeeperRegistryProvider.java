package com.lt.sisyphus.rpc.registry.zookeeper;

import com.lt.sisyphus.rpc.config.provider.ProviderConfig;
import com.lt.sisyphus.rpc.registry.RpcRegistryProviderService;
import com.lt.sisyphus.rpc.registry.zookeeper.client.CuratorImpl;
import com.lt.sisyphus.rpc.registry.zookeeper.client.ZookeeperClient;
import com.lt.sisyphus.rpc.utils.FastJsonConvertUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Data
@Slf4j
public class SisyphusZookeeperRegistryProvider extends RpcZookeeperRegistryAbstract implements RpcRegistryProviderService {

    //	zookeeper address地址
    private String address;

    //	连接超时时间
    private int connectionTimeout;

    private ZookeeperClient zookeeperClient;

    public SisyphusZookeeperRegistryProvider() {}

    public void init() throws Exception {
        this.zookeeperClient = new CuratorImpl(address, connectionTimeout);
        // 初始化根节点
        if (!zookeeperClient.checkExists(ROOT_PATH)) {
            zookeeperClient.addPersistentNode(ROOT_PATH, ROOT_VALUE);
        }
    }

    /*
    * zookeeper目录结构
    * /sisyphus-rpc   --->  	rapid-rpc-1.0.0
     * 		/com.lt.sisyphus.rpc.invoke.consumer.test.HelloService:1.0.0
     * 			/providers
     * 				/192.168.11.101:5678
     * 				/192.168.11.102:5678
     * 		/com.lt.sisyphus.rpc.invoke.consumer.test.HelloService:1.0.1
     * 			/providers
     * 				/192.168.11.201:1234
     * 				/192.168.11.202:1234
    * */
    @Override
    public void registry(ProviderConfig providerConfig) throws Exception {
        String interfaceClass = providerConfig.getInterfaceClass();
        Object ref = providerConfig.getRef();
        // TODO 后续加入version，group概念
        String registryKey = ROOT_PATH + "/" + interfaceClass;

        if (!zookeeperClient.checkExists(registryKey)) {

            Method[] methods = ref.getClass().getDeclaredMethods();
            Map<String, String> methodMap = new HashMap<>();

            for (Method method : methods) {
                String methodName = method.getName();
                Class<?>[] paramterTypes = method.getParameterTypes();
                String methodValue = "";
                for (Class<?> clazz : paramterTypes) {
                    String patamterTypeName = clazz.getName();
                    methodValue += patamterTypeName + ",";
                }
                //	自己和大家演示的：hello@com.bfxy.rapid.rpc.invoke.consumer.test.User,java.lang.String
                String methodInputName = StringUtils.equals(methodValue, "") ? "" : methodValue.substring(0, methodValue.length() - 1);
                String key = methodName + "@" + methodInputName;
                // TODO methodMap中的key可以放更多的东西
                methodMap.put(key, key);
            }

            zookeeperClient.addPersistentNode(registryKey,
                    FastJsonConvertUtil.convertObjectToJSON(methodMap));
            zookeeperClient.addPersistentNode(registryKey + PROVIDERS_PATH, "");
        }

        String address = providerConfig.getServerAddress();
        String registryInstanceKey = registryKey + PROVIDERS_PATH + "/" + address;

        Map<String, String> instanceMap = new HashMap<String, String>();
        instanceMap.put("weight", providerConfig.getWeight() + "");

        //	key: /rapid-rpc/com.bfxy.rapid.rpc.invoke.consumer.test.HelloService:1.0.0/providers/127.0.0.1:5678
        //	value: instanceMap to json
        this.zookeeperClient.addEphemeralNode(registryInstanceKey,
                FastJsonConvertUtil.convertObjectToJSON(instanceMap));
    }
}
