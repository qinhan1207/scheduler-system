package com.qinhan.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class NetworkUtils {

    @Data
    @AllArgsConstructor
    public static class NetworkStats {
        private double avgLatency; // 毫秒
        private double lossRate;   // 百分比 (0.0 - 100.0)
    }

    /**
     * 执行系统 Ping 命令
     * @param target 目标 (IP 或 域名)
     * @param count 发包数量 (建议 5-10)
     * @param timeoutSec 等待超时时间 (秒)
     * @return NetworkStats
     */
    public static NetworkStats ping(String target, int count, int timeoutSec) {
        // -c: 次数, -W: 总超时(秒), -i: 发包间隔(秒)
        // 你的 BusyBox 支持 -i 0.2，这很好，能加快探测速度
        String cmd = String.format("ping -c %d -W %d -i 0.2 -q %s", count, timeoutSec, target);

        double avgRtt = 0.0;
        double lossRate = 100.0; // 默认全丢，防守型编程

        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd.split(" "));
            process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            
            // 正则1: 匹配 "17% packet loss"
            Pattern lossPattern = Pattern.compile("(\\d+(\\.\\d+)?)% packet loss");
            
            // 正则2: 匹配 "round-trip min/avg/max = 96.307/219.023/339.472 ms"
            // 我们需要提取中间那个 avg 值 (219.023)
            Pattern rttPattern = Pattern.compile("(rtt|round-trip) min/avg/max.*? =.*?/([\\d\\.]+)/");

            while ((line = reader.readLine()) != null) {
                if (line.contains("packet loss")) {
                    Matcher m = lossPattern.matcher(line);
                    if (m.find()) {
                        lossRate = Double.parseDouble(m.group(1));
                    }
                }
                if (line.contains("min/avg/max")) {
                    Matcher m = rttPattern.matcher(line);
                    if (m.find()) {
                        avgRtt = Double.parseDouble(m.group(2));
                    }
                }
            }
            
            boolean finished = process.waitFor(timeoutSec + 2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }

        } catch (Exception e) {
            log.error("Ping error for {}: {}", target, e.getMessage());
            return new NetworkStats(0.0, 100.0);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }

        // 兜底逻辑：如果丢包率极高，可能没有 RTT 输出，给一个惩罚值
        if (lossRate >= 99.0 && avgRtt == 0.0) {
            avgRtt = 2000.0; 
        }

        return new NetworkStats(avgRtt, lossRate);
    }
}