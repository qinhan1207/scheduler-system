package com.qinhan;

import com.qinhan.properties.LsaClusterConfigProperties;
import com.qinhan.service.ClusterMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
class LocalSchedulerAgentApplicationTests {

    @Autowired
    private LsaClusterConfigProperties lsaClusterConfigProperties;

    @Autowired
    private ClusterMonitorService clusterMonitorService;

    @Test
    void contextLoads() {
    }

    @Test
    public void run() throws Exception {
        log.info("🚀 启动 LSA 集群连接测试...");

        lsaClusterConfigProperties.getConfigs().parallelStream().forEach(config -> {
            try {
                clusterMonitorService.testClusterConnection(config.getKubeconfigPath());
            } catch (Exception e) {
                log.error("❌ 集群 [{}] 连接失败: {}", config.getName(), e.getMessage());
            }
        });

        log.info("✅ 所有集群连接测试完成。");
    }

    @Test
    public void testCollectClusterStatus(){
        log.info("测试成员集群信息的收集...");

        lsaClusterConfigProperties.getConfigs().parallelStream().forEach(config -> {
            try {
                clusterMonitorService.collectClusterStatus(config.getKubeconfigPath());
            } catch (Exception e) {
                log.error("❌ 集群 [{}] 连接失败: {}", config.getName(), e.getMessage());
            }
        });

        log.info("✅ 所有集群指标收集没有问题完成。");

    }


}
