package com.xkmxz.attack_defense_capture_point_xkmxz.render;

import com.xkmxz.attack_defense_capture_point_xkmxz.network.CaptureDataSyncPayload;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端据点数据缓存 — 从服务端通过 {@link CaptureDataSyncPayload} 同步。
 * <p>
 * 客户端无法直接访问服务端的 CaptureManager，
 * 所有据点渲染数据（中心坐标、半径、颜色、是否显示）缓存在此类中，
 * 由 {@link CapturePointWorldRenderer} 读取。同时缓存区域名称列表，
 * 供方块菜单选择区域时使用。
 */
public final class ClientCaptureDataCache {

    /** 单例 */
    public static final ClientCaptureDataCache INSTANCE = new ClientCaptureDataCache();

    private final Map<String, CaptureDataSyncPayload.PointRenderData> points = new HashMap<>();
    private final List<String> zoneNames = new ArrayList<>();

    private ClientCaptureDataCache() {}

    /** 从网络包更新缓存 */
    public synchronized void updateFrom(CaptureDataSyncPayload payload) {
        points.clear();
        points.putAll(payload.points());
        zoneNames.clear();
        zoneNames.addAll(payload.zoneNames());
    }

    /** 获取所有据点渲染数据的不可修改视图 */
    public synchronized Map<String, CaptureDataSyncPayload.PointRenderData> getPoints() {
        return Collections.unmodifiableMap(new HashMap<>(points));
    }

    /** 获取客户端缓存的区域名称列表 */
    public synchronized List<String> getZoneNames() {
        return Collections.unmodifiableList(new ArrayList<>(zoneNames));
    }

    /** 清空缓存（玩家退出世界时） */
    public synchronized void clear() {
        points.clear();
        zoneNames.clear();
    }
}
