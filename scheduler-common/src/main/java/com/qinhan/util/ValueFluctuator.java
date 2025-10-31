package com.qinhan.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 数值波动工具类
 */
public class ValueFluctuator {

    /**
     * 在上次值的基础上增加一定的随机波动
     *
     * @param lastValue 上次数值
     * @param maxDelta 最大波动幅度（百分比）
     * @param min 最小值
     * @param max 最大值
     * @return 波动后的新数值
     */
    public static double fluctuate(double lastValue, double maxDelta, double min, double max) {
        double delta = ThreadLocalRandom.current().nextDouble(-maxDelta, maxDelta);
        double newValue = lastValue + delta;
        return Math.max(min, Math.min(newValue, max));
    }
}
