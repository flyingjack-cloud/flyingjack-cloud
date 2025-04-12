package top.flyingjack.common.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.flyingjack.common.error.ErrorCode;
import top.flyingjack.common.error.exception.BusinessException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 *  雪花id生成代理：
 *  - 设置为代理是提醒使用时根据数据库实际操作使用
 *
 * 0 | 00000000000000000000000000000000000000000 | 00000  | 00000  | 000000000000
 *   |               时间差（41位）               | 数据中心（5位） | 机器（5位） | 序列号（12位）
 *
 * @author Zumin Li
 * @date 2025/4/16 12:42
 */
public class SnowflakeIdGeneratorDelegate {
    // 起始时间戳（2023-01-01）
    private final static long START_STMP = 1672531200000L;

    // 各部分位数
    private final static long SEQUENCE_BITS = 12; // 序列号位数
    private final static long MACHINE_BITS = 5;   // 机器标识位数
    private final static long DATACENTER_BITS = 5; // 数据中心位数

    // 最大值计算
    private final static long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);
    private final static long MAX_MACHINE_NUM = ~(-1L << MACHINE_BITS);
    private final static long MAX_DATACENTER_NUM = ~(-1L << DATACENTER_BITS);

    // 左移位
    private final static long MACHINE_LEFT = SEQUENCE_BITS;
    private final static long DATACENTER_LEFT = SEQUENCE_BITS + MACHINE_BITS;
    private final static long TIMESTMP_LEFT = DATACENTER_LEFT + DATACENTER_BITS;
    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdGeneratorDelegate.class);

    private final long machineId;     // 机器ID
    private final long datacenterId;  // 数据中心ID
    private final AtomicLong sequence = new AtomicLong(0);      // 序列号
    private volatile long lastStmp = -1L;     // 上次时间戳,设置为volatile表示其他线程立即可见

    // 允许的时钟回拨阈值（毫秒）
    private final static long MAX_BACKWARD_MS = 5;

    public SnowflakeIdGeneratorDelegate(long machineId, long datacenterId) {
        if (machineId > MAX_MACHINE_NUM || machineId < 0) {
            log.warn("Machine id exceed");
            throw new BusinessException(ErrorCode.INVALID_PARAM, "Machine id exceed");
        }
        if (datacenterId > MAX_DATACENTER_NUM || datacenterId < 0) {
            log.warn("Datacenter id exceed");
            throw new BusinessException(ErrorCode.INVALID_PARAM, "Datacenter id exceed");
        }
        this.machineId = machineId;
        this.datacenterId = datacenterId;
    }


    /**
     * 雪花id生成，包括处理回拨问题
     * - 线程同步的，保证多个线程不会同时使用
     *
     * @throws BusinessException 生成失败时抛出
     */
    public synchronized long nextId() {
        long currStmp = System.currentTimeMillis();

        if (currStmp < lastStmp) {
            // 处理时钟回拨
            long backwardMs = lastStmp - currStmp;

            // 小于允许回拨等待时间差时，线程等待
            if (backwardMs <= MAX_BACKWARD_MS) {
                try {
                    TimeUnit.MILLISECONDS.sleep(backwardMs << 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Thread failed to wait(time backward)", e);
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Thread failed to wait(time backward)");
                }

                // 等待后如果还是存在回拨问题，抛出异常
                currStmp = System.currentTimeMillis();
                if (currStmp < lastStmp) {
                    log.warn("Time backward exceeded, refused");
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR,"Time backward exceeded, refused");
                }
            } else {
                log.warn("Unacceptable time backward value -{}ms", backwardMs);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Unacceptable time backward value");
            }
        }

        // 如果时间戳和上次一致，证明时同一毫秒生成，继续递增序列号
        if (currStmp == lastStmp) {
            sequence.set((sequence.get() + 1 ) & MAX_SEQUENCE); // 通过与操作，限制不会超过最大值
            if (sequence.get() == 0L) {  // 如果归0了，证明序列超过了最大值，使用下个毫秒生成
                currStmp = getNextMill();
            }
        } else {
            sequence.set(0L);
        }

        // 记录当前时间戳
        lastStmp = currStmp;

        // 通过位移算法，拼接各个bit上的值
        return (currStmp - START_STMP) << TIMESTMP_LEFT
                | datacenterId << DATACENTER_LEFT
                | machineId << MACHINE_LEFT
                | sequence.get();
    }

    // 通过循环调用获取下一毫秒时间戳
    private long getNextMill() {
        long mill = System.currentTimeMillis();
        while (mill <= lastStmp) {
            mill = System.currentTimeMillis();
        }
        return mill;
    }
}
