package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphView;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.GraphElementModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.INodeWithOptions;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.wire.WireModel;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.CaptureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.stream.Stream;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * 据点管理图视图 - 使用 LDLib2 nodegraphtookit 框架提供节点图编辑体验。
 * 右键菜单功能全部独立于指令调用，直接通过命令与服务端同步。
 */
public class CapturePointGraphView extends GraphView {

    private Level level;
    private ToastLayer toastLayer;
    private Runnable refreshCallback;
    private int refreshCounter;
    private CapturePointGraphScreen screen;
    private float canvasWidth = 800f;
    private float canvasHeight = 800f;
    /** 待恢复的视角状态（延迟到第一帧 screenTick 后应用） */
    private boolean hasPendingViewState = false;
    private float pendingOffsetX, pendingOffsetY, pendingScale;
    /** 是否在下一帧执行 fitGraphChildren */
    private boolean pendingFit = false;

    private static final Logger LOGGER = LogUtils.getLogger();

    public CapturePointGraphView() {
        super();
        hideHeaders();
        hidePanels();
        suppressDefaultItemLibrary();
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public void setToastLayer(ToastLayer toastLayer) {
        this.toastLayer = toastLayer;
    }

    /**
     * 设置节点数据刷新回调，每 tick 都会调用（内部按间隔节流）。
     */
    public void setScreen(CapturePointGraphScreen screen) {
        this.screen = screen;
    }

    public void setRefreshCallback(Runnable callback) {
        this.refreshCallback = callback;
    }

    private void hideHeaders() {
        header.setVisible(false);
        header.setDisplay(false);
        header.layout(l -> l.width(0).height(0));
    }

    private void hidePanels() {
        getPanelLayer().clearAllChildren();
        getPanelLayer().setVisible(false);
        getPanelLayer().setDisplay(false);
    }

    /** 编辑模式下显示面板层，让选项检查器出现并可编辑 */
    public void showPanelsForEditMode(boolean show) {
        var panelLayer = getPanelLayer();
        if (show) {
            panelLayer.setVisible(true);
            panelLayer.setDisplay(true);
        } else {
            // 非编辑模式：隐藏面板，回到初始状态
            panelLayer.clearAllChildren();
            panelLayer.setVisible(false);
            panelLayer.setDisplay(false);
        }
    }

    private void suppressDefaultItemLibrary() {
        itemLibrary.setVisible(false);
        itemLibrary.setDisplay(false);
        itemLibrary.setAllowHitTest(false);
        itemLibrary.layout(l -> {
            l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE);
            l.left(-100000f);
            l.top(-100000f);
            l.width(0);
            l.height(0);
        });
    }

    @Override
    public CapturePointGraphView loadGraph(Graph graph) {
        super.loadGraph(graph);
        suppressDefaultItemLibrary();
        return this;
    }

    @Override
    public void screenTick() {
        hideDefaultItemLibraryIfDisplayed();
        super.screenTick();
        hideDefaultItemLibraryIfDisplayed();

        // 应用待处理的视角状态（延迟到 layout 完成后的第一帧）
        if (hasPendingViewState) {
            hasPendingViewState = false;
            try {
                this.graphView.setOffsetX(pendingOffsetX);
                this.graphView.setOffsetY(pendingOffsetY);
                // 使用反射设置缩放（LDLib2 的 GraphView 未公开 setScale）
                setGraphViewScale(pendingScale);
                LOGGER.info("Restored view state: offsetX={}, offsetY={}, scale={}",
                        pendingOffsetX, pendingOffsetY, pendingScale);
            } catch (Exception e) {
                LOGGER.warn("Failed to restore view state, falling back to fit", e);
                this.fitGraphChildren(40f);
            }
        } else if (pendingFit) {
            pendingFit = false;
            this.fitGraphChildren(40f);
        }

        // 驱动通知气泡动画
        if (toastLayer != null) {
            toastLayer.tick();
        }

        // 每15 tick（约0.75秒）刷新一次节点标题，显示实时数据
        refreshCounter++;
        if (refreshCounter >= 15 && refreshCallback != null) {
            refreshCounter = 0;
            refreshCallback.run();
        }
    }

    private void hideDefaultItemLibraryIfDisplayed() {
        if (itemLibrary.isDisplayed()) {
            itemLibrary.hide();
        }
        suppressDefaultItemLibrary();
    }

    // ---- 视角状态管理 ----

    /**
     * 设置待恢复的视角状态，将在下一帧 screenTick 时应用。
     */
    public void setPendingViewState(float offsetX, float offsetY, float scale) {
        this.hasPendingViewState = true;
        this.pendingOffsetX = offsetX;
        this.pendingOffsetY = offsetY;
        this.pendingScale = scale;
    }

    /** 请求在下一帧执行 fitGraphChildren */
    public void requestFitOnNextTick() {
        this.pendingFit = true;
    }

    /** 获取当前视角状态 */
    public CaptureManager.ViewState getCurrentViewState() {
        return new CaptureManager.ViewState(
                this.graphView.getOffsetX(),
                this.graphView.getOffsetY(),
                this.graphView.getScale(),
                canvasWidth,
                canvasHeight
        );
    }

