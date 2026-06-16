package com.xkmxz.attack_defense_capture_point_xkmxz;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 攻防战占领点 Mod 配置。
 * 提供占领相关参数的可配置化支持。
 */
@EventBusSubscriber(modid = Attack_defense_capture_point_xkmxz.MODID)
public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ---- 占领逻辑参数 ----

    /** 占领进度检查间隔（tick数），默认 10 tick (0.5秒) */
    private static final ModConfigSpec.IntValue CAPTURE_INTERVAL_TICKS = BUILDER
            .comment("Capture progress check interval in ticks (default: 10 = 0.5s)")
            .defineInRange("captureIntervalTicks", 10, 1, 200);

    /** 同步安全网间隔（tick数），默认 40 tick (2秒) */
    private static final ModConfigSpec.IntValue SYNC_INTERVAL_TICKS = BUILDER
            .comment("Sync safenet interval in ticks (default: 40 = 2s)")
            .defineInRange("syncIntervalTicks", 40, 10, 600);

    /** 据点空闲自动恢复的超时时间（tick数），默认 600 tick (30秒) */
    private static final ModConfigSpec.IntValue IDLE_TIMEOUT_TICKS = BUILDER
            .comment("Idle timeout before auto-recovery starts in ticks (default: 600 = 30s)")
            .defineInRange("idleTimeoutTicks", 600, 100, 72000);

    /** 占领/清除速度（每 tick 变化的进度值） */
    private static final ModConfigSpec.IntValue CAPTURE_SPEED = BUILDER
            .comment("Capture/neutralize speed per tick (default: 2)")
            .defineInRange("captureSpeed", 2, 1, 10);

    /** 自动恢复速度（每 tick 恢复的进度值） */
    private static final ModConfigSpec.IntValue RECOVERY_SPEED = BUILDER
            .comment("Recovery speed per tick when no players are present (default: 1)")
            .defineInRange("recoverySpeed", 1, 0, 10);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int captureIntervalTicks;
    public static int syncIntervalTicks;
    public static int idleTimeoutTicks;
    public static int captureSpeed;
    public static int recoverySpeed;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        captureIntervalTicks = CAPTURE_INTERVAL_TICKS.get();
        syncIntervalTicks = SYNC_INTERVAL_TICKS.get();
        idleTimeoutTicks = IDLE_TIMEOUT_TICKS.get();
        captureSpeed = CAPTURE_SPEED.get();
        recoverySpeed = RECOVERY_SPEED.get();
    }
}
