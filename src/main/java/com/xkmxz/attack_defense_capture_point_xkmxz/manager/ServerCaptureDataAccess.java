package com.xkmxz.attack_defense_capture_point_xkmxz.manager;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * 服务端统一数据访问实现 — 包装 {@link CaptureManager}，委派所有操作。
 * <p>
 * 参考 synaxis 的 NetworkBlockEntitySupport 模式：
 * 将 SavedData 的直接访问封装在实现层，调用方只依赖 {@link ICaptureDataAccess} 接口。
 * <p>
 * 获取方式：{@code ICaptureDataAccess.server(level)}
 */
public class ServerCaptureDataAccess implements ICaptureDataAccess {

    private final CaptureManager manager;

    public ServerCaptureDataAccess(CaptureManager manager) {
        this.manager = manager;
    }

    // ================================================================
    //  据点操作 — 全部委派给 CaptureManager
    // ================================================================

    @Override
    public Map<String, CaptureManager.CapturePointEntry> getPoints() {
        return manager.getPoints();
    }

    @Override
    public void addOrUpdatePoint(String name, BlockPos pos) {
        manager.addOrUpdatePoint(name, pos);
    }

    @Override
    public void addOrUpdatePointWithRadius(String name, BlockPos pos, double radius) {
        manager.addOrUpdatePointWithRadius(name, pos, radius);
    }

    @Override
    public void removePoint(String name) {
        manager.removePoint(name);
    }

    @Override
    public void setPointOwner(String name, @Nullable String owner) {
        manager.setPointOwner(name, owner);
    }

    @Override
    public void setPointRadius(String name, double radius) {
        manager.setPointRadius(name, radius);
    }

    @Override
    public void setPointDisplayColor(String name, int color) {
        manager.setPointDisplayColor(name, color);
    }

    @Override
    public void setPointShowRange(String name, boolean show) {
        manager.setPointShowRange(name, show);
    }

    // ================================================================
    //  区域操作 — 全部委派给 CaptureManager
    // ================================================================

    @Override
    public Map<String, CaptureManager.ZoneEntry> getZones() {
        return manager.getZones();
    }

    @Override
    public void createZone(String name, @Nullable String requiredZone) {
        manager.createZone(name, requiredZone);
    }

    @Override
    public void removeZone(String name) {
        manager.removeZone(name);
    }

    @Override
    public void addPointToZone(String zoneName, String pointName) {
        manager.addPointToZone(zoneName, pointName);
    }

    @Override
    public void removePointFromZone(String zoneName, String pointName) {
        manager.removePointFromZone(zoneName, pointName);
    }

    @Override
    public void setZoneRequiredZone(String zoneName, @Nullable String requiredZone) {
        manager.setZoneRequiredZone(zoneName, requiredZone);
    }

    // ================================================================
    //  查询
    // ================================================================

    @Override
    @Nullable
    public String findZoneForPoint(String pointName) {
        return manager.findZoneForPoint(pointName);
    }

    @Override
    public boolean isZoneCaptured(String zoneName) {
        return manager.isZoneCaptured(zoneName);
    }

    @Override
    public boolean canAccessZone(String zoneName) {
        return manager.canAccessZone(zoneName);
    }

    // ================================================================
    //  批量 & 版本
    // ================================================================

    @Override
    public long getVersion() {
        return manager.getVersion();
    }

    @Override
    public void applyGraphSnapshot(Map<String, CaptureManager.CapturePointEntry> newPoints,
                                   Map<String, CaptureManager.ZoneEntry> newZones) {
        manager.applyGraphSnapshot(newPoints, newZones);
    }
}