    public void setCanvasSize(float width, float height) {
        this.canvasWidth = Math.max(200f, width);
        this.canvasHeight = Math.max(200f, height);
    }

    public float getCanvasWidth() {
        return canvasWidth;
    }

    public float getCanvasHeight() {
        return canvasHeight;
    }

    /**
     * 通过反射设置内部 graphView 的缩放值（LDLib2 未公开 setScale 方法）。
     */
    private void setGraphViewScale(float scale) {
        try {
            var field = this.graphView.getClass().getDeclaredField("scale");
            field.setAccessible(true);
            field.setFloat(this.graphView, scale);
            // 刷新变换矩阵（protected 方法，需要反射调用）
            var refreshMethod = this.graphView.getClass().getDeclaredMethod("refreshContentTransform");
            refreshMethod.setAccessible(true);
            refreshMethod.invoke(this.graphView);
        } catch (Exception e) {
            LOGGER.warn("Cannot set graph view scale via reflection", e);
        }
    }

    /**
     * 将屏幕坐标转换为图空间坐标（通过 contentViewContainer 的 getLocalMouse 方法，
     * 自动处理所有嵌套变换，包括平移和缩放）。
     * 参考 synaxis CircuitGraphView.createMenu() 的实现方式。
     */
    private org.joml.Vector2f screenToGraphCoords(float screenX, float screenY) {
        // getContentViewContainer().getLocalMouse() 正确地将 GraphView 本地坐标
        // 转换为内容容器本地坐标（图空间坐标），自动处理 offset/scale 变换
        return getContentViewContainer().getLocalMouse(screenX, screenY);
    }

    public org.joml.Vector2f getGraphCoordsAtScreen(float screenX, float screenY) {
        return screenToGraphCoords(screenX, screenY);
    }

    // ---- 右键菜单 ----

