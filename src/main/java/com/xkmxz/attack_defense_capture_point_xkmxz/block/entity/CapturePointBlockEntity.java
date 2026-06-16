package com.xkmxz.attack_defense_capture_point_xkmxz.block.entity;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.mojang.logging.LogUtils;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.CaptureManager;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.ICaptureDataAccess;
import com.xkmxz.attack_defense_capture_point_xkmxz.network.CaptureDataSyncPayload;
import com.xkmxz.attack_defense_capture_point_xkmxz.network.BlockEntityActionPayload;
import com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph.CapturePointGraphScreen;
import com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph.ToastNotification;
import com.xkmxz.attack_defense_capture_point_xkmxz.render.ClientCaptureDataCache;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.function.Consumer;

/**
 * 据点方块实体 — 绑定一个据点并展示其范围轮廓。
 * <p>
 * <b>持久化策略</b>：仅持久化 {@link #boundPointName}（方块与据点的绑定关系）。
 * radius / displayColor / showRange 不持久化，而是从 {@link ICaptureDataAccess} 实时同步，
 * 以 CaptureManager 为唯一数据源，避免数据冲突。
 * <p>
 * <b>实时同步</b>：服务端通过 {@link #syncFromManager()} 从 CaptureManager 同步最新数据，
 * 然后通过 {@link #getUpdateTag}/{@link #handleUpdateTag} 网络同步到客户端渲染层。
 * <p>
 * 写入操作：客户端 UI → {@link #sendAction} → {@link BlockEntityActionPayload} → 服务端处理 →
 * 更新 CaptureManager → 回调该 BE {@link #syncFromManager()} → 同步到客户端。
 */
