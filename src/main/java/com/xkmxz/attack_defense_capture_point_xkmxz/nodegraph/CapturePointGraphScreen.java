package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.GraphElementModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.INodeWithOptions;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.wire.WireModel;
import com.xkmxz.attack_defense_capture_point_xkmxz.block.entity.CapturePointBlockEntity;
import com.xkmxz.attack_defense_capture_point_xkmxz.gui.CapturePointTheme;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.CaptureManager;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.ICaptureDataAccess;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

/**
 * 据点管理节点图屏幕 - 使用 LDLib2 nodegraphtookit 框架提供完整的节点图编辑体验。
 * 支持保存节点关系到 CaptureManager。
 */
public class CapturePointGraphScreen {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Level level;
    private final CapturePointGraphView graphView;
    private final CapturePointGraph graph;
    private boolean editMode = false;
    private long snapshotVersion = -1; // 进入编辑模式时捕获的版本号，用于冲突检测

    public CapturePointGraphScreen(Level level) {
        this.level = level;
        this.graph = new CapturePointGraph();
        this.graphView = new CapturePointGraphView();
        this.graphView.setLevel(level);
        this.graphView.setScreen(this);
        this.graphView.loadGraph(graph);
        // 注册实时数据刷新回调（每 ~15 tick 由 graphView.screenTick() 驱动）
        this.graphView.setRefreshCallback(this::refreshNodeTitles);
    }

    /** 返回当前是否处于编辑模式，GraphView 可用此判断菜单行为 */
    public boolean isEditMode() {
        return editMode;
    }

    /** 获取图模型实例，供 GraphView 创建节点使用 */
    public CapturePointGraph getGraph() {
        return graph;
    }

    /** 进入编辑模式时快照当前版本号 */
    private void captureSnapshotVersion() {
        var mgr = getServerCaptureManager();
        snapshotVersion = mgr != null ? mgr.getVersion() : -1;
    }

    public void open() {
        var mc = Minecraft.getInstance();
        var win = mc.getWindow();
        int scw = win.getGuiScaledWidth();
        int sch = win.getGuiScaledHeight();

        // 根容器 - 全屏，使用 SDF 圆角背景
        var root = CapturePointTheme.panel(CapturePointTheme.ROOT_COLOR)
                .layout(l -> l.width(scw).height(sch));

        // 创建通知层（偏右上角，在顶栏下方，避免被遮挡）
        var toastContainer = new UIElement()
                .layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                        .right(8).top(36).width(280).heightPercent(80));
        root.addChildren(toastContainer);

        var toastLayer = new ToastLayer(toastContainer);
        graphView.setToastLayer(toastLayer);

        // 顶栏
        var topBar = createTopBar();
        root.addChildren(topBar);

        // 图视图 - 填充剩余空间
        graphView.layout(l -> l.widthPercent(100).flex(1));
        root.addChildren(graphView);

        // 根据 CaptureManager 加载数据到图
        loadDataToGraph();
        // 初始立即同步节点选项数据
        refreshNodeTitles();
        // 注册选项值变更监听器，实现 GUI→Game 实时回写
        registerOptionChangeListeners();