    @Override
    protected TreeBuilder.Menu createMenu(float screenX, float screenY) {
        var menu = TreeBuilder.Menu.start();
        boolean isEditing = screen != null && screen.isEditMode();

        // 创建据点
        menu.leaf(
                Component.translatable("gui.capture_point_graph.menu.create_point").getString(),
                () -> {
                    if (level != null) {
                        CapturePointGraphDialogs.openCreatePointDialog(level);
                    }
                });

        // 创建区域
        menu.leaf(
                Component.translatable("gui.capture_point_graph.menu.create_zone").getString(),
                () -> {
                    if (level != null) {
                        CapturePointGraphDialogs.openCreateZoneDialog(level);
                    }
                });

        // 创建条件节点
        menu.leaf(
                Component.translatable("gui.capture_point_graph.menu.create_condition").getString(),
                () -> createCustomNode(screenX, screenY, new CaptureConditionNode(),
                        "condition_", "node.capture_condition.display_name"));

        // 创建逻辑门节点
        menu.leaf(
                Component.translatable("gui.capture_point_graph.menu.create_logic_gate").getString(),
                () -> createCustomNode(screenX, screenY, new LogicGateNode(),
                        "gate_", "node.logic_gate.display_name"));

        // 创建动作节点
        menu.leaf(
                Component.translatable("gui.capture_point_graph.menu.create_action").getString(),
                () -> createCustomNode(screenX, screenY, new CaptureActionNode(),
                        "action_", "node.capture_action.display_name"));

        // 创建常量节点
        menu.leaf(
                Component.translatable("gui.capture_point_graph.menu.create_constant").getString(),
                () -> createCustomNode(screenX, screenY, new ConstantNode(),
                        "const_", "node.constant.display_name"));

        // 如果有选中的连线，添加"删除连线"操作（直接删除，无需确认对话框）
        boolean hasWireSelected = getSelected().stream().anyMatch(m -> m instanceof WireModel);
        if (hasWireSelected) {
            menu.leaf(
                    Component.translatable("gui.capture_point_graph.menu.delete_wire").getString(),
                    () -> deleteSelectedElements(m -> m instanceof WireModel));
        }

        // 如果有选中的节点，添加操作选项
        var selectedModels = getSelectedModels();
        if (!selectedModels.isEmpty()) {
            // 编辑模式下：添加"编辑属性"选项（优先显示）
            if (isEditing) {
                for (var model : selectedModels) {
                    String nodeName = model.getName();
                    menu.leaf(
                            Component.translatable("gui.capture_point_graph.menu.edit_properties").getString(),
                            () -> {
                                if (level != null) {
                                    CapturePointGraphDialogs.openEditPropertiesDialog(level, nodeName, getNodeType(model));
                                }
                            });
                    break;
                }
            }

            // 删除选项（编辑模式和非编辑模式都有）
            menu.leaf(
                    Component.translatable("gui.capture_point_graph.menu.delete").getString(),
                    () -> {
                        if (level != null) {
                            // 逐个处理选中的节点
                            for (var model : selectedModels) {
                                String name = model.getName();
                                String nodeType = getNodeType(model);
                                CapturePointGraphDialogs.openDeleteConfirmDialog(level, name, nodeType);
                                // 注意：只处理第一个选中项，避免多个弹窗
                                break;
                            }
                        }
                    });

            // 非编辑模式下：高级配置（仅据点节点）
            if (!isEditing) {
                for (var model : selectedModels) {
                    if (isPointModel(model)) {
                        String pointName = model.getName();
                        menu.leaf(
                                Component.translatable("gui.capture_point_graph.menu.advanced_config").getString(),
                                () -> {
                                    if (level != null) {
                                        CapturePointGraphDialogs.openAdvancedConfigDialog(level, pointName);
                                    }
                                });
                    }
                    break;
                }
            }
        }

        // 查看状态
        menu.leaf(Component.translatable("gui.capture_point_graph.menu.list_status").getString(), () -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.connection.sendCommand("capturepoint list");
            }
        });

        // 刷新
        menu.leaf(Component.translatable("gui.capture_point_graph.menu.refresh").getString(), () -> {
            var mc = Minecraft.getInstance();
            if (level != null) {
                mc.setScreen(null);
                new CapturePointGraphScreen(level).open();
            }
        });

        // 关闭
        menu.leaf(Component.translatable("gui.capture_point_graph.menu.close").getString(), () -> {
            Minecraft.getInstance().setScreen(null);
        });

        return menu;
    }

    /**
     * 通用方法：在右键点击位置创建自定义节点模型。
     * 将屏幕坐标（screenX, screenY）转换为图空间坐标，确保节点出现在鼠标位置下。
     */
    private void createCustomNode(float screenX, float screenY,
                                   com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node node,
                                   String namePrefix, String langKey) {
        if (level != null && screen != null) {
            // 将屏幕坐标转换为图空间坐标（考虑平移和缩放）
            var graphPos = screenToGraphCoords(screenX, screenY);
            var nodeModel = screen.getGraph().graphModel.createNodeModel(node,
                    new org.joml.Vector2f(graphPos.x, graphPos.y));
            String name = namePrefix + java.util.UUID.randomUUID().toString().substring(0, 6);
            nodeModel.setName(name);
            nodeModel.setTitle(Component.translatable(langKey));
            ToastNotification.push(ToastNotification.Type.SUCCESS,
                    Component.translatable("toast.capture_node.create.success", name));
        }
    }

    /**
     * 获取选中的节点模型列表（只包含 AbstractNodeModel）。
     */
    private List<AbstractNodeModel> getSelectedModels() {
        return getSelected().stream()
                .filter(m -> m instanceof AbstractNodeModel)
                .map(m -> (AbstractNodeModel) m)
                .toList();
    }

    /**
     * 判断节点模型是否为区域节点（通过检查是否有 "required_zone" 选项）。
     */
    private boolean isZoneModel(AbstractNodeModel model) {
        if (model instanceof INodeWithOptions opts) {
            return opts.getNodeOptionById("required_zone") != null;
        }
        return false;
    }

    /**
     * 判断节点模型是否为据点节点（通过检查是否有 "owner_team" 选项）。
     */
    private boolean isPointModel(AbstractNodeModel model) {
        if (model instanceof INodeWithOptions opts) {
            return opts.getNodeOptionById("owner_team") != null;
        }
        return false;
    }

    /**
     * 判断节点模型是否为条件节点（通过检查是否有 "condition_type" 选项）。
     */
    private boolean isConditionModel(AbstractNodeModel model) {
        if (model instanceof INodeWithOptions opts) {
            return opts.getNodeOptionById("property") != null
                    || opts.getNodeOptionById("condition_type") != null;
        }
        return false;
    }

    /**
     * 判断节点模型是否为逻辑门节点（通过检查是否有 "gate_type" 选项）。
     */
    private boolean isGateModel(AbstractNodeModel model) {
        if (model instanceof INodeWithOptions opts) {
            return opts.getNodeOptionById("gate_type") != null;
        }
        return false;
    }

    /**
     * 判断节点模型是否为动作节点（通过检查是否有 "action_type" 选项）。
     */
    private boolean isActionModel(AbstractNodeModel model) {
        if (model instanceof INodeWithOptions opts) {
            return opts.getNodeOptionById("action_type") != null;
        }
        return false;
    }

    private boolean isConstantModel(AbstractNodeModel model) {
        if (model instanceof INodeWithOptions opts) {
            return opts.getNodeOptionById("constant_value") != null;
        }
        return false;
    }

    /**
     * 获取节点类型字符串： "point" / "zone" / "condition" / "gate" / "action" / "constant" / "decision"。
     */
    private String getNodeType(AbstractNodeModel model) {
        if (isZoneModel(model)) return "zone";
        if (isPointModel(model)) return "point";
        if (isConditionModel(model)) return "condition";
        if (isGateModel(model)) return "gate";
        if (isActionModel(model)) return "action";
        if (isConstantModel(model)) return "constant";
        // fallback for old-style decision node
        if (model instanceof INodeWithOptions opts
                && opts.getNodeOptionById("condition") != null) return "decision";
        return "unknown";
    }
}
