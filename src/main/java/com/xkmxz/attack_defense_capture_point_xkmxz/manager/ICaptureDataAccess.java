package com.xkmxz.attack_defense_capture_point_xkmxz.manager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 统一的核心数据访问接口 — 所有读取、查询和写入操作都通过此接口进行。
 * <p>
 * 设计参考 synaxis 的 NetworkBlockEntityAccess / CimulinkKindSupport 架构：
 * <ul>
 *   <li>方块（CapturePointBlockEntity 网络处理器）使用此接口读写数据</li>
 *   <li>命令（ModCommands）使用此接口读写数据</li>
 *   <li>GUI（CapturePointGraphScreen / CapturePointGraphDialogs）使用此接口查询数据</li>
 * </ul>
 * <p>
 * 服务端通过 {@link #server(ServerLevel)} 获取实现，
 * 客户端可通过 {@link #client(Level)} 获取回退代理（查询受限）。
 */
public interface ICaptureDataAccess {

    // ================================================================
    //  工厂方法
    // ================================================================

    /** 获取服务端数据访问实例（服务端专用，客户端调用会抛出 IllegalStateException） */
    static ICaptureDataAccess server(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("ICaptureDataAccess.server() can only be called on server side");
        }
        return new ServerCaptureDataAccess(CaptureManager.get(serverLevel));
    }

    /** 获取服务端数据访问实例（从 ServerLevel 直接获取） */
    static ICaptureDataAccess server(ServerLevel level) {
        return new ServerCaptureDataAccess(CaptureManager.get(level));
    }

    // ================================================================
    //  据点 (Capture Point) 操作
    // ================================================================

    /** 获取所有据点的不可修改视图 */
    Map<String, CaptureManager.CapturePointEntry> getPoints();

    /** 获取指定名称的据点，不存在返回 null */
    @Nullable
    default CaptureManager.CapturePointEntry getPoint(String name) {
        return getPoints().get(name);
    }

    /** 判断据点是否存在 */
    default boolean hasPoint(String name) {
        return getPoints().containsKey(name);
    }

    /** 添加或更新据点（使用默认半径和颜色） */
    void addOrUpdatePoint(String name, BlockPos pos);

    /** 添加或更新据点（指定半径） */
    void addOrUpdatePointWithRadius(String name, BlockPos pos, double radius);

    /** 移除据点 */
    void removePoint(String name);

    /** 设置据点占领状态（同步规则：会触发所属区域的占领状态重新计算） */
    void setPointCaptured(String name, boolean captured);

    /** 设置据点的占领队伍（ownerTeam）。如果队伍不为 null，自动标记为已占领。 */
    void setPointOwnerTeam(String name, @Nullable String team);

    /** 设置据点正在占领的队伍（capturingTeam）。null 表示无队伍正在占领。 */
    void setPointCapturingTeam(String name, @Nullable String team);

    /** 设置据点的占领进度（0-100）。达到 100 时自动完成占领。 */
    void setPointCaptureProgress(String name, int progress);

    /** 设置据点半径 */
    void setPointRadius(String name, double radius);

    /** 设置据点显示颜色 */
    void setPointDisplayColor(String name, int color);

    /** 设置据点是否显示范围 */
    void setPointShowRange(String name, boolean show);

    // ================================================================
    //  区域 (Zone) 操作
    // ================================================================

    /** 获取所有区域的不可修改视图 */
    Map<String, CaptureManager.ZoneEntry> getZones();

    /** 获取指定名称的区域，不存在返回 null */
    @Nullable
    default CaptureManager.ZoneEntry getZone(String name) {
        return getZones().get(name);
    }

    /** 判断区域是否存在 */
    default boolean hasZone(String name) {
        return getZones().containsKey(name);
    }

    /** 创建区域（可选指定前置依赖区域） */
    void createZone(String name, @Nullable String requiredZone);

    /** 移除区域 */
    void removeZone(String name);

    /** 将据点添加到区域 */
    void addPointToZone(String zoneName, String pointName);

    /** 将据点从区域移除 */
    void removePointFromZone(String zoneName, String pointName);

    /** 设置或清除区域的依赖区域 */
    void setZoneRequiredZone(String zoneName, @Nullable String requiredZone);

    /**
     * 设置区域占领状态，并同步到区域内所有据点（双向同步规则③）。
     * 区域已占领 → 所有子据点标记为已占领
     * 区域未占领 → 所有子据点标记为未占领
     */
    void setZoneCaptured(String zoneName, boolean captured);

    // ================================================================
    //  查询方法
    // ================================================================

    /** 查找据点所属的区域名称，不在任何区域则返回 null */
    @Nullable String findZoneForPoint(String pointName);

    /** 判断区域是否已被占领（返回区域自身的 captured 状态） */
    boolean isZoneCaptured(String zoneName);

    /** 判断区域是否可访问（前置区域已占领或没有前置依赖） */
    boolean canAccessZone(String zoneName);

    // ================================================================
    //  批量操作 & 版本控制
    // ================================================================

    /** 获取当前数据版本号，用于 GUI 冲突检测 */
    long getVersion();

    /** 批量应用完整数据快照（GUI 编辑模式保存的唯一入口） */
    void applyGraphSnapshot(Map<String, CaptureManager.CapturePointEntry> newPoints,
                            Map<String, CaptureManager.ZoneEntry> newZones);
}
