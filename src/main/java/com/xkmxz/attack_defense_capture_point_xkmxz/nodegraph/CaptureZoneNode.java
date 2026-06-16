package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortCapacity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import net.minecraft.network.chat.Component;

/**
 * 区域节点 - 代表一个占领区域。<br>
 * <br>
 * <b>职责：纯结构声明，不负责逻辑判断。</b><br>
 * <ul>
 *   <li>point_in — 接收据点信号（声明哪些据点属于此区域）</li>
 *   <li>zone_out — 发出依赖信号（声明此区域是其他区域的前置）</li>
 *   <li>required_zone — 接收依赖信号（声明此区域依赖哪个前置区域）</li>
 *   <li>unlock_in — 接收解锁信号（由逻辑组件在运行时控制）</li>
 * </ul>
 * 所有逻辑判断（条件评估、解锁控制等）由 CaptureConditionNode、LogicGateNode、
 * CaptureActionNode 等逻辑部件完成。
 */
public class CaptureZoneNode extends Node {

    public CaptureZoneNode() {
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("node.capture_zone.display_name");
    }

    @Override
    public IGuiTexture getNodeIcon() {
        return new ColorRectTexture(0xFF42A5F5); // 蓝色图标
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        super.onDefineOptions(context);
        // captured 状态选项
        context.addOption("captured", Boolean.class)
                .withDefaultValue(false)
                .withDisplayName(Component.translatable("node.capture_zone.option.captured"))
                .build();
        // owner_team 区域占领队伍（只读显示）
        context.addOption("owner_team", String.class)
                .withDefaultValue("")
                .withDisplayName(Component.translatable("node.capture_zone.option.owner_team"))
                .build();
        // zone_progress 区域控制进度（只读显示）
        context.addOption("zone_progress", String.class)
                .withDefaultValue("")
                .withDisplayName(Component.translatable("node.capture_zone.option.zone_progress"))
                .build();
        // requiredZone 依赖区域名称（编辑模式下可编辑）
        context.addOption("required_zone", String.class)
                .withDefaultValue("")
                .withDisplayName(Component.translatable("node.capture_zone.option.required_zone"))
                .build();
        // 包含的据点列表（只读展示用）
        context.addOption("points", String.class)
                .withDefaultValue("")
                .withDisplayName(Component.translatable("node.capture_zone.option.points"))
                .build();
        // ---- 编辑模式可用选项 ----
        // description 区域描述（编辑模式下可编辑）
        context.addOption("description", String.class)
                .withDefaultValue("")
                .withDisplayName(Component.translatable("node.capture_zone.option.description"))
                .build();
        // can_edit_points 可编辑的据点列表（编辑模式下手动指定据点名称，逗号分隔）
        context.addOption("edit_points", String.class)
                .withDefaultValue("")
                .withDisplayName(Component.translatable("node.capture_zone.option.edit_points"))
                .build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        // 输入端口 - 接收据点信号（多个据点可通过此端口加入区域，支持多连线）
        var pointInPort = (PortModel) context.addInputPort("point_in", CapturePointTypes.POINT_SIGNAL)
                .withDisplayName(Component.translatable("node.capture_zone.port.point_in"))
                .build();
        pointInPort.setPortCapacity(PortCapacity.MULTIPLE);

        // ---- 区域依赖接口 (zone_out → required_zone) ----
        // 输出端口 - 发出区域信号（连接到其他区域的 required_zone 输入，表示区域先后关系）
        context.addOutputPort("zone_out", CapturePointTypes.ZONE_SIGNAL)
                .withDisplayName(Component.translatable("node.capture_zone.port.zone_out"))
                .build();
        // 输入端口 - 接收区域依赖信号（该区域依赖的另一个区域）
        context.addInputPort("required_zone", CapturePointTypes.ZONE_SIGNAL)
                .withDisplayName(Component.translatable("node.capture_zone.port.required_zone"))
                .build();

        // ---- 区域解锁接口（仅输入）----
        // 输入端口 - 接收布尔解锁信号（由条件节点/逻辑门节点的输出直接连接）
        // 类型改为 BOOLEAN_SIGNAL，使条件节点 true_out/false_out 可直接连接到此端口
        var unlockInPort = (PortModel) context.addInputPort("unlock_in", CapturePointTypes.BOOLEAN_SIGNAL)
                .withDisplayName(Component.translatable("node.capture_zone.port.unlock_in"))
                .build();
        unlockInPort.setPortCapacity(PortCapacity.MULTIPLE);
    }
}