        var ui = ModularUI.of(UI.of(root));
        mc.setScreen(new ModularUIScreen(ui,
                Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.title")));
    }

    private UIElement createTopBar() {
        var tb = CapturePointTheme.panel(CapturePointTheme.FIELD_COLOR)
                .layout(l -> l.widthPercent(100).height(32)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW)
                        .gapAll(6).paddingAll(4));

        // 标题
        var title = CapturePointTheme.titleLabel(
                Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.title"));
        title.layout(l -> l.widthAuto().heightPercent(100));

        // 编辑模式切换按钮
        var editToggleBtn = CapturePointTheme.styledButton(
                Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.edit_mode.off"));
        editToggleBtn.layout(l -> l.width(60).heightPercent(100));
        editToggleBtn.setOnClick(e -> {
            editMode = !editMode;
            updateEditToggleButton(editToggleBtn);
            graphView.showPanelsForEditMode(editMode);
            tb.style(s -> s.backgroundTexture(CapturePointTheme.panelBg(editMode ? 0xFF2E1A1A : CapturePointTheme.FIELD_COLOR)));
            if (editMode) {
                captureSnapshotVersion();
            }
            ToastNotification.push(editMode ? ToastNotification.Type.INFO : ToastNotification.Type.SUCCESS,
                    Component.translatable(editMode ? "toast.capture_point_graph.edit_mode.on" : "toast.capture_point_graph.edit_mode.off"));
        });

        // 保存按钮
        var saveBtn = CapturePointTheme.styledButton(
                Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.btn_save"));
        saveBtn.layout(l -> l.width(50).heightPercent(100));
        saveBtn.setOnClick(e -> saveGraph());

        // 刷新按钮
        var refreshBtn = CapturePointTheme.styledButton(
                Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.btn_refresh"));
        refreshBtn.layout(l -> l.width(50).heightPercent(100));
        refreshBtn.setOnClick(e -> {
            mc().setScreen(null);
            new CapturePointGraphScreen(level).open();
        });

        // 关闭按钮
        var closeBtn = CapturePointTheme.styledButton(
                Component.translatable("gui.attack_defense_capture_point_xkmxz.block.close"));
        closeBtn.layout(l -> l.width(50).heightPercent(100));
        closeBtn.setOnClick(e -> mc().setScreen(null));

        // 占位弹性空间
        var spacer = new UIElement().layout(l -> l.flex(1));

        tb.addChildren(title, spacer, editToggleBtn, saveBtn, refreshBtn, closeBtn);
        return tb;
    }

    /** 更新编辑模式切换按钮的文本和颜色 */
    private void updateEditToggleButton(Button btn) {
        if (editMode) {
            btn.setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.edit_mode.on"));
            btn.style(s -> s.backgroundTexture(CapturePointTheme.buttonBg(0xFFAA3333)));
        } else {
            btn.setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.edit_mode.off"));
            btn.style(s -> s.backgroundTexture(CapturePointTheme.buttonBg(CapturePointTheme.BUTTON_COLOR)));
        }
    }

    // ================================================================
    //  保存节点图到 CaptureManager (快照模式 + 版本检测)
    // ================================================================

    /** 从当前节点模型中读取选项值 */
    private static String getOptionString(NodeModel nm, String id) {
        if (nm instanceof INodeWithOptions opts) {
            var opt = opts.getNodeOptionById(id);
            if (opt instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption nodeOpt) {
                var port = nodeOpt.getPortModel();
                if (port instanceof IFieldValueConfigurable configurable) {
                    var v = configurable.getValue();
                    return v != null ? v.toString() : "";
                }
            }
        }
        return "";
    }

    private static double getOptionDouble(NodeModel nm, String id) {
        try {
            return Double.parseDouble(getOptionString(nm, id));
        } catch (Exception e) { return 5.0; }
    }

    private static int getOptionInt(NodeModel nm, String id) {
        try {
            return Integer.parseInt(getOptionString(nm, id));
        } catch (Exception e) { return 0xFFFF4444; }
    }

    private static boolean getOptionBool(NodeModel nm, String id) {
        String val = getOptionString(nm, id);
        if (val == null || val.isEmpty()) return false;
        return "true".equalsIgnoreCase(val) || "1".equals(val) || "yes".equalsIgnoreCase(val);
    }

    /**
     * 从节点图中构建完整数据快照。<br>
     * <br>
     * <b>处理流程：</b><br>
     * 1. 收集所有节点（据点/区域/判断器）<br>
     * 2. 解析连线：直连（point→zone）、判断器输入（point→decision）、<br>
     *    判断器输出（decision→zone）、区域依赖（zone→zone）、<br>
     *    区域→判断器（zone_out→zone_target）、判断器→区域依赖（zone_true_out/false_out→required_zone）<br>
     * 3. 构建据点数据<br>
     * 4. 据点判断器条件路由：根据条件将据点分配到 true_out 或 false_out<br>
     * 5. 构建区域数据（直连据点 + 判断器路由据点）<br>
     * 6. 区域判断器条件路由：根据条件将区域依赖（zone_out）分配到 zone_true_out 或 zone_false_out<br>
     * 7. 双向同步规则
     */
    private Map.Entry<Map<String, CaptureManager.CapturePointEntry>, Map<String, CaptureManager.ZoneEntry>> buildSnapshotFromGraph() {
        var newPoints = new LinkedHashMap<String, CaptureManager.CapturePointEntry>();
        var newZones = new LinkedHashMap<String, CaptureManager.ZoneEntry>();

        // ---- Phase 1: 收集所有节点 ----
        var pointModels = new HashMap<String, NodeModel>();
        var zoneModels = new HashMap<String, NodeModel>();
        var decisionModels = new HashMap<String, NodeModel>();

        for (var element : graph.graphModel.getGraphElementModels()) {
            if (element instanceof NodeModel nm) {
                String name = nm.getName();
                if (name == null || name.isEmpty()) continue;
                if (hasOutputPort(nm, "point_signal")) {
                    pointModels.put(name, nm);
                } else if (hasInputPort(nm, "target")) {
                    // 判断器节点：具有 target 输入端口
                    decisionModels.put(name, nm);
                } else if (hasOutputPort(nm, "zone_out") || hasInputPort(nm, "point_in")) {
                    zoneModels.put(name, nm);
                }
            }
        }

        // ---- Phase 2: 解析连线 ----
        // wireBasedZonePoints: zoneName → [pointName, ...] 直连（不经过判断器）
        var wireBasedZonePoints = new LinkedHashMap<String, List<String>>();
        // wireBasedRequiredZone: zoneName → requiredZoneName 区域依赖
        var wireBasedRequiredZone = new LinkedHashMap<String, String>();
        // wireBasedUnlockDeps: zoneName → [depZoneName, ...] 🔓 解锁依赖（unlock_out → unlock_in），独立于区域信号
        var wireBasedUnlockDeps = new LinkedHashMap<String, List<String>>();
        // decisionInputs: decisionName → [pointName, ...] 判断器输入（连接到target端口的据点）
        var decisionInputs = new LinkedHashMap<String, List<String>>();
        // decisionOutputs: decisionName → (portName → [zoneName, ...]) 判断器各输出端口连接的区域
        var decisionOutputs = new LinkedHashMap<String, Map<String, List<String>>>();
        // zoneDecisionInputs: decisionName → [zoneName, ...] 判断器区域输入（连接到zone_target端口的区域）
        var zoneDecisionInputs = new LinkedHashMap<String, List<String>>();
        // zoneDecisionOutputs: decisionName → (portName → [zoneName, ...]) 判断器区域输出端口连接的区域
        var zoneDecisionOutputs = new LinkedHashMap<String, Map<String, List<String>>>();

        for (var element : graph.graphModel.getGraphElementModels()) {
            if (element instanceof WireModel wire) {
                var fromPort = wire.getFromPort();
                var toPort = wire.getToPort();
                if (fromPort == null || toPort == null) continue;

                var fromNode = fromPort.getNodeModel();
                var toNode = toPort.getNodeModel();
                if (!(fromNode instanceof NodeModel fromNm) || !(toNode instanceof NodeModel toNm)) continue;

                String fromName = fromNm.getName();
                String toName = toNm.getName();
                if (fromName == null || toName == null) continue;

                // 1. 据点→区域直连: from=point_signal(O)  to=point_in(I)
                if (hasOutputPort(fromNm, "point_signal") && hasInputPort(toNm, "point_in")) {
                    wireBasedZonePoints.computeIfAbsent(toName, k -> new ArrayList<>()).add(fromName);
                }
                // 2. 据点→判断器: from=point_signal(O)  to=target(I)
                else if (hasOutputPort(fromNm, "point_signal") && hasInputPort(toNm, "target")) {
                    decisionInputs.computeIfAbsent(toName, k -> new ArrayList<>()).add(fromName);
                }
                // 3. 判断器→区域: from=true_out/false_out(O)  to=point_in(I)
                else if (hasInputPort(toNm, "point_in") && hasOutputPort(fromNm, "true_out")) {
                    decisionOutputs.computeIfAbsent(fromName, k -> new LinkedHashMap<>())
                            .computeIfAbsent("true_out", k -> new ArrayList<>()).add(toName);
                }
                else if (hasInputPort(toNm, "point_in") && hasOutputPort(fromNm, "false_out")) {
                    decisionOutputs.computeIfAbsent(fromName, k -> new LinkedHashMap<>())
                            .computeIfAbsent("false_out", k -> new ArrayList<>()).add(toName);
                }
                // 4. 区域→区域依赖: from=zone_out(O)  to=required_zone(I)
                else if (hasOutputPort(fromNm, "zone_out") && hasInputPort(toNm, "required_zone")) {
                    wireBasedRequiredZone.put(toName, fromName);
                }
                // 5. 区域→判断器(区域信号): from=zone_out(O)  to=zone_target(I)
                else if (hasOutputPort(fromNm, "zone_out") && hasInputPort(toNm, "zone_target")) {
                    zoneDecisionInputs.computeIfAbsent(toName, k -> new ArrayList<>()).add(fromName);
                }
                // 6. 判断器(区域信号)→区域依赖: from=zone_true_out/zone_false_out(O)  to=required_zone(I)
                else if (hasInputPort(toNm, "required_zone") && hasOutputPort(fromNm, "zone_true_out")) {
                    zoneDecisionOutputs.computeIfAbsent(fromName, k -> new LinkedHashMap<>())
                            .computeIfAbsent("zone_true_out", k -> new ArrayList<>()).add(toName);
                }
                else if (hasInputPort(toNm, "required_zone") && hasOutputPort(fromNm, "zone_false_out")) {
                    zoneDecisionOutputs.computeIfAbsent(fromName, k -> new LinkedHashMap<>())
                            .computeIfAbsent("zone_false_out", k -> new ArrayList<>()).add(toName);
                }
                // 7. 🔓 区域→区域解锁: from=unlock_out(O)  to=unlock_in(I) — 独立于区域依赖的解锁接口
                else if (hasOutputPort(fromNm, "unlock_out") && hasInputPort(toNm, "unlock_in")) {
                    wireBasedUnlockDeps.computeIfAbsent(toName, k -> new ArrayList<>()).add(fromName);
                }
            }
        }

        // ---- Phase 3: 构建据点数据 ----
        var player = mc().player;
        BlockPos defaultPos = player != null ? player.blockPosition() : BlockPos.ZERO;
        for (var entry : pointModels.entrySet()) {
            String name = entry.getKey();
            var nm = entry.getValue();
            String posStr = getOptionString(nm, "position");
            BlockPos pos = defaultPos;
            if (!posStr.isEmpty()) {
                String[] parts = posStr.split(",");
                if (parts.length == 3) {
                    try {
                        pos = new BlockPos(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim())
                        );
                    } catch (Exception ignored) {}
                }
            }
            double radius = getOptionDouble(nm, "radius");
            int color = getOptionInt(nm, "display_color");
            boolean showRange = getOptionBool(nm, "show_range");
            boolean captured = getOptionBool(nm, "captured");
            String ownerTeam = getOptionString(nm, "owner_team");
            String ownerTeamOrNull = ownerTeam.isEmpty() ? null : ownerTeam;
            newPoints.put(name, new CaptureManager.CapturePointEntry(
                    name, pos, captured,
                    ownerTeamOrNull, null, 0, null,
                    radius, color, showRange));
        }

        // ---- Phase 4: 判断器条件路由 ----
        // decisionRoutedPoints: zoneName → [pointName, ...] 通过判断器路由到区域的据点
        var decisionRoutedPoints = new LinkedHashMap<String, List<String>>();

        for (var decisionEntry : decisionModels.entrySet()) {
            String decisionName = decisionEntry.getKey();
            var nm = decisionEntry.getValue();

            // 读取判断器条件配置
            String condition = getOptionString(nm, "condition");
            String targetTeam = getOptionString(nm, "target_team");

            // 获取输入此判断器的据点列表
            List<String> inputPoints = decisionInputs.get(decisionName);
            if (inputPoints == null || inputPoints.isEmpty()) continue;

            // 获取此判断器的输出映射
            Map<String, List<String>> outputs = decisionOutputs.get(decisionName);
            if (outputs == null || outputs.isEmpty()) continue;

            // 对每个输入据点执行条件判断
            for (String pointName : inputPoints) {
                var pointEntry = newPoints.get(pointName);
                if (pointEntry == null) continue;

                // 评估条件
                boolean conditionMet = evaluateCondition(condition, targetTeam, pointEntry);

                // 根据结果选择输出端口
                String outputPort = conditionMet ? "true_out" : "false_out";
                List<String> targetZones = outputs.get(outputPort);
                if (targetZones == null || targetZones.isEmpty()) continue;

                // 将据点路由到所有连接到该输出端口的区域
                for (String zoneName : targetZones) {
                    decisionRoutedPoints.computeIfAbsent(zoneName, k -> new ArrayList<>()).add(pointName);
                }
            }
        }

        // ---- Phase 5: 构建区域数据 + 双向同步 ----
        for (var entry : zoneModels.entrySet()) {
            String name = entry.getKey();
            var nm = entry.getValue();

            boolean zoneCaptured = getOptionBool(nm, "captured");

            // 依赖区域：优先从连线解析，回退到选项值
            String reqZone = wireBasedRequiredZone.get(name);
            if (reqZone == null || reqZone.isEmpty()) {
                reqZone = getOptionString(nm, "required_zone");
            }

            // 合并据点：直连 + 判断器路由
            var cpList = new ArrayList<String>();
            var directPoints = wireBasedZonePoints.get(name);
            if (directPoints != null) {
                cpList.addAll(directPoints);
            }
            var routedPoints = decisionRoutedPoints.get(name);
            if (routedPoints != null) {
                // 避免重复
                for (String rp : routedPoints) {
                    if (!cpList.contains(rp)) {
                        cpList.add(rp);
                    }
                }
            }

            // 规则③：区域占领状态 → 强制同步到所有子据点
            for (var cpName : cpList) {
                var existing = newPoints.get(cpName);
                if (existing != null) {
                    newPoints.put(cpName, existing.withCaptured(zoneCaptured));
                }
            }

            // 🔓 解锁依赖：从 unlock_out → unlock_in 连线解析
            var unlockDeps = wireBasedUnlockDeps.get(name);
            var unlockDepList = unlockDeps != null ? unlockDeps : new ArrayList<String>();

            newZones.put(name, new CaptureManager.ZoneEntry(
                    name, cpList, reqZone.isEmpty() ? null : reqZone, zoneCaptured, null, unlockDepList));
        }

        // ---- Phase 6: 区域判断器条件路由 ----
        // 评估判断器的条件并路由区域依赖信号（zone_out → zone_target → decision → zone_true_out/false_out → required_zone）
        var zoneDecisionRoutedDeps = new LinkedHashMap<String, String>();

        for (var decisionEntry : decisionModels.entrySet()) {
            String decisionName = decisionEntry.getKey();
            var nm = decisionEntry.getValue();

            String condition = getOptionString(nm, "condition");
            String targetTeam = getOptionString(nm, "target_team");

            // 获取输入此判断器的区域列表（来自 zone_out → zone_target 连线）
            List<String> inputZones = zoneDecisionInputs.get(decisionName);
            if (inputZones == null || inputZones.isEmpty()) continue;

            // 获取此判断器的区域输出映射
            Map<String, List<String>> outputs = zoneDecisionOutputs.get(decisionName);
            if (outputs == null || outputs.isEmpty()) continue;

            // 对每个输入区域执行条件判断
            for (String zoneName : inputZones) {
                var zoneEntry = newZones.get(zoneName);
                if (zoneEntry == null) continue;

                // 评估条件（基于区域的状态：captured / owner_team / not_captured）
                boolean conditionMet = evaluateZoneCondition(condition, targetTeam, zoneEntry);

                // 根据结果选择输出端口
                String outputPort = conditionMet ? "zone_true_out" : "zone_false_out";
                List<String> targetZones = outputs.get(outputPort);
                if (targetZones == null || targetZones.isEmpty()) continue;

                // 将区域名称设置为下游区域的 requiredZone
                for (String targetZoneName : targetZones) {
                    zoneDecisionRoutedDeps.put(targetZoneName, zoneName);
                }
            }
        }

        // 用判断器路由的依赖关系覆盖区域 entries 的 requiredZone（判断器路由优先于直连）
        for (var entry : zoneDecisionRoutedDeps.entrySet()) {
            String zoneName = entry.getKey();
            String requiredZone = entry.getValue();
            var existing = newZones.get(zoneName);
            if (existing != null && requiredZone != null && !requiredZone.isEmpty()) {
                newZones.put(zoneName, existing.withRequiredZone(requiredZone));
            }
        }

        // 规则②：根据所有子据点的占领状态重新计算区域 captured
        for (var zoneName : List.copyOf(newZones.keySet())) {
            var zone = newZones.get(zoneName);
            boolean allCaptured = !zone.capturePoints().isEmpty();
            for (var cpName : zone.capturePoints()) {
                var cp = newPoints.get(cpName);
                if (cp == null || !cp.captured()) {
                    allCaptured = false;
                    break;
                }
            }
            newZones.put(zoneName, zone.withCaptured(allCaptured));
        }

        return new AbstractMap.SimpleEntry<>(newPoints, newZones);
    }

    /**
     * 评估判断器的条件是否满足。<br>
     * <br>
     * <b>支持的条件类型：</b>
     * <ul>
     *   <li>{@code captured} — 据点已被占领（captured == true）</li>
     *   <li>{@code not_captured} — 据点未被占领（captured == false）</li>
     *   <li>{@code owner_team} — 据点的 ownerTeam 匹配 target_team</li>
     *   <li>{@code capturing} — 据点的 capturingTeam 匹配 target_team</li>
     *   <li>{@code not_capturing} — 据点未被任何队伍占领中</li>
     * </ul>
     */
    private static boolean evaluateCondition(String condition, String targetTeam,
                                              CaptureManager.CapturePointEntry pointEntry) {
        if (condition == null || condition.isEmpty()) condition = "captured";

        return switch (condition) {
            case "captured" -> pointEntry.captured();
            case "not_captured" -> !pointEntry.captured();
            case "owner_team" -> {
                if (targetTeam == null || targetTeam.isEmpty()) yield false;
                yield targetTeam.equals(pointEntry.ownerTeam());
            }
            case "capturing" -> {
                if (targetTeam == null || targetTeam.isEmpty()) {
                    yield pointEntry.capturingTeam() != null;
                }
                yield targetTeam.equals(pointEntry.capturingTeam());
            }
            case "not_capturing" -> pointEntry.capturingTeam() == null;
            default -> false;
        };
    }

    /**
     * 评估判断器对区域的条件是否满足。<br>
     * <br>
     * <b>支持的条件类型：</b>
     * <ul>
     *   <li>{@code captured} — 区域已被占领（captured == true）</li>
     *   <li>{@code not_captured} — 区域未被占领（captured == false）</li>
     *   <li>{@code owner_team} — 区域的 ownerTeam 匹配 target_team</li>
     * </ul>
     */
    private static boolean evaluateZoneCondition(String condition, String targetTeam,
                                                  CaptureManager.ZoneEntry zoneEntry) {
        if (condition == null || condition.isEmpty()) condition = "captured";

        return switch (condition) {
            case "captured" -> zoneEntry.captured();
            case "not_captured" -> !zoneEntry.captured();
            case "owner_team" -> {
                if (targetTeam == null || targetTeam.isEmpty()) yield false;
                yield targetTeam.equals(zoneEntry.ownerTeam());
            }
            // capturing / not_capturing 对区域无效，默认走 false
            case "capturing", "not_capturing" -> false;
            default -> false;
        };
    }

    /**
     * 将当前节点图中的所有更改保存到 CaptureManager。
     * 使用快照方式：构建完整数据 → 版本检测 → applyGraphSnapshot 原子写入。
     */
    private void saveGraph() {
        var mgr = getCaptureManager();
        if (mgr == null) {
            ToastNotification.push(ToastNotification.Type.ERROR,
                    Component.literal("无法访问服务端数据，请使用命令 /capturepoint setcaptured 操作"));
            return;
        }

        try {
            // 构建数据快照
            var snapshot = buildSnapshotFromGraph();
            var newPoints = snapshot.getKey();
            var newZones = snapshot.getValue();

            // 收集所有节点的布局信息 + 判断器节点数据
            var layouts = new LinkedHashMap<String, CaptureManager.NodeLayout>();
            var decisions = new LinkedHashMap<String, CaptureManager.DecisionNodeData>();
            for (var element : graph.graphModel.getGraphElementModels()) {
                if (element instanceof NodeModel nm) {
                    String name = nm.getName();
                    if (name == null || name.isEmpty()) continue;
                    var pos = nm.getPosition();
                    if (pos != null) {
                        layouts.put(name, new CaptureManager.NodeLayout(pos.x(), pos.y()));
                    }
                    // 判断器节点额外保存 options
                    // 注意：nm 是 CustomNodeModelImpl（或子类），需要用 ICustomNodeModel.getNode() 获取原始 Node
                    if (nm instanceof ICustomNodeModel customNodeModel) {
                        Node originalNode = customNodeModel.getNode();
                        if (originalNode instanceof CaptureDecisionNode) {
                            String condition = readOptionString(nm, "condition");
                            String targetTeam = readOptionString(nm, "target_team");
                            int progress = readOptionInt(nm, "progress_threshold");
                            decisions.put(name, new CaptureManager.DecisionNodeData(
                                    name, pos != null ? pos.x() : 0, pos != null ? pos.y() : 0,
                                    condition != null ? condition : "captured",
                                    targetTeam, progress));
                        }
                    }
                }
            }

            // 编辑模式下进行版本冲突检测
            if (editMode && snapshotVersion >= 0) {
                long currentVersion = mgr.getVersion();
                if (currentVersion != snapshotVersion) {
                    // 数据已被外部修改（命令/方块），弹出确认对话框
                    openConflictDialog(mgr, newPoints, newZones, layouts, decisions);
                    return;
                }
            }

            // 无冲突或非编辑模式：直接应用（含布局 + 判断器）
            mgr.applyGraphSnapshotWithLayout(newPoints, newZones, layouts, decisions);

            // 立即同步所有已加载方块实体的渲染缓存
            var serverLevel = getServerLevel();
            if (serverLevel != null) {
                CapturePointBlockEntity.syncAllBoundBlocks(serverLevel);
            }

            ToastNotification.push(ToastNotification.Type.SUCCESS,
                    Component.translatable("toast.capture_point_graph.saved"));

        } catch (Exception e) {
            LOGGER.error("Save graph failed: {}", e.getMessage(), e);
            ToastNotification.push(ToastNotification.Type.ERROR,
                    Component.literal("Save failed: " + e.getMessage()));
        }
    }

    /** 读取节点模型的 String 类型选项值 */
    @Nullable
    private static String readOptionString(NodeModel nm, String optionId) {
        try {
            if (nm instanceof INodeWithOptions opts) {
                var opt = opts.getNodeOptionById(optionId);
                if (opt instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption nodeOpt) {
                    var port = nodeOpt.getPortModel();
                    if (port instanceof IFieldValueConfigurable cfg) {
                        var val = cfg.getValue();
                        return val != null ? val.toString() : null;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** 读取节点模型的 int 类型选项值 */
    private static int readOptionInt(NodeModel nm, String optionId) {
        try {
            if (nm instanceof INodeWithOptions opts) {
                var opt = opts.getNodeOptionById(optionId);
                if (opt instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption nodeOpt) {
                    var port = nodeOpt.getPortModel();
                    if (port instanceof IFieldValueConfigurable cfg) {
                        var val = cfg.getValue();
                        if (val instanceof Number n) return n.intValue();
                    }
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /**
     * 打开版本冲突对话框，询问用户是否覆盖外部修改的数据。
     */
    private void openConflictDialog(CaptureManager captureManager,
                                     Map<String, CaptureManager.CapturePointEntry> newPoints,
                                     Map<String, CaptureManager.ZoneEntry> newZones,
                                     Map<String, CaptureManager.NodeLayout> layouts,
                                     Map<String, CaptureManager.DecisionNodeData> decisions) {
        var mc = mc();
        int dw = 340, dh = 130;

        var root = CapturePointTheme.panel()
                .layout(l -> l.width(dw).height(dh).paddingAll(12).gapAll(8)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN));

        var title = CapturePointTheme.titleLabel(
                Component.translatable("gui.capture_point_graph.dialog.conflict.title"));
        title.layout(l -> l.widthPercent(100).heightAuto());
        root.addChildren(title);

        var msg = CapturePointTheme.secondaryLabel(
                Component.translatable("gui.capture_point_graph.dialog.conflict.message"));
        msg.layout(l -> l.widthPercent(100).heightAuto());
        root.addChildren(msg);

        var spacer = new UIElement().layout(l -> l.flex(1));

        var btnRow = new UIElement()
                .layout(l -> l.widthPercent(100).height(30)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(6));
        var overwriteBtn = CapturePointTheme.styledButton(
                Component.translatable("gui.capture_point_graph.dialog.conflict.overwrite"));
        overwriteBtn.layout(l -> l.flex(1).heightPercent(100));
        overwriteBtn.setOnClick(e -> {
            var overwriteMgr = getCaptureManager();
            if (overwriteMgr != null) {
                overwriteMgr.applyGraphSnapshotWithLayout(newPoints, newZones, layouts, decisions);
            } else {
                captureManager.applyGraphSnapshotWithLayout(newPoints, newZones, layouts, decisions);
            }

            var sl = getServerLevel();
            if (sl != null) {
                CapturePointBlockEntity.syncAllBoundBlocks(sl);
            }

            ToastNotification.push(ToastNotification.Type.SUCCESS,
                    Component.translatable("toast.capture_point_graph.saved"));
            mc.setScreen(null);
            new CapturePointGraphScreen(level).open();
        });

        var cancelBtn = CapturePointTheme.styledButton(
                Component.translatable("gui.capture_point_graph.dialog.cancel"));
        cancelBtn.layout(l -> l.flex(1).heightPercent(100));
        cancelBtn.setOnClick(e -> {
            mc.setScreen(null);
            new CapturePointGraphScreen(level).open();
        });

        btnRow.addChildren(overwriteBtn, cancelBtn);
        root.addChildren(title, msg, spacer, btnRow);

        var wrap = new UIElement()
                .layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(0).gapAll(0)
                        .justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER)
                        .alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER));
        wrap.addChildren(root);

        var ui = ModularUI.of(UI.of(wrap));
        mc.setScreen(new ModularUIScreen(ui,
                Component.translatable("gui.capture_point_graph.dialog.conflict.title")));
    }

    /** 检查节点是否有指定名称的输出端口 */
    private static boolean hasOutputPort(NodeModel nm, String portName) {
        return nm.getOutputsById().containsKey(portName);
    }

    /** 检查节点是否有指定名称的输入端口 */
    private static boolean hasInputPort(NodeModel nm, String portName) {
        return nm.getInputsById().containsKey(portName);
    }

    // ================================================================
    //  从 CaptureManager 加载数据到节点图
    // ================================================================

    private void loadDataToGraph() {
        try {
            var mgr = getCaptureManager();
            if (mgr == null) return;

            var points = mgr.getPoints();
            var zones = mgr.getZones();
            if (points.isEmpty() && zones.isEmpty()) return;

            // 获取保存的节点布局（如果有的话）
            var savedLayouts = mgr.getNodeLayouts();

            var pointModels = new LinkedHashMap<String, NodeModel>();
            var zoneModels = new LinkedHashMap<String, NodeModel>();

            float startX = 100;
            float startY = 100;
            float gapX = 220;
            float gapY = 120;
            int idx = 0;

            // 创建据点节点（优先使用保存的布局）
            for (var entry : points.values()) {
                var saved = savedLayouts.get(entry.name());
                float x = saved != null ? saved.x() : (startX + (idx % 4) * gapX);
                float y = saved != null ? saved.y() : (startY + (idx / 4) * gapY);
                var node = new CapturePointNode();
                var nodeModel = graph.graphModel.createNodeModel(node,
                        new org.joml.Vector2f(x, y));
                nodeModel.setName(entry.name());
                nodeModel.setTitle(Component.literal(entry.name()));
                pointModels.put(entry.name(), nodeModel);
                idx++;
            }

            // 创建区域节点（优先使用保存的布局）
            int zoneIdx = 0;
            for (var entry : zones.values()) {
                var saved = savedLayouts.get(entry.name());
                float x = saved != null ? saved.x() : (startX + 250 + (zoneIdx % 3) * gapX);
                float y = saved != null ? saved.y() : (startY + (zoneIdx / 3) * gapY + gapY);
                var node = new CaptureZoneNode();
                var nodeModel = graph.graphModel.createNodeModel(node,
                        new org.joml.Vector2f(x, y));
                nodeModel.setName(entry.name());
                nodeModel.setTitle(Component.literal(entry.name()));
                zoneModels.put(entry.name(), nodeModel);
                zoneIdx++;
            }

            // 创建判断器节点（从保存的数据恢复）
            var savedDecisions = mgr.getDecisionNodes();
            if (!savedDecisions.isEmpty()) {
                for (var entry : savedDecisions.entrySet()) {
                    var data = entry.getValue();
                    var node = new CaptureDecisionNode();
                    var nodeModel = graph.graphModel.createNodeModel(node,
                            new org.joml.Vector2f(data.x(), data.y()));
                    nodeModel.setName(data.name());
                    nodeModel.setTitle(Component.literal(data.name()));
                    setOptionValue(nodeModel, "condition", ConditionMode.fromId(data.condition()));
                    setOptionValue(nodeModel, "target_team", data.targetTeam() != null ? data.targetTeam() : "");
                    setOptionValue(nodeModel, "progress_threshold", data.progressThreshold());
                }
            }

            // 建立连线
            for (var zoneEntry : zones.values()) {
                var zoneModel = zoneModels.get(zoneEntry.name());
                if (zoneModel == null) continue;
                var pointInPort = zoneModel.getInputsById().get("point_in");
                if (pointInPort == null) continue;

                for (var pointName : zoneEntry.capturePoints()) {
                    var pointModel = pointModels.get(pointName);
                    if (pointModel == null) continue;
                    var signalPort = pointModel.getOutputsById().get("point_signal");
                    if (signalPort == null) continue;
                    try {
                        graph.graphModel.createWire(signalPort, pointInPort);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to create wire from point '{}' to zone '{}': {}",
                                pointName, zoneEntry.name(), e.getMessage());
                    }
                }
            }

            for (var zoneEntry : zones.values()) {
                if (zoneEntry.requiredZone() == null || zoneEntry.requiredZone().isEmpty()) continue;
                var zoneModel = zoneModels.get(zoneEntry.name());
                if (zoneModel == null) continue;
                var reqZoneInput = zoneModel.getInputsById().get("required_zone");
                if (reqZoneInput == null) continue;

                var requiredZoneModel = zoneModels.get(zoneEntry.requiredZone());
                if (requiredZoneModel == null) continue;
                var zoneOutPort = requiredZoneModel.getOutputsById().get("zone_out");
                if (zoneOutPort == null) continue;
                try {
                    graph.graphModel.createWire(zoneOutPort, reqZoneInput);
                } catch (Exception e) {
                    LOGGER.warn("Failed to create dependency wire from zone '{}' to zone '{}': {}",
                            zoneEntry.requiredZone(), zoneEntry.name(), e.getMessage());
                }
            }

            // 🔓 建立解锁连线（unlock_out → unlock_in），独立于区域依赖
            for (var zoneEntry : zones.values()) {
                if (zoneEntry.unlockDependencies() == null || zoneEntry.unlockDependencies().isEmpty()) continue;
                var zoneModel = zoneModels.get(zoneEntry.name());
                if (zoneModel == null) continue;
                var unlockInPort = zoneModel.getInputsById().get("unlock_in");
                if (unlockInPort == null) continue;

                for (var depName : zoneEntry.unlockDependencies()) {
                    var depZoneModel = zoneModels.get(depName);
                    if (depZoneModel == null) continue;
                    var unlockOutPort = depZoneModel.getOutputsById().get("unlock_out");
                    if (unlockOutPort == null) continue;
                    try {
                        graph.graphModel.createWire(unlockOutPort, unlockInPort);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to create unlock wire from zone '{}' to zone '{}': {}",
                                depName, zoneEntry.name(), e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.warn("Failed to load data to graph: {}", e.getMessage());
        }
    }

    // ================================================================
    //  实时刷新节点标题和选项（由 screenTick 每 ~0.75 秒驱动）
    // ================================================================

    /** 同步锁：当为 true 时，来自 Game→GUI 的同步不触发 GUI→Game 回写 */
    private boolean isSyncingFromGame = false;

    /**
     * 从 CaptureManager 读取最新数据，更新节点标题。
     * 非编辑模式：同步所有选项值（只读展示）
     * 编辑模式：只更新节点标题，不覆盖选项值（用户正在编辑）
     */
    public void refreshNodeTitles() {
        try {
            var mgr = getServerCaptureManager();
            if (mgr == null) return;

            var points = mgr.getPoints();
            var zones = mgr.getZones();

            isSyncingFromGame = true;
            try {
                for (var element : graph.graphModel.getGraphElementModels()) {
                    if (element instanceof NodeModel nm) {
                        String name = nm.getName();
                        if (name == null || name.isEmpty()) continue;

                        if (hasOutputPort(nm, "point_signal")) {
                            // 据点节点：显示名称 + 所属区域
                            var entry = points.get(name);
                            if (entry != null) {
                                String zoneName = mgr.findZoneForPoint(name);
                                String zoneDisplay = zoneName != null ? zoneName : "—";
                                nm.setTitle(Component.literal(name + " [" + zoneDisplay + "]"));
                                // 编辑模式下不覆盖选项值（用户正在编辑）
                                if (!editMode) {
                                    syncPointOptions(nm, entry, mgr.isZoneCaptured(name), zoneName);
                                }
                            } else {
                                nm.setTitle(Component.literal(name));
                            }
                        } else if (hasInputPort(nm, "target")) {
                            // 判断器节点：显示名称 + 条件类型
                            String condition = getOptionString(nm, "condition");
                            if (condition == null || condition.isEmpty()) condition = "captured";
                            nm.setTitle(Component.literal(name + " [? " + condition + "]"));
                        } else if (hasOutputPort(nm, "zone_out") || hasInputPort(nm, "point_in")) {
                            // 区域节点：显示名称 + 占领状态 + 点数
                            var entry = zones.get(name);
                            if (entry != null) {
                                boolean captured = mgr.isZoneCaptured(name);
                                boolean accessible = mgr.canAccessZone(name);
                                int ptCount = entry.capturePoints().size();
                                String status = captured ? "✓" : "✗";
                                String access = accessible ? "" : " 🔒";
                                nm.setTitle(Component.literal(name + " [" + status + " " + ptCount + "pts" + access + "]"));
                                // 编辑模式下不覆盖选项值
                                if (!editMode) {
                                    syncZoneOptions(nm, entry, captured, points);
                                }
                            } else {
                                nm.setTitle(Component.literal(name));
                            }
                        }
                    }
                }
            } finally {
                isSyncingFromGame = false;
            }
        } catch (Exception ignored) {}
    }

    /**
     * 通过 IFieldValueConfigurable API 正确设置节点选项值。
     * 这比反射可靠得多，会通过 Constant 更新值并自动刷新 UI。
     */
    private static void setOptionValue(NodeModel nm, String optionId, Object value) {
        try {
            if (nm instanceof INodeWithOptions opts) {
                var opt = opts.getNodeOptionById(optionId);
                // NodeOption 是 INodeOption 的实现类，包含 portModel 字段
                if (opt instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption nodeOpt) {
                    var port = nodeOpt.getPortModel();
                    if (port instanceof IFieldValueConfigurable configurable) {
                        configurable.setValue(value);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * 同步据点节点选项（captured / position / radius / display_color / show_range）
     * "所属"信息由 mgr.findZoneForPoint 获取并在标题中展示。
     * 编辑模式字段（radius / display_color / show_range）始终从 CaptureManager 同步。
     */
    private static void syncPointOptions(NodeModel nm, CaptureManager.CapturePointEntry entry, boolean isCaptured, @org.jetbrains.annotations.Nullable String zoneName) {
        setOptionValue(nm, "captured", entry.captured());
        setOptionValue(nm, "owner_team", entry.ownerTeam() != null ? entry.ownerTeam() : "");
        setOptionValue(nm, "position", entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ());
        // 编辑模式额外字段
        setOptionValue(nm, "radius", entry.radius());
        setOptionValue(nm, "display_color", entry.displayColor());
        setOptionValue(nm, "show_range", entry.showRange());
    }

    /**
     * 同步区域节点选项（captured / required_zone / points / description / edit_points）
     */
    private static void syncZoneOptions(NodeModel nm, CaptureManager.ZoneEntry entry, boolean captured,
                                         Map<String, CaptureManager.CapturePointEntry> points) {
        setOptionValue(nm, "captured", captured);
        setOptionValue(nm, "owner_team", entry.ownerTeam() != null ? entry.ownerTeam() : "");
        setOptionValue(nm, "required_zone", entry.requiredZone() != null ? entry.requiredZone() : "");
        setOptionValue(nm, "points", String.join(", ", entry.capturePoints()));
        // 计算区域控制进度
        String progressStr = buildZoneProgressString(entry, points);
        setOptionValue(nm, "zone_progress", progressStr);
        // 编辑模式额外字段
        setOptionValue(nm, "description", "");
        setOptionValue(nm, "edit_points", String.join(", ", entry.capturePoints()));
    }

    /** 构建区域控制进度字符串，如 "红:75% 蓝:25%" */
    private static String buildZoneProgressString(CaptureManager.ZoneEntry entry,
                                                   Map<String, CaptureManager.CapturePointEntry> points) {
        var teamCount = new LinkedHashMap<String, Integer>();
        int total = 0;
        for (var cpName : entry.capturePoints()) {
            var cp = points.get(cpName);
            if (cp != null && cp.ownerTeam() != null) {
                teamCount.merge(cp.ownerTeam(), 1, Integer::sum);
                total++;
            }
        }
        if (total == 0) return "无";
        var sb = new StringBuilder();
        for (var e : teamCount.entrySet()) {
            if (!sb.isEmpty()) sb.append(" ");
            int pct = e.getValue() * 100 / total;
            sb.append(e.getKey()).append(":").append(pct).append("%");
        }
        return sb.toString();
    }

    // ================================================================
    //  GUI→Game 回写机制
    // ================================================================

    /**
     * 为所有节点选项的常量注册值变更监听器。
     * 当用户在 UI 中编辑选项（如修改 owner）时，自动写回 CaptureManager。
     * 配合 isSyncingFromGame 锁避免 Game→GUI 同步时触发回写造成循环。
     */
    private void registerOptionChangeListeners() {
        try {
            var access = getServerCaptureManager();
            if (access == null) return;

            for (var element : graph.graphModel.getGraphElementModels()) {
                if (element instanceof NodeModel nm) {
                    String name = nm.getName();
                    if (name == null || name.isEmpty()) continue;

                    boolean isPointNode = hasOutputPort(nm, "point_signal");
                    for (var opt : nm.getNodeOptions()) {
                        var port = opt.getPortModel();
                        var constant = port.getConfigurableConstant();
                        if (constant != null) {
                            var optId = opt.getId(); // 捕获变量
                            constant.addListener(newValue -> {
                                // 正在从游戏同步时不触发回写
                                if (isSyncingFromGame) return;
                                onOptionValueChanged(access, name, isPointNode, optId, newValue);
                            });
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * 当节点选项值被用户编辑时调用。
     * 编辑模式下：变更仅保存在节点模型的 Constant 中，不写入 CaptureManager，
     * 所有改动通过 "保存" 按钮的 buildSnapshotFromGraph → applyGraphSnapshot 批量写入。
     * 非编辑模式：忽略回写（只读显示）。
     */
    private void onOptionValueChanged(ICaptureDataAccess access, String nodeName, boolean isPointNode,
                                       String optionId, Object newValue) {
        // 编辑模式下变更仅存储于节点模型的本地 Constant 中，
        // 等待用户点击"保存"时通过 buildSnapshotFromGraph → applyGraphSnapshot 批量写入。
        // 此处不做任何 CaptureManager 写入操作，以保持数据一致性。
    }

    // ================================================================
    //  工具方法
    // ================================================================

    private ServerLevel getServerLevel() {
        if (level instanceof ServerLevel sl) return sl;
        var mc = mc();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getLevel(level.dimension());
        }
        return null;
    }

    /** 获取服务端统一数据访问接口（单机/服务端均可用） */
    private ICaptureDataAccess getServerCaptureManager() {
        if (level instanceof ServerLevel sl) return ICaptureDataAccess.server(sl);
        var mc = mc();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            var sl = mc.getSingleplayerServer().getLevel(level.dimension());
            if (sl != null) return ICaptureDataAccess.server(sl);
        }
        return null;
    }

    /** 获取 CaptureManager 原始实例（用于布局持久化等接口未暴露的操作） */
    @Nullable
    private CaptureManager getCaptureManager() {
        if (level instanceof ServerLevel sl) return CaptureManager.get(sl);
        var mc = mc();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            var sl = mc.getSingleplayerServer().getLevel(level.dimension());
            if (sl != null) return CaptureManager.get(sl);
        }
        return null;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
