package com.xkmxz.attack_defense_capture_point_xkmxz.manager;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

public class CaptureManager extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "capture_points_data";
    private static final String TAG_VERSION = "version";

    private final Map<String, CapturePointEntry> points = new LinkedHashMap<>();
    private final Map<String, ZoneEntry> zones = new LinkedHashMap<>();
    private final Map<String, NodeLayout> nodeLayouts = new LinkedHashMap<>();
    private final Map<String, DecisionNodeData> decisionNodes = new LinkedHashMap<>();
    /** 通用节点选项：nodeName → { optionId → optionValue } 用于持久化条件/逻辑门/动作/常量等节点配置 */
    private final Map<String, Map<String, String>> nodeOptions = new LinkedHashMap<>();
    private final List<GraphWireData> graphWires = new ArrayList<>();
    /** 节点图视角状态（平移位置 + 缩放），null 表示使用默认视角（fit to children） */
    @Nullable
    private ViewState viewState = null;
    private long version = 0;
    @Nullable
    private String defenderTeam = null; // 默认防守方队伍，不为 null 时所有据点初始归此队伍

    // ---- Data Records ----

    public record CapturePointEntry(String name, BlockPos pos, boolean captured,
                                    @Nullable String ownerTeam,
                                    @Nullable String capturingTeam,
                                    int captureProgress,
                                    @Nullable String lastOwnerTeam,
                                    double radius, int displayColor, boolean showRange) {
        public static final double DEFAULT_RADIUS = 5.0;
        public static final int DEFAULT_COLOR = 0xFFFF4444;
        /** 占领进度最大值 (0-100) */
        public static final int MAX_PROGRESS = 100;
        /** 自动恢复超时 tick 数 (30 秒) */
        public static final int IDLE_TIMEOUT_TICKS = 600;
        /** 自动恢复速度 (每 tick +1) */
        public static final int RECOVERY_SPEED = 1;

        public CompoundTag toNbt() {
            var tag = new CompoundTag();
            tag.putString("name", name);
            tag.putInt("x", pos.getX());
            tag.putInt("y", pos.getY());
            tag.putInt("z", pos.getZ());
            tag.putBoolean("captured", captured);
            if (ownerTeam != null) tag.putString("ownerTeam", ownerTeam);
            if (capturingTeam != null) tag.putString("capturingTeam", capturingTeam);
            if (captureProgress > 0) tag.putInt("captureProgress", captureProgress);
            if (lastOwnerTeam != null) tag.putString("lastOwnerTeam", lastOwnerTeam);
            tag.putDouble("radius", radius);
            tag.putInt("displayColor", displayColor);
            tag.putBoolean("showRange", showRange);
            return tag;
        }

        public static CapturePointEntry fromNbt(CompoundTag tag) {
            var name = tag.getString("name");
            var pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            var captured = tag.contains("captured") && tag.getBoolean("captured");
            var ownerTeam = tag.contains("ownerTeam") ? tag.getString("ownerTeam") : null;
            var capturingTeam = tag.contains("capturingTeam") ? tag.getString("capturingTeam") : null;
            var captureProgress = tag.contains("captureProgress") ? tag.getInt("captureProgress") : 0;
            var lastOwnerTeam = tag.contains("lastOwnerTeam") ? tag.getString("lastOwnerTeam") : null;
            var radius = tag.contains("radius") ? tag.getDouble("radius") : DEFAULT_RADIUS;
            var displayColor = tag.contains("displayColor") ? tag.getInt("displayColor") : DEFAULT_COLOR;
            var showRange = tag.contains("showRange") && tag.getBoolean("showRange");
            return new CapturePointEntry(name, pos, captured, ownerTeam, capturingTeam, captureProgress, lastOwnerTeam, radius, displayColor, showRange);
        }

        public CapturePointEntry withCaptured(boolean newCaptured) {
            return new CapturePointEntry(name, pos, newCaptured, newCaptured ? ownerTeam : null, newCaptured ? capturingTeam : null, newCaptured ? MAX_PROGRESS : 0, newCaptured ? null : lastOwnerTeam, radius, displayColor, showRange);
        }

        public CapturePointEntry withOwnerTeam(@Nullable String newOwnerTeam) {
            return new CapturePointEntry(name, pos, newOwnerTeam != null, newOwnerTeam, capturingTeam, captureProgress, lastOwnerTeam, radius, displayColor, showRange);
        }

        public CapturePointEntry withCapturingTeam(@Nullable String newCapturingTeam) {
            return new CapturePointEntry(name, pos, captured, ownerTeam, newCapturingTeam, captureProgress, lastOwnerTeam, radius, displayColor, showRange);
        }

        public CapturePointEntry withCaptureProgress(int newProgress) {
            boolean newCaptured = newProgress >= MAX_PROGRESS;
            String newOwner = newCaptured ? (ownerTeam != null ? ownerTeam : capturingTeam) : ownerTeam;
            String newLastOwner = (!newCaptured && ownerTeam != null && capturingTeam != null && !ownerTeam.equals(capturingTeam)) ? ownerTeam : lastOwnerTeam;
            return new CapturePointEntry(name, pos, newCaptured, newOwner, capturingTeam, Math.min(newProgress, MAX_PROGRESS), newLastOwner, radius, displayColor, showRange);
        }

        public CapturePointEntry withLastOwnerTeam(@Nullable String newLastOwnerTeam) {
            return new CapturePointEntry(name, pos, captured, ownerTeam, capturingTeam, captureProgress, newLastOwnerTeam, radius, displayColor, showRange);
        }

        public CapturePointEntry withRadius(double newRadius) {
            return new CapturePointEntry(name, pos, captured, ownerTeam, capturingTeam, captureProgress, lastOwnerTeam, newRadius, displayColor, showRange);
        }

        public CapturePointEntry withDisplayColor(int newColor) {
            return new CapturePointEntry(name, pos, captured, ownerTeam, capturingTeam, captureProgress, lastOwnerTeam, radius, newColor, showRange);
        }

        public CapturePointEntry withShowRange(boolean newShowRange) {
            return new CapturePointEntry(name, pos, captured, ownerTeam, capturingTeam, captureProgress, lastOwnerTeam, radius, displayColor, newShowRange);
        }
    }

    public record ZoneEntry(String name, List<String> capturePoints, @Nullable String requiredZone, boolean captured,
                            @Nullable String ownerTeam, List<String> unlockDependencies, boolean locked) {

        public CompoundTag toNbt() {
            var tag = new CompoundTag();
            tag.putString("name", name);
            var list = new ListTag();
            for (var cp : capturePoints) {
                list.add(StringTag.valueOf(cp));
            }
            tag.put("capturePoints", list);
            if (requiredZone != null) tag.putString("requiredZone", requiredZone);
            tag.putBoolean("captured", captured);
            if (ownerTeam != null) tag.putString("ownerTeam", ownerTeam);
            if (unlockDependencies != null && !unlockDependencies.isEmpty()) {
                var unlockList = new ListTag();
                for (var dep : unlockDependencies) {
                    unlockList.add(StringTag.valueOf(dep));
                }
                tag.put("unlockDependencies", unlockList);
            }
            if (locked) tag.putBoolean("locked", true);
            return tag;
        }

        public static ZoneEntry fromNbt(CompoundTag tag) {
            var name = tag.getString("name");
            var list = tag.getList("capturePoints", Tag.TAG_STRING);
            var points = new ArrayList<String>();
            for (int i = 0; i < list.size(); i++) {
                points.add(list.getString(i));
            }
            var requiredZone = tag.contains("requiredZone") ? tag.getString("requiredZone") : null;
            var captured = tag.contains("captured") && tag.getBoolean("captured");
            var ownerTeam = tag.contains("ownerTeam") ? tag.getString("ownerTeam") : null;
            var unlockDeps = new ArrayList<String>();
            if (tag.contains("unlockDependencies", Tag.TAG_LIST)) {
                var unlockList = tag.getList("unlockDependencies", Tag.TAG_STRING);
                for (int i = 0; i < unlockList.size(); i++) {
                    unlockDeps.add(unlockList.getString(i));
                }
            }
            var locked = tag.contains("locked") && tag.getBoolean("locked");
            return new ZoneEntry(name, points, requiredZone, captured, ownerTeam, unlockDeps, locked);
        }

        public ZoneEntry withCaptured(boolean newCaptured) {
            return new ZoneEntry(name, capturePoints, requiredZone, newCaptured, ownerTeam, unlockDependencies, locked);
        }

        public ZoneEntry withOwnerTeam(@Nullable String newOwnerTeam) {
            return new ZoneEntry(name, capturePoints, requiredZone, captured, newOwnerTeam, unlockDependencies, locked);
        }

        public ZoneEntry withCapturePoints(List<String> newCapturePoints) {
            return new ZoneEntry(name, newCapturePoints, requiredZone, captured, ownerTeam, unlockDependencies, locked);
        }

        public ZoneEntry withRequiredZone(@Nullable String newRequiredZone) {
            return new ZoneEntry(name, capturePoints, newRequiredZone, captured, ownerTeam, unlockDependencies, locked);
        }

        public ZoneEntry withUnlockDependencies(List<String> newUnlockDeps) {
            return new ZoneEntry(name, capturePoints, requiredZone, captured, ownerTeam, newUnlockDeps, locked);
        }

        public ZoneEntry withLocked(boolean newLocked) {
            return new ZoneEntry(name, capturePoints, requiredZone, captured, ownerTeam, unlockDependencies, newLocked);
        }
    }

    // ---- Node Layout (视觉布局，不参与游戏逻辑) ----

    public record NodeLayout(float x, float y) {
        private static final String TAG_X = "x";
        private static final String TAG_Y = "y";

        public CompoundTag toNbt() {
            var tag = new CompoundTag();
            tag.putFloat(TAG_X, x);
            tag.putFloat(TAG_Y, y);
            return tag;
        }

        public static NodeLayout fromNbt(CompoundTag tag) {
            return new NodeLayout(tag.getFloat(TAG_X), tag.getFloat(TAG_Y));
        }
    }

    // ---- Decision Node Data (判断器节点数据持久化) ----

    public record DecisionNodeData(String name, float x, float y,
                                    String condition, @Nullable String targetTeam,
                                    int progressThreshold) {
        private static final String TAG_NAME = "name";
        private static final String TAG_X = "x";
        private static final String TAG_Y = "y";
        private static final String TAG_CONDITION = "condition";
        private static final String TAG_TARGET_TEAM = "targetTeam";
        private static final String TAG_PROGRESS = "progressThreshold";

        public CompoundTag toNbt() {
            var tag = new CompoundTag();
            tag.putString(TAG_NAME, name);
            tag.putFloat(TAG_X, x);
            tag.putFloat(TAG_Y, y);
            tag.putString(TAG_CONDITION, condition);
            if (targetTeam != null && !targetTeam.isEmpty()) tag.putString(TAG_TARGET_TEAM, targetTeam);
            tag.putInt(TAG_PROGRESS, progressThreshold);
            return tag;
        }

        public static DecisionNodeData fromNbt(CompoundTag tag) {
            return new DecisionNodeData(
                    tag.getString(TAG_NAME),
                    tag.getFloat(TAG_X),
                    tag.getFloat(TAG_Y),
                    tag.getString(TAG_CONDITION),
                    tag.contains(TAG_TARGET_TEAM) ? tag.getString(TAG_TARGET_TEAM) : null,
                    tag.contains(TAG_PROGRESS) ? tag.getInt(TAG_PROGRESS) : 50
            );
        }
    }

    // ---- View State (节点图视角状态) ----

    public record ViewState(float offsetX, float offsetY, float scale, float canvasWidth, float canvasHeight) {
        private static final String TAG_OFFSET_X = "offsetX";
        private static final String TAG_OFFSET_Y = "offsetY";
        private static final String TAG_SCALE = "scale";
        private static final String TAG_CANVAS_WIDTH = "canvasWidth";
        private static final String TAG_CANVAS_HEIGHT = "canvasHeight";

        public CompoundTag toNbt() {
            var tag = new CompoundTag();
            tag.putFloat(TAG_OFFSET_X, offsetX);
            tag.putFloat(TAG_OFFSET_Y, offsetY);
            tag.putFloat(TAG_SCALE, scale);
            tag.putFloat(TAG_CANVAS_WIDTH, canvasWidth);
            tag.putFloat(TAG_CANVAS_HEIGHT, canvasHeight);
            return tag;
        }

        public static ViewState fromNbt(CompoundTag tag) {
            return new ViewState(
                    tag.getFloat(TAG_OFFSET_X),
                    tag.getFloat(TAG_OFFSET_Y),
                    tag.getFloat(TAG_SCALE),
                    tag.contains(TAG_CANVAS_WIDTH) ? tag.getFloat(TAG_CANVAS_WIDTH) : 800f,
                    tag.contains(TAG_CANVAS_HEIGHT) ? tag.getFloat(TAG_CANVAS_HEIGHT) : 800f
            );
        }
    }

    public record GraphWireData(String fromNode, String fromPort, String toNode, String toPort) {
        public CompoundTag toNbt() {
            var tag = new CompoundTag();
            tag.putString("fromNode", fromNode);
            tag.putString("fromPort", fromPort);
            tag.putString("toNode", toNode);
            tag.putString("toPort", toPort);
            return tag;
        }

        public static GraphWireData fromNbt(CompoundTag tag) {
            return new GraphWireData(
                    tag.getString("fromNode"),
                    tag.getString("fromPort"),
                    tag.getString("toNode"),
                    tag.getString("toPort")
            );
        }
    }

    public void setViewState(@Nullable ViewState viewState) {
        this.viewState = viewState;
        setDirty();
    }

    @Nullable
    public ViewState getViewState() {
        return viewState;
    }

    // ---- Singleton Access ----

    public static CaptureManager get(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("CaptureManager can only be accessed from server side");
        }
        DimensionDataStorage storage = serverLevel.getDataStorage();
        return storage.computeIfAbsent(new SavedData.Factory<>(CaptureManager::new, CaptureManager::new), DATA_NAME);
    }

    public CaptureManager() {
    }

    public CaptureManager(CompoundTag tag, HolderLookup.Provider registries) {
        load(tag, registries);
    }

    // ---- Version & Batch Write ----

    /** 获取当前数据版本号，每次写入递增 */
    public long getVersion() {
        return version;
    }

    /** 递增版本号并标记脏数据 */
    private void bumpVersion() {
        version++;
        setDirty();
    }

    /**
     * 批量应用 GUI 编辑器的完整数据快照（原子操作，版本号只递增一次）。
     * 此方法是 GUI 编辑模式保存时的唯一写入入口，
     * 命令和方块的其他单次写入仍走各自的 setXxx 方法。
     */
    public void applyGraphSnapshot(Map<String, CapturePointEntry> newPoints,
                                    Map<String, ZoneEntry> newZones) {
        points.clear();
        zones.clear();
        points.putAll(newPoints);
        zones.putAll(newZones);
        bumpVersion();
        LOGGER.info("Applied graph snapshot: {} points, {} zones (version {})",
                points.size(), zones.size(), version);
    }

    /**
     * 批量应用 GUI 编辑器的完整数据快照 + 节点布局 + 节点选项 + 视角状态。
     */
    public void applyGraphSnapshotWithLayout(Map<String, CapturePointEntry> newPoints,
                                              Map<String, ZoneEntry> newZones,
                                              Map<String, NodeLayout> layouts,
                                              Map<String, DecisionNodeData> decisions,
                                              Map<String, Map<String, String>> nodeOpts,
                                              List<GraphWireData> wires,
                                              @Nullable ViewState viewState) {
        points.clear();
        zones.clear();
        points.putAll(newPoints);
        zones.putAll(newZones);
        nodeLayouts.clear();
        nodeLayouts.putAll(layouts);
        decisionNodes.clear();
        decisionNodes.putAll(decisions);
        nodeOptions.clear();
        nodeOptions.putAll(nodeOpts);
        graphWires.clear();
        graphWires.addAll(wires);
        this.viewState = viewState;
        bumpVersion();
        LOGGER.info("Applied graph snapshot: {} points, {} zones, {} layouts, {} decisions, {} nodeOptions, {} wires (version {})",
                points.size(), zones.size(), layouts.size(), decisions.size(), nodeOpts.size(), wires.size(), version);
    }

    // ---- Node Layout ----

    // ---- Data Access ----

    public Map<String, CapturePointEntry> getPoints() {
        return Collections.unmodifiableMap(points);
    }

    public Map<String, ZoneEntry> getZones() {
        return Collections.unmodifiableMap(zones);
    }

    /** 获取所有节点布局的不可修改视图 */
    public Map<String, NodeLayout> getNodeLayouts() {
        return Collections.unmodifiableMap(nodeLayouts);
    }

    /** 获取所有判断器节点数据的不可修改视图 */
    public Map<String, DecisionNodeData> getDecisionNodes() {
        return Collections.unmodifiableMap(decisionNodes);
    }

    /** 获取所有通用节点选项的不可修改视图 */
    public Map<String, Map<String, String>> getNodeOptions() {
        return Collections.unmodifiableMap(nodeOptions);
    }

    /** 获取所有连线数据的不可修改视图 */
    public List<GraphWireData> getGraphWires() {
        return Collections.unmodifiableList(graphWires);
    }

    public void addOrUpdatePoint(String name, BlockPos pos) {
        points.put(name, new CapturePointEntry(name, pos, false,
                null, null, 0, null,
                CapturePointEntry.DEFAULT_RADIUS, CapturePointEntry.DEFAULT_COLOR, false));
        bumpVersion();
    }

    public void addOrUpdatePointWithRadius(String name, BlockPos pos, double radius) {
        points.put(name, new CapturePointEntry(name, pos, false,
                null, null, 0, null,
                radius, CapturePointEntry.DEFAULT_COLOR, false));
        bumpVersion();
    }

    public void removePoint(String name) {
        points.remove(name);
        bumpVersion();
    }

    public void setPointCaptured(String name, boolean captured) {
        var existing = points.get(name);
        if (existing != null) {
            points.put(name, existing.withCaptured(captured));
            // 重新计算所属区域的占领状态（双向同步规则②）
            recalcZoneCapturedForPoint(name);
            bumpVersion();
        }
    }

    /** 设置据点的占领队伍（ownerTeam）。如果队伍不为 null，自动标记为已占领。 */
    public void setPointOwnerTeam(String name, @Nullable String team) {
        var existing = points.get(name);
        if (existing != null) {
            points.put(name, existing.withOwnerTeam(team));
            recalcZoneCapturedForPoint(name);
            bumpVersion();
        }
    }

    /** 设置据点正在占领的队伍（capturingTeam）。null 表示无队伍正在占领。 */
    public void setPointCapturingTeam(String name, @Nullable String team) {
        var existing = points.get(name);
        if (existing != null) {
            points.put(name, existing.withCapturingTeam(team));
            bumpVersion();
        }
    }

    /** 设置据点的占领进度（0-100）。达到 100 时自动完成占领。 */
    public void setPointCaptureProgress(String name, int progress) {
        var existing = points.get(name);
        if (existing != null) {
            int clamped = Math.max(0, Math.min(progress, CapturePointEntry.MAX_PROGRESS));
            points.put(name, existing.withCaptureProgress(clamped));
            recalcZoneCapturedForPoint(name);
            bumpVersion();
        }
    }

    /** 设置据点的上一任占领者（用于自动恢复）。 */
    public void setPointLastOwnerTeam(String name, @Nullable String lastOwner) {
        var existing = points.get(name);
        if (existing != null) {
            points.put(name, existing.withLastOwnerTeam(lastOwner));
            bumpVersion();
        }
    }

    /** 设置区域的占领状态，并同步到区域内所有据点（双向同步规则③） */
    public void setZoneCaptured(String zoneName, boolean captured) {
        var zone = zones.get(zoneName);
        if (zone != null) {
            // 先更新区域本身
            zones.put(zoneName, zone.withCaptured(captured));
            // 同步到所有子据点
            for (var cpName : zone.capturePoints()) {
                var cp = points.get(cpName);
                if (cp != null) {
                    points.put(cpName, cp.withCaptured(captured));
                }
            }
            bumpVersion();
        }
    }

    /** 据点状态变更后，重新计算其所属区域的占领状态 + 多数决 ownerTeam */
    private void recalcZoneCapturedForPoint(String pointName) {
        String zoneName = findZoneForPoint(pointName);
        if (zoneName == null) return;
        var zone = zones.get(zoneName);
        if (zone == null) return;

        // 计算 captured：所有据点都被占领
        boolean allCaptured = true;
        for (var cpName : zone.capturePoints()) {
            var cp = points.get(cpName);
            if (cp == null || !cp.captured()) {
                allCaptured = false;
                break;
            }
        }

        // 计算 ownerTeam：多数决
        String newOwnerTeam = null;
        var teamCount = new HashMap<String, Integer>();
        for (var cpName : zone.capturePoints()) {
            var cp = points.get(cpName);
            if (cp != null && cp.ownerTeam() != null) {
                teamCount.merge(cp.ownerTeam(), 1, Integer::sum);
            }
        }
        if (!teamCount.isEmpty()) {
            int maxCount = Collections.max(teamCount.values());
            // 筛选出达到 maxCount 的队伍，只有唯一一个才设为 ownerTeam
            var topTeams = teamCount.entrySet().stream()
                    .filter(e -> e.getValue() == maxCount)
                    .toList();
            if (topTeams.size() == 1) {
                newOwnerTeam = topTeams.get(0).getKey();
            }
        }

        zones.put(zoneName, zone.withCaptured(allCaptured).withOwnerTeam(newOwnerTeam));
    }

    public void setPointRadius(String name, double radius) {
        var existing = points.get(name);
        if (existing != null) {
            points.put(name, existing.withRadius(radius));
            bumpVersion();
        }
    }

    public void setPointDisplayColor(String name, int color) {
        var existing = points.get(name);
        if (existing != null) {
            points.put(name, existing.withDisplayColor(color));
            bumpVersion();
        }
    }

    public void setPointShowRange(String name, boolean showRange) {
        var existing = points.get(name);
        if (existing != null) {
            points.put(name, existing.withShowRange(showRange));
            bumpVersion();
        }
    }

    public void createZone(String name, @Nullable String requiredZone) {
        zones.put(name, new ZoneEntry(name, new ArrayList<>(), requiredZone, false, null, new ArrayList<>(), false));
        bumpVersion();
    }

    public void removeZone(String name) {
        zones.remove(name);
        bumpVersion();
    }

    public void addPointToZone(String zoneName, String pointName) {
        var zone = zones.get(zoneName);
        if (zone != null) {
            var newList = new ArrayList<>(zone.capturePoints());
            if (!newList.contains(pointName)) {
                newList.add(pointName);
                zones.put(zoneName, new ZoneEntry(zone.name(), newList, zone.requiredZone(), zone.captured(), zone.ownerTeam(), zone.unlockDependencies(), zone.locked()));
                bumpVersion();
            }
        }
    }

    public void removePointFromZone(String zoneName, String pointName) {
        var zone = zones.get(zoneName);
        if (zone != null) {
            var newList = new ArrayList<>(zone.capturePoints());
            newList.remove(pointName);
            zones.put(zoneName, new ZoneEntry(zone.name(), newList, zone.requiredZone(), zone.captured(), zone.ownerTeam(), zone.unlockDependencies(), zone.locked()));
            bumpVersion();
        }
    }

    /**
     * 设置区域的依赖区域（requiredZone），即攻防模式下前置区域。
     * 此方法直接操作内部 zones 列表，不同于 getZones() 返回的不可修改视图。
     * @param zoneName 要修改的区域名称
     * @param requiredZone 依赖的前置区域名称（null 表示清除依赖）
     */
    public void setZoneRequiredZone(String zoneName, @Nullable String requiredZone) {
        var zone = zones.get(zoneName);
        if (zone != null) {
            // 保留原有据点列表和占领状态，仅修改区域依赖
            zones.put(zoneName, new ZoneEntry(zone.name(), new ArrayList<>(zone.capturePoints()), requiredZone, zone.captured(), zone.ownerTeam(), zone.unlockDependencies(), zone.locked()));
            bumpVersion();
        }
    }

    /**
     * 设置区域的解锁依赖列表。
     * 解锁依赖控制区域的运行时可访问性，由逻辑组件（CaptureActionNode）在运行时控制。
     * @param zoneName 区域名称
     * @param unlockDeps 解锁依赖的区域名称列表
     */
    public void setZoneUnlockDependencies(String zoneName, List<String> unlockDeps) {
        var zone = zones.get(zoneName);
        if (zone != null) {
            zones.put(zoneName, new ZoneEntry(zone.name(), new ArrayList<>(zone.capturePoints()), zone.requiredZone(), zone.captured(), zone.ownerTeam(), unlockDeps, zone.locked()));
            bumpVersion();
        }
    }

    public void setZoneLocked(String zoneName, boolean locked) {
        var zone = zones.get(zoneName);
        if (zone != null && zone.locked() != locked) {
            zones.put(zoneName, zone.withLocked(locked));
            bumpVersion();
        }
    }

    /**
     * 查找包含指定据点的区域名称。
     * @return 区域名称，如果据点不属于任何区域则返回 null
     */
    @Nullable
    public String findZoneForPoint(String pointName) {
        for (var entry : zones.entrySet()) {
            if (entry.getValue().capturePoints().contains(pointName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public boolean isZoneCaptured(String zoneName) {
        var zone = zones.get(zoneName);
        if (zone == null) return false;
        return zone.captured();
    }

    public boolean canAccessZone(String zoneName) {
        return canAccessZone(zoneName, new HashSet<>());
    }

    private boolean canAccessZone(String zoneName, Set<String> visiting) {
        var zone = zones.get(zoneName);
        if (zone == null) return false;

        if (zone.locked()) return false;

        if (!visiting.add(zoneName)) {
            LOGGER.warn("Detected zone dependency cycle while resolving access for '{}'", zoneName);
            return false;
        }

        try {
            if (zone.requiredZone() != null && !zone.requiredZone().isEmpty()) {
                var required = zones.get(zone.requiredZone());
                if (required == null || !required.captured()) {
                    return false;
                }
            }

            // 解锁依赖由逻辑组件（CaptureActionNode）在运行时通过 CaptureManager API 控制。
            if (zone.unlockDependencies() != null && !zone.unlockDependencies().isEmpty()) {
                for (var dep : zone.unlockDependencies()) {
                    var depZone = zones.get(dep);
                    if (depZone == null || !depZone.captured()) return false;
                    if (!canAccessZone(dep, visiting)) return false;
                }
                return true; // 所有解锁依赖的区域都可访问
            }

            // 无解锁依赖 → 区域无解锁约束，默认可访问
            return true;
        } finally {
            visiting.remove(zoneName);
        }
    }

    // ---- Defender Team (攻防模式) ----

    /** 获取当前防守方队伍。null 表示无防守方（自由占领模式）。 */
    @Nullable
    public String getDefenderTeam() {
        return defenderTeam;
    }

    /** 设置防守方队伍，并将所有据点和区域初始化为该队伍占领。null 表示清除防守方。 */
    public void setDefenderTeam(@Nullable String team) {
        this.defenderTeam = team;
        if (team != null) {
            // 将所有据点设为防守队伍占领
            for (var entry : List.copyOf(points.entrySet())) {
                var name = entry.getKey();
                var point = entry.getValue();
                points.put(name, point.withOwnerTeam(team));
            }
            // 将所有区域设为已占领 + 防守方队伍
            for (var entry : List.copyOf(zones.entrySet())) {
                var name = entry.getKey();
                var zone = entry.getValue();
                zones.put(name, zone.withCaptured(true).withOwnerTeam(team));
            }
        }
        bumpVersion();
        LOGGER.info("Defender team set to: {}", team);
    }

    // ---- Persistence ----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var pointsList = new ListTag();
        for (var entry : points.values()) {
            pointsList.add(entry.toNbt());
        }
        tag.put("points", pointsList);

        var zonesList = new ListTag();
        for (var entry : zones.values()) {
            zonesList.add(entry.toNbt());
        }
        tag.put("zones", zonesList);

        tag.putLong(TAG_VERSION, version);
        if (defenderTeam != null) tag.putString("defenderTeam", defenderTeam);

        // 保存节点布局
        if (!nodeLayouts.isEmpty()) {
            var layoutsList = new ListTag();
            for (var entry : nodeLayouts.entrySet()) {
                var layoutTag = entry.getValue().toNbt();
                layoutTag.putString("name", entry.getKey());
                layoutsList.add(layoutTag);
            }
            tag.put("nodeLayouts", layoutsList);
        }

        // 保存判断器节点数据
        if (!decisionNodes.isEmpty()) {
            var decList = new ListTag();
            for (var entry : decisionNodes.entrySet()) {
                var decTag = entry.getValue().toNbt();
                decTag.putString("name", entry.getKey());
                decList.add(decTag);
            }
            tag.put("decisionNodes", decList);
        }

        // 保存通用节点选项
        if (!nodeOptions.isEmpty()) {
            var optsList = new ListTag();
            for (var entry : nodeOptions.entrySet()) {
                var optTag = new CompoundTag();
                optTag.putString("name", entry.getKey());
                var optMap = entry.getValue();
                if (optMap != null && !optMap.isEmpty()) {
                    var valuesTag = new CompoundTag();
                    for (var optEntry : optMap.entrySet()) {
                        valuesTag.putString(optEntry.getKey(), optEntry.getValue() != null ? optEntry.getValue() : "");
                    }
                    optTag.put("values", valuesTag);
                }
                optsList.add(optTag);
            }
            tag.put("nodeOptions", optsList);
        }

        // 保存视角状态
        if (!graphWires.isEmpty()) {
            var wireList = new ListTag();
            for (var wire : graphWires) {
                wireList.add(wire.toNbt());
            }
            tag.put("graphWires", wireList);
        }

        if (viewState != null) {
            tag.put("viewState", viewState.toNbt());
        }

        return tag;
    }

    private void load(CompoundTag tag, HolderLookup.Provider registries) {
        points.clear();
        zones.clear();
        nodeLayouts.clear();
        decisionNodes.clear();
        nodeOptions.clear();
        graphWires.clear();
        viewState = null;
        defenderTeam = null;

        var pointsList = tag.getList("points", Tag.TAG_COMPOUND);
        for (int i = 0; i < pointsList.size(); i++) {
            var entry = CapturePointEntry.fromNbt(pointsList.getCompound(i));
            points.put(entry.name(), entry);
        }

        var zonesList = tag.getList("zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < zonesList.size(); i++) {
            var entry = ZoneEntry.fromNbt(zonesList.getCompound(i));
            zones.put(entry.name(), entry);
        }

        version = tag.contains(TAG_VERSION, Tag.TAG_LONG) ? tag.getLong(TAG_VERSION) : 0;
        if (tag.contains("defenderTeam")) defenderTeam = tag.getString("defenderTeam");

        // 加载节点布局
        if (tag.contains("nodeLayouts", Tag.TAG_LIST)) {
            var layoutsList = tag.getList("nodeLayouts", Tag.TAG_COMPOUND);
            for (int i = 0; i < layoutsList.size(); i++) {
                var layoutTag = layoutsList.getCompound(i);
                var name = layoutTag.getString("name");
                if (!name.isEmpty()) {
                    nodeLayouts.put(name, NodeLayout.fromNbt(layoutTag));
                }
            }
        }

        // 加载判断器节点数据
        if (tag.contains("decisionNodes", Tag.TAG_LIST)) {
            var decList = tag.getList("decisionNodes", Tag.TAG_COMPOUND);
            for (int i = 0; i < decList.size(); i++) {
                var decTag = decList.getCompound(i);
                var name = decTag.getString("name");
                if (!name.isEmpty()) {
                    decisionNodes.put(name, DecisionNodeData.fromNbt(decTag));
                }
            }
        }

        // 加载通用节点选项
        if (tag.contains("nodeOptions", Tag.TAG_LIST)) {
            var optsList = tag.getList("nodeOptions", Tag.TAG_COMPOUND);
            for (int i = 0; i < optsList.size(); i++) {
                var optTag = optsList.getCompound(i);
                var name = optTag.getString("name");
                if (!name.isEmpty() && optTag.contains("values", Tag.TAG_COMPOUND)) {
                    var valuesTag = optTag.getCompound("values");
                    var optMap = new LinkedHashMap<String, String>();
                    for (var key : valuesTag.getAllKeys()) {
                        optMap.put(key, valuesTag.getString(key));
                    }
                    nodeOptions.put(name, optMap);
                }
            }
        }

        // 加载视角状态
        if (tag.contains("graphWires", Tag.TAG_LIST)) {
            var wireList = tag.getList("graphWires", Tag.TAG_COMPOUND);
            for (int i = 0; i < wireList.size(); i++) {
                graphWires.add(GraphWireData.fromNbt(wireList.getCompound(i)));
            }
        }

        if (tag.contains("viewState", Tag.TAG_COMPOUND)) {
            viewState = ViewState.fromNbt(tag.getCompound("viewState"));
        } else {
            viewState = null;
        }

        LOGGER.info("Loaded {} points, {} zones, {} layouts, {} decisions, {} nodeOptions, {} wires (version {}), defender={}",
                points.size(), zones.size(), nodeLayouts.size(), decisionNodes.size(), nodeOptions.size(), graphWires.size(), version, defenderTeam);
    }
}