public class CapturePointBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static BlockEntityType<CapturePointBlockEntity> TYPE;

    // ================================================================
    //  持久化数据 — 仅 boundPointName 是方块的"本地"数据
    // ================================================================

    /** 绑定的据点名称（空字符串 = 未绑定） */
    private String boundPointName = "";

    // ================================================================
    //  渲染缓存 — 从 CaptureManager 同步，不持久化到磁盘
    //  仅通过 getUpdateTag()/handleUpdateTag() 做网络同步
    // ================================================================

    private double radius = 5.0;
    private int displayColor = 0xFFFF4444;
    private boolean showRange = false;

    // ---- NBT 键名 ----
    private static final String TAG_BOUND_POINT = "boundPointName";
    private static final String TAG_RADIUS = "radius";
    private static final String TAG_DISPLAY_COLOR = "displayColor";
    private static final String TAG_SHOW_RANGE = "showRange";

    public CapturePointBlockEntity(BlockPos pos, BlockState blockState) {
        super(TYPE, pos, blockState);
    }

    // ================================================================
    //  NBT 持久化（磁盘 → 仅持久化绑定名）
    // ================================================================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_BOUND_POINT, boundPointName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boundPointName = tag.contains(TAG_BOUND_POINT) ? tag.getString(TAG_BOUND_POINT) : "";
    }

    // ================================================================
    //  网络同步（服务端→客户端）
    //  服务端在 getUpdateTag 中将渲染数据写入 NBT，
    //  客户端在 handleUpdateTag 中读取并以本地缓存用于渲染。
    // ================================================================

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = super.getUpdateTag(registries);
        // 写入当前渲染数据（服务端调用时已经通过 syncFromManager 从 CaptureManager 同步了最新值）
        tag.putString(TAG_BOUND_POINT, boundPointName);
        tag.putDouble(TAG_RADIUS, radius);
        tag.putInt(TAG_DISPLAY_COLOR, displayColor);
        tag.putBoolean(TAG_SHOW_RANGE, showRange);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        boundPointName = tag.contains(TAG_BOUND_POINT) ? tag.getString(TAG_BOUND_POINT) : "";
        radius = tag.contains(TAG_RADIUS) ? tag.getDouble(TAG_RADIUS) : 5.0;
        displayColor = tag.contains(TAG_DISPLAY_COLOR) ? tag.getInt(TAG_DISPLAY_COLOR) : 0xFFFF4444;
        showRange = tag.contains(TAG_SHOW_RANGE) && tag.getBoolean(TAG_SHOW_RANGE);
    }

    // ================================================================
    //  生命週期回调
    // ================================================================

    @Override
    public void onLoad() {
        super.onLoad();
        // 服务端：从 CaptureManager 同步最新数据到本地缓存
        if (level != null && !level.isClientSide && !boundPointName.isEmpty()) {
            syncFromManager();
        }
    }

    // ================================================================
    //  Getter（仅供渲染器使用）
    // ================================================================

    public String getBoundPointName() { return boundPointName; }
    public double getRadius() { return radius; }
    public int getDisplayColor() { return displayColor; }
    public boolean isShowRange() { return showRange; }

    /**
     * 从客户端缓存读取据点的渲染数据（CaptureManager 的同步副本）。
     * 优先于本地缓存字段，确保方块菜单显示的数据与 CaptureManager 一致。
     */
    @org.jetbrains.annotations.Nullable
    private CaptureDataSyncPayload.PointRenderData getPointDataFromCache() {
        if (boundPointName.isEmpty()) return null;
        return ClientCaptureDataCache.INSTANCE.getPoints().get(boundPointName);
    }

    // ================================================================
    //  服务端同步引擎
    // ================================================================

    /**
     * 【服务端调用】从 CaptureManager 同步绑定据点的最新数据到本地缓存，
     * 如果有变化则同步到客户端。
     * <p>
     * 当 CaptureManager 中的数据被命令/GUI/其他方块修改后，
     * 应调用此方法使该方块实体反映最新数据。
     */
    public void syncFromManager() {
        if (level == null || level.isClientSide) return;
        if (boundPointName.isEmpty()) return;

        var access = getServerDataAccess();
        if (access == null) return;

        var entry = access.getPoint(boundPointName);
        if (entry == null) {
            // 绑定的据点已被删除
            LOGGER.warn("Bound point '{}' no longer exists, unbinding BE at {}", boundPointName, worldPosition);
            boundPointName = "";
            setChanged();
            syncToClient();
            return;
        }

        boolean changed = false;
        if (radius != entry.radius()) { radius = entry.radius(); changed = true; }
        if (displayColor != entry.displayColor()) { displayColor = entry.displayColor(); changed = true; }
        if (showRange != entry.showRange()) { showRange = entry.showRange(); changed = true; }

        if (changed) {
            setChanged();
            syncToClient();
        }
    }

    /**
     * 【服务端调用】设置绑定名并同步到 CaptureManager 和客户端。
     */
    public void setBoundPointNameFromServer(String name) {
        this.boundPointName = name != null ? name : "";
        syncFromManager();
        setChanged();
        syncToClient();
    }

    // ================================================================
    //  全局同步 — 命令 / GUI / 方块三者数据一致性
    // ================================================================

    /**
     * 【服务端调用】同步当前世界中所有已加载的 CapturePointBlockEntity，
     * 让它们从 CaptureManager 获取最新数据。
     * <p>
     * 在以下场景调用以确保数据一致性：
     * <ul>
     *   <li>GUI 节点图编辑器保存后（{@code applyGraphSnapshot}）</li>
     *   <li>命令修改据点/区域数据后</li>
     *   <li>服务器周期性 tick（安全网）</li>
     * </ul>
     */
    public static void syncAllBoundBlocks(ServerLevel level) {
        int viewDistance = level.getServer().getPlayerList().getViewDistance();
        // 遍历所有在线玩家，在每个玩家周围的视距范围内查找方块实体
        for (var player : level.players()) {
            var playerChunk = player.chunkPosition();
            for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                    int cx = playerChunk.x + dx;
                    int cz = playerChunk.z + dz;
                    if (!level.hasChunk(cx, cz)) continue;
                    var chunk = level.getChunk(cx, cz);
                    if (chunk instanceof net.minecraft.world.level.chunk.LevelChunk lc) {
                        for (var be : lc.getBlockEntities().values()) {
                            if (be instanceof CapturePointBlockEntity cbe) {
                                cbe.syncFromManager();
                            }
                        }
                    }
                }
            }
        }
        // 同步后广播据点数据到所有客户端（确保世界渲染器显示最新数据）
        CaptureDataSyncPayload.broadcastToAll(level);
    }

    // ================================================================
    //  同步辅助
    // ================================================================

    /** 发送方块更新包到客户端（仅服务端调用） */
    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    /** 获取服务端统一数据访问接口 */
    private ICaptureDataAccess getServerDataAccess() {
        if (level instanceof ServerLevel sl) {
            return ICaptureDataAccess.server(sl);
        }
        return null;
    }

    // ================================================================
    //  网络包通信
    // ================================================================

    /**
     * 通过网络包发送方块操作到服务端。
     * 客户端→服务端严格分离，不直接访问 CaptureManager。
     */
    private void sendAction(String action, String data) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new BlockEntityActionPayload(worldPosition, action, data));
    }

    // ================================================================
    //  打开方块控制菜单（客户端调用）
    // ================================================================

    public void openUI(Player player) {
        if (level != null && level.isClientSide) {
            openMenuScreen();
        }
    }

    /** 创建并显示方块功能菜单屏幕。自适应宽度：屏幕 40%，最大 260px。 */
    private void openMenuScreen() {
        var mc = Minecraft.getInstance();
        var win = mc.getWindow();
        int scw = win.getGuiScaledWidth();
        int sch = win.getGuiScaledHeight();

        int panelW = Math.min(scw * 40 / 100, 260);
        int btnH = 22;
        int gap = 3;
        int titleH = 14;
        int statusH = 12;
        int bottomH = 20;
        int pad = 6;
        int panelH = Math.min(pad + titleH + statusH + gap + 6 * (btnH + gap) + bottomH + pad, (int)(sch * 0.8));

        int bg = 0xFF1A1A2E;
        int btnBg = 0xFF16213E;

        var root = new UIElement()
                .layout(l -> l.width(panelW).height(panelH).paddingAll(pad).gapAll(gap)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(bg)));

        var titleLabel = new Label().setText(Component.translatable("gui.capture_point_block.menu.title"));
        titleLabel.layout(l -> l.widthPercent(100).height(titleH));
        titleLabel.textStyle(s -> s.fontSize(10.0f).textColor(0xFFAAAAAA));
        root.addChildren(titleLabel);

        var boundLabel = new Label();
        updateBoundLabel(boundLabel);
        boundLabel.layout(l -> l.widthPercent(100).height(statusH));
        boundLabel.textStyle(s -> s.fontSize(9.0f).textColor(0xFF888888));
        root.addChildren(boundLabel);

        root.addChildren(createFuncButton(btnBg, btnH,
                "gui.capture_point_block.func1", this::funcCreatePoint));
        root.addChildren(createFuncButton(btnBg, btnH,
                "gui.capture_point_block.func2", this::funcBindZone));
        root.addChildren(createFuncButton(btnBg, btnH,
                "gui.capture_point_block.func3", this::funcViewStatus));
        root.addChildren(createFuncButton(btnBg, btnH,
                "gui.capture_point_block.func4", () -> funcSetRadius(mc)));
        root.addChildren(createFuncButton(btnBg, btnH,
                "gui.capture_point_block.func5", () -> funcToggleShowRange(mc)));
        root.addChildren(createFuncButton(btnBg, btnH,
                "gui.capture_point_block.func6", () -> funcRemoveBinding(mc)));

        // 底部按钮行
        var bottomRow = new UIElement()
                .layout(l -> l.widthPercent(100).height(bottomH)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(3));
        var openGraphBtn = new Button().setText(Component.translatable("gui.capture_point_block.open_graph"));
        openGraphBtn.layout(l -> l.flex(1).heightPercent(100));
        openGraphBtn.setOnClick(e -> {
            mc.setScreen(null);
            new CapturePointGraphScreen(level).open();
        });
        var closeBtn = new Button().setText(Component.translatable("gui.capture_point_block.close"));
        closeBtn.layout(l -> l.width(44).heightPercent(100));
        closeBtn.setOnClick(e -> mc.setScreen(null));
        bottomRow.addChildren(openGraphBtn, closeBtn);
        root.addChildren(bottomRow);

        var wrap = new UIElement()
                .layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(0).gapAll(0)
                        .justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER)
                        .alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER));
        wrap.addChildren(root);

        var ui = ModularUI.of(UI.of(wrap));
        mc.setScreen(new ModularUIScreen(ui,
                Component.translatable("gui.capture_point_block.menu.title")));
    }

    private UIElement createFuncButton(int bg, int h, String langKey, Runnable onClick) {
        var btn = new Button()
                .setText(Component.translatable(langKey));
        btn.layout(l -> l.widthPercent(100).height(h));
        btn.style(s -> s.background(Sprites.BORDER)
                .backgroundTexture(new ColorRectTexture(bg)));
        btn.setOnClick(e -> onClick.run());
        return btn;
    }

    private void updateBoundLabel(Label label) {
        if (boundPointName.isEmpty()) {
            label.setText(Component.translatable("gui.capture_point_block.unbound"));
        } else {
            label.setText(Component.translatable("gui.capture_point_block.bound", boundPointName));
        }
    }

    // ================================================================
    //  6 个功能实现 — 全部通过网络包通信
    // ================================================================

    /**
     * 功能1: 以方块坐标创建据点并绑定。
     * 生成唯一名称 → 乐观更新本地绑定名 → 发送网络包到服务端。
     */
    private void funcCreatePoint() {
        if (!boundPointName.isEmpty()) {
            ToastNotification.push(ToastNotification.Type.INFO,
                    Component.translatable("toast.capture_point_block.already_bound", boundPointName));
            reopenMenu();
            return;
        }

        var pos = worldPosition;
        String name = "capture_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();

        // 乐观更新本地绑定名
        boundPointName = name;
        setChanged();

        // 发送网络包到服务端创建据点（服务端处理后会同步数据回来）
        sendAction("create_point_at", name + "," + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "," + CaptureManager.CapturePointEntry.DEFAULT_RADIUS);

        ToastNotification.push(ToastNotification.Type.SUCCESS,
                Component.translatable("toast.capture_point_block.created", name));
        reopenMenu();
    }

    /**
     * 功能2: 将绑定的据点绑定到区域。
     * 弹出区域选择对话框。
     */
    private void funcBindZone() {
        if (boundPointName.isEmpty()) {
            ToastNotification.push(ToastNotification.Type.ERROR,
                    Component.translatable("toast.capture_point_block.not_bound"));
            reopenMenu();
            return;
        }
        openZoneSelectDialog();
    }

    /**
     * 功能3: 查看据点状态。
     * 使用本地缓存数据展示。
     */
    private void funcViewStatus() {
        if (boundPointName.isEmpty()) {
            ToastNotification.push(ToastNotification.Type.INFO,
                    Component.translatable("toast.capture_point_block.not_bound"));
            reopenMenu();
            return;
        }

        // 从客户端缓存读取据点数据（来自 CaptureManager）
        var cached = getPointDataFromCache();
        double displayRadius = cached != null ? cached.radius() : radius;
        boolean displayShowRange = cached != null ? cached.showRange() : showRange;
        boolean displayCaptured = cached != null ? cached.captured() : false;

        ToastNotification.push(ToastNotification.Type.INFO,
                Component.translatable("toast.capture_point_block.status_name", boundPointName));
        ToastNotification.push(ToastNotification.Type.INFO,
                Component.translatable("toast.capture_point_block.status_pos",
                        worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()));
        ToastNotification.push(ToastNotification.Type.INFO,
                Component.translatable("toast.capture_point_block.status_radius", (int) displayRadius));
        ToastNotification.push(ToastNotification.Type.INFO,
                Component.translatable("toast.capture_point_block.status_captured",
                        displayCaptured
                                ? Component.translatable("gui.capture_point_graph.dialog.advanced_config.toggle.on").getString()
                                : Component.translatable("gui.capture_point_graph.dialog.advanced_config.toggle.off").getString()));
        ToastNotification.push(ToastNotification.Type.INFO,
                Component.translatable("toast.capture_point_block.status_range",
                        displayShowRange
                                ? Component.translatable("gui.capture_point_graph.dialog.advanced_config.toggle.on").getString()
                                : Component.translatable("gui.capture_point_graph.dialog.advanced_config.toggle.off").getString()));

        reopenMenu();
    }

    /**
     * 功能4: 设定据点大小（半径）。
     * 通过网络包发送到服务端处理。
     */
    private void funcSetRadius(Minecraft mc) {
        var cached = getPointDataFromCache();
        double currentRadius = cached != null ? cached.radius() : radius;
        openInputDialog(
                Component.translatable("gui.capture_point_block.dialog.radius.title"),
                Component.translatable("gui.capture_point_block.dialog.radius.label"),
                String.valueOf((int) currentRadius),
                (input) -> {
                    try {
                        double newRadius = Double.parseDouble(input);
                        if (newRadius < 1 || newRadius > 100) {
                            ToastNotification.push(ToastNotification.Type.ERROR,
                                    Component.translatable("toast.capture_point_block.radius_invalid"));
                            reopenMenu();
                            return;
                        }

                        // 发送网络包到服务端修改据点半径（服务端处理后会经过 syncFromManager 同步回来）
                        if (!boundPointName.isEmpty()) {
                            sendAction("set_radius", boundPointName + "," + newRadius);
                        }

                        ToastNotification.push(ToastNotification.Type.SUCCESS,
                                Component.translatable("toast.capture_point_block.radius_set", (int) newRadius));
                    } catch (NumberFormatException e) {
                        ToastNotification.push(ToastNotification.Type.ERROR,
                                Component.translatable("toast.capture_point_block.radius_invalid"));
                    }
                    reopenMenu();
                }
        );
    }

    /**
     * 功能5: 切换显示据点范围并允许自定义颜色。
     * 通过网络包发送到服务端处理。
     */
    private void funcToggleShowRange(Minecraft mc) {
        var cached = getPointDataFromCache();
        boolean currentShowRange = cached != null ? cached.showRange() : showRange;
        boolean newShow = !currentShowRange; // 计算期望的新状态，不下写本地缓存，由服务端同步回来

        if (!boundPointName.isEmpty()) {
            sendAction("toggle_range", boundPointName + "," + newShow);
        }

        if (newShow) {
            openColorPickerDialog();
        } else {
            ToastNotification.push(ToastNotification.Type.SUCCESS,
                    Component.translatable("toast.capture_point_block.range_off"));
            reopenMenu();
        }
    }

    /**
     * 功能6: 移除绑定 — 打开子菜单选择操作。
     */
    private void funcRemoveBinding(Minecraft mc) {
        if (boundPointName.isEmpty()) {
            ToastNotification.push(ToastNotification.Type.ERROR,
                    Component.translatable("toast.capture_point_block.not_bound"));
            reopenMenu();
            return;
        }

        int scw = mc.getWindow().getGuiScaledWidth();
        int sch = mc.getWindow().getGuiScaledHeight();
        int dw = Math.min(scw * 50 / 100, 260);
        int dh = Math.min(130, (int)(sch * 0.8));
        var root = new UIElement()
                .layout(l -> l.width(dw).height(dh).paddingAll(10).gapAll(6)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(0xFF1A1A2E)));

        var title = new Label().setText(Component.translatable("gui.capture_point_block.dialog.unbind.title"));
        title.layout(l -> l.widthPercent(100).heightAuto());
        root.addChildren(title);

        // 选项1: 仅清除方块绑定（不删除服务端据点）
        var unbindBlockBtn = new Button().setText(Component.translatable("gui.capture_point_block.dialog.unbind.block"));
        unbindBlockBtn.layout(l -> l.widthPercent(100).height(24));
        unbindBlockBtn.setOnClick(e -> {
            // 发送网络包清除服务端的绑定状态
            String currentName = boundPointName;
            sendAction("block_unbind", currentName);
            // 乐观更新本地状态
            boundPointName = "";
            setChanged();
            ToastNotification.push(ToastNotification.Type.SUCCESS,
                    Component.translatable("toast.capture_point_block.unbind_block"));
            mc.setScreen(null);
            reopenMenu();
        });
        root.addChildren(unbindBlockBtn);

        // 选项2: 从区域中移除据点
        var unbindZoneBtn = new Button().setText(Component.translatable("gui.capture_point_block.dialog.unbind.zone"));
        unbindZoneBtn.layout(l -> l.widthPercent(100).height(24));
        unbindZoneBtn.setOnClick(e -> {
            String currentName = boundPointName;
            sendAction("remove_from_zone", currentName);
            ToastNotification.push(ToastNotification.Type.SUCCESS,
                    Component.translatable("toast.capture_point_block.unbind_zone", currentName));
            mc.setScreen(null);
            reopenMenu();
        });
        root.addChildren(unbindZoneBtn);

        // 选项3: 取消
        var cancelBtn = new Button().setText(Component.translatable("gui.capture_point_graph.dialog.cancel"));
        cancelBtn.layout(l -> l.widthPercent(100).height(20));
        cancelBtn.setOnClick(e -> {
            mc.setScreen(null);
            reopenMenu();
        });
        root.addChildren(cancelBtn);

        var ui = ModularUI.of(UI.of(root));
        mc.setScreen(new ModularUIScreen(ui,
                Component.translatable("gui.capture_point_block.dialog.unbind.title")));
    }

    // ================================================================
    //  区域选择对话框
    // ================================================================

    private void openZoneSelectDialog() {
        var mc = Minecraft.getInstance();
        var cache = ClientCaptureDataCache.INSTANCE;
        var zoneNames = cache.getZoneNames();

        if (zoneNames.isEmpty()) {
            ToastNotification.push(ToastNotification.Type.ERROR,
                    Component.translatable("toast.capture_point_block.no_zones"));
            reopenMenu();
            return;
        }

        int scw = mc.getWindow().getGuiScaledWidth();
        int sch = mc.getWindow().getGuiScaledHeight();
        int panelW = Math.min(scw * 50 / 100, 320);
        int btnH = 28;
        int rawDh = 40 + zoneNames.size() * (btnH + 4) + 44;
        int dh = Math.min(rawDh, (int)(sch * 0.8));

        var root = new UIElement()
                .layout(l -> l.width(panelW).height(dh).paddingAll(12).gapAll(6)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(0xFF1A1A2E)));

        var title = new Label().setText(Component.translatable("gui.capture_point_block.dialog.select_zone"));
        title.layout(l -> l.widthPercent(100).heightAuto());
        root.addChildren(title);

        for (var zoneName : zoneNames) {
            var btn = new Button().setText(Component.literal(zoneName));
            btn.layout(l -> l.widthPercent(100).height(btnH));
            btn.setOnClick(e -> {
                sendAction("add_to_zone", zoneName + "," + boundPointName);
                ToastNotification.push(ToastNotification.Type.SUCCESS,
                        Component.translatable("toast.capture_point_block.bound_to_zone", boundPointName, zoneName));
                mc.setScreen(null);
                reopenMenu();
            });
            root.addChildren(btn);
        }

        var cancelBtn = new Button().setText(Component.translatable("gui.capture_point_graph.dialog.cancel"));
        cancelBtn.layout(l -> l.widthPercent(100).height(btnH));
        cancelBtn.setOnClick(e -> {
            mc.setScreen(null);
            reopenMenu();
        });
        root.addChildren(cancelBtn);

        var wrap = new UIElement()
                .layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(0).gapAll(0)
                        .justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER)
                        .alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER));
        wrap.addChildren(root);

        var ui = ModularUI.of(UI.of(wrap));
        mc.setScreen(new ModularUIScreen(ui,
                Component.translatable("gui.capture_point_block.dialog.select_zone")));
    }

    // ================================================================
    //  颜色选择对话框
    // ================================================================

    private static final int[] PRESET_COLORS = {
            0xFFFF4444, // 红
            0xFFFF9800, // 橙
            0xFFFFEB3B, // 黄
            0xFF4CAF50, // 绿
            0xFF2196F3, // 蓝
            0xFF9C27B0, // 紫
            0xFFFFFFFF, // 白
            0xFF000000  // 黑
    };

    private void openColorPickerDialog() {
        var mc = Minecraft.getInstance();
        int scw = mc.getWindow().getGuiScaledWidth();
        int sch = mc.getWindow().getGuiScaledHeight();
        int cols = 4;
        int rows = (int) Math.ceil((double) PRESET_COLORS.length / cols);
        int cw = 50, ch = 36, cgap = 6;
        int dw = Math.min(cols * (cw + cgap) + 24 + cgap, (int)(scw * 0.8));
        int dh = Math.min(rows * (ch + cgap) + 80, (int)(sch * 0.8));

        var root = new UIElement()
                .layout(l -> l.width(dw).height(dh).paddingAll(12).gapAll(8)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(0xFF1A1A2E)));

        var title = new Label().setText(Component.translatable("gui.capture_point_block.dialog.color_picker"));
        title.layout(l -> l.widthPercent(100).heightAuto());
        root.addChildren(title);

        var grid = new UIElement()
                .layout(l -> l.widthPercent(100).heightAuto()
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW)
                        .flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(cgap));
        grid.style(s -> s.backgroundTexture(new ColorRectTexture(0x00000000)));

        for (int color : PRESET_COLORS) {
            var colorBtn = new UIElement()
                    .layout(l -> l.width(cw).height(ch))
                    .style(s -> s.background(Sprites.BORDER)
                            .backgroundTexture(new ColorRectTexture(color)));
            int selectedColor = color;
            colorBtn.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.MOUSE_DOWN, ev -> {
                // 通过网络包同步到服务端（不直接修改本地缓存，由 syncFromManager 同步回来）
                if (!boundPointName.isEmpty()) {
                    sendAction("set_color", boundPointName + "," + selectedColor);
                }

                ToastNotification.push(ToastNotification.Type.SUCCESS,
                        Component.translatable("toast.capture_point_block.color_set"));
                mc.setScreen(null);
                reopenMenu();
            });
            grid.addChildren(colorBtn);
        }
        root.addChildren(grid);

        var hint = new Label().setText(Component.translatable("gui.capture_point_block.dialog.color_picker.hint"));
        hint.layout(l -> l.widthPercent(100).heightAuto());
        root.addChildren(hint);

        var btnRow = new UIElement()
                .layout(l -> l.widthPercent(100).height(28)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(6));
        var cancelBtn = new Button().setText(Component.translatable("gui.capture_point_graph.dialog.cancel"));
        cancelBtn.layout(l -> l.width(80).heightPercent(100));
        cancelBtn.setOnClick(e -> {
            mc.setScreen(null);
            reopenMenu();
        });
        var disableBtn = new Button().setText(Component.translatable("gui.capture_point_block.dialog.disable_range"));
        disableBtn.layout(l -> l.flex(1).heightPercent(100));
        disableBtn.setOnClick(e -> {
            if (!boundPointName.isEmpty()) {
                sendAction("toggle_range", boundPointName + ",false");
            }
            ToastNotification.push(ToastNotification.Type.INFO,
                    Component.translatable("toast.capture_point_block.range_off"));
            mc.setScreen(null);
            reopenMenu();
        });
        btnRow.addChildren(disableBtn, cancelBtn);
        root.addChildren(btnRow);

        var ui = ModularUI.of(UI.of(root));
        mc.setScreen(new ModularUIScreen(ui,
                Component.translatable("gui.capture_point_block.dialog.color_picker")));
    }

    // ================================================================
    //  通用输入对话框
    // ================================================================

    private void openInputDialog(Component titleText, Component labelText,
                                  String defaultValue, Consumer<String> onConfirm) {
        var mc = Minecraft.getInstance();
        int scw = mc.getWindow().getGuiScaledWidth();
        int sch = mc.getWindow().getGuiScaledHeight();
        int dw = Math.min(scw * 50 / 100, 320);
        int dh = Math.min(160, (int)(sch * 0.8));

        var root = new UIElement()
                .layout(l -> l.width(dw).height(dh).paddingAll(12).gapAll(8)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(0xFF1A1A2E)));

        var title = new Label().setText(titleText);
        title.layout(l -> l.widthPercent(100).heightAuto());
        root.addChildren(title);

        var label = new Label().setText(labelText);
        label.layout(l -> l.widthPercent(100).heightAuto());
        root.addChildren(label);

        var textField = new TextField();
        textField.layout(l -> l.widthPercent(100).height(28));
        textField.textFieldStyle(s -> {
            s.textColor(0xFFFFFFFF);
            s.fontSize(14.0f);
        });
        textField.setValue(defaultValue, false);
        root.addChildren(textField);

        root.addChildren(new UIElement().layout(l -> l.flex(1)));

        var btnRow = new UIElement()
                .layout(l -> l.widthPercent(100).height(30)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(8));
        var confirmBtn = new Button().setText(Component.translatable("gui.capture_point_graph.dialog.confirm"));
        confirmBtn.layout(l -> l.flex(1).heightPercent(100));
        confirmBtn.setOnClick(e -> {
            var input = textField.getText().trim();
            onConfirm.accept(input);
        });
        var cancelBtn = new Button().setText(Component.translatable("gui.capture_point_graph.dialog.cancel"));
        cancelBtn.layout(l -> l.flex(1).heightPercent(100));
        cancelBtn.setOnClick(e -> {
            mc.setScreen(null);
            reopenMenu();
        });
        btnRow.addChildren(confirmBtn, cancelBtn);
        root.addChildren(btnRow);

        var ui = ModularUI.of(UI.of(root));
        mc.setScreen(new ModularUIScreen(ui, titleText));
    }

    // ================================================================
    //  工具方法
    // ================================================================

    /** 重新打开菜单 */
    private void reopenMenu() {
        Minecraft.getInstance().setScreen(null);
        openMenuScreen();
    }
}
