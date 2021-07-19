package com.lt.sisyphus.rpc.registry.zookeeper;

import com.lt.sisyphus.rpc.client.RpcClient;
import com.lt.sisyphus.rpc.config.consumer.CachedService;
import com.lt.sisyphus.rpc.config.consumer.ConsumerConfig;
import com.lt.sisyphus.rpc.registry.RpcRegistryConsumerService;
import com.lt.sisyphus.rpc.registry.zookeeper.client.ChangedEvent;
import com.lt.sisyphus.rpc.registry.zookeeper.client.CuratorImpl;
import com.lt.sisyphus.rpc.registry.zookeeper.client.NodeListener;
import com.lt.sisyphus.rpc.registry.zookeeper.client.ZookeeperClient;
import com.lt.sisyphus.rpc.utils.FastJsonConvertUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Data
public class SisyphusZookeeperRegistryConsumer extends RpcZookeeperRegistryAbstract implements RpcRegistryConsumerService, NodeListener {

    //	zookeeper address地址
    private String address;

    //	连接超时时间
    private int connectionTimeout;

    private ZookeeperClient zookeeperClient;

    private ConcurrentHashMap<String, List<CachedService>> CACHED_SERVICES = new ConcurrentHashMap<String, List<CachedService>>();

    private ConcurrentHashMap<String, ConsumerConfig> CACHED_CONSUMER_CONFIGS = new ConcurrentHashMap<>();

    private final ReentrantLock LOCK = new ReentrantLock();

    public SisyphusZookeeperRegistryConsumer() {
    }

    public void init() throws Exception {
        this.zookeeperClient = new CuratorImpl(address, connectionTimeout);
        // 初始化根节点
        if (!zookeeperClient.checkExists(ROOT_PATH)) {
            zookeeperClient.addPersistentNode(ROOT_PATH, ROOT_VALUE);
        }
        this.zookeeperClient.listener4ChildrenPath(ROOT_PATH, this);
    }
    // TODO 特殊处理
    public void refreshCache(String interfaceName) {
        String refreshPath = ROOT_PATH + "/" + interfaceName + PROVIDERS_PATH;
        List<String> interfaceAddress = this.zookeeperClient.getNodes(refreshPath);
        interfaceAddress.forEach(address -> {
            String targetColumnName = refreshPath + "/" + address;
            String data = null;
            try {
                data = this.zookeeperClient.getData(targetColumnName);
            } catch (Exception e) {
                e.printStackTrace();
            }
            Map<String, String> map = FastJsonConvertUtil.convertJSONToObject(data, Map.class);
            int weight = Integer.parseInt(map.get("weight"));
            CachedService cs = new CachedService(address, weight);

            List<CachedService> addresses = CACHED_SERVICES.get(interfaceName);
            if(addresses == null) {
                //	把数据变更的节点信息加载到缓存
                CopyOnWriteArrayList<CachedService> newAddresses = new CopyOnWriteArrayList<CachedService>();
                newAddresses.add(cs);
                CACHED_SERVICES.put(interfaceName, newAddresses);

                //	创建新的ConsumerConfig对象
                ConsumerConfig consumerConfig = new ConsumerConfig();
                consumerConfig.setInterfaceClass(interfaceName);
                CopyOnWriteArrayList<String> urls = new CopyOnWriteArrayList<String>();
                for(int i = 0; i< weight; i ++) {
                    urls.add(address);
                }
                consumerConfig.setUrls(urls);
                //	初始化RpcClient
                consumerConfig.initRpcClient();
                //	继续进行缓存：
                CACHED_CONSUMER_CONFIGS.put(interfaceName, consumerConfig);

            } else {
                // 	增加一个新的列表
                addresses.add(cs);
                ConsumerConfig consumerConfig = CACHED_CONSUMER_CONFIGS.get(interfaceName);
                RpcClient rpcClient = consumerConfig.getRpcClient();
                CopyOnWriteArrayList<String> urls = new CopyOnWriteArrayList<String>();
                for(CachedService cachedService: addresses) {
                    int cWeight = cachedService.getWeight();
                    for(int i = 0 ; i < cWeight; i ++) {
                        urls.add(cachedService.getAddress());
                    }
                }
                //	更新consumerConfig里的urls
                consumerConfig.setUrls(urls);
                //	更新rpcClient里面的urls
                rpcClient.updateConnectedServer(urls);
            }
        });
    }

    @Override
    public <T> ConsumerConfig getConsumer(Class<T> clazz) {
        return CACHED_CONSUMER_CONFIGS.get(clazz.getName());
    }

    @Override
    public void nodeChanged(ZookeeperClient client, ChangedEvent event) throws Exception {
        //	节点信息
        String path = event.getPath();
        //	数据信息
        String data = event.getData();
        //	监听类型
        ChangedEvent.Type type = event.getType();

        //	节点新增的代码逻辑：
        if(ChangedEvent.Type.CHILD_ADDED == type) {

            String[] pathArray = null;
            if(!StringUtils.isBlank(path) && (pathArray = path.substring(1).split("/")).length == 2) {
                //	对根节点下的直接子节点进行继续监听，就是我们的服务权限命名+版本号的路径监听
                //	/sisyphus-rpc/com.lt.sisyphus.rpc.invoke.consumer.test.HelloService:1.0.0
                this.zookeeperClient.listener4ChildrenPath(path, this);
            }

            //	/sisyphus-rpc/com.lt.sisyphus.rpc.invoke.consumer.test.HelloService:1.0.0/providers
            if(!StringUtils.isBlank(path) && (pathArray = path.substring(1).split("/")).length == 3) {
                this.zookeeperClient.listener4ChildrenPath(path, this);
            }
            //	/sisyphus-rpc/com.lt.sisyphus.rpc.invoke.consumer.test.HelloService:1.0.0/providers/192.168.11.112
            if(!StringUtils.isBlank(path) && (pathArray = path.substring(1).split("/")).length == 4) {
                try {
                    /**
                     * pathArray ===>
                     *
                     * sisyphus-rpc [0]
                     * com.lt.sisyphus.rpc.consumer.test.HelloService:1.0.0  [1]
                     * providers [2]
                     * 192.168.11.112:8080 [3]
                     */
                    // TODO 暂时不支持version，group
                    LOCK.lock();
                    /*String interfaceClassWithV = pathArray[1];
                    String[] arrays = interfaceClassWithV.split(":");
                    String interfaceClass = arrays[0];
                    String version = arrays[1];*/
                    String interfaceClass = pathArray[1];

                    refreshCache(interfaceClass);

                } finally {
                    LOCK.unlock();
                }
            }
        }
    }
}
