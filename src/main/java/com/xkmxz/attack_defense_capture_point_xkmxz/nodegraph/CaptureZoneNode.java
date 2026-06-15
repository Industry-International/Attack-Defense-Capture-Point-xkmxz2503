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
 * 区域节点 - 代表一个占领区域。
 * 具有一个输入端口（point_in）接收来自 CapturePointNode 的连接（表示"区域包含据点"），
 * 一个输出端口（zone_out）连接到其他区域的依赖输入（表示"区域先后关系"），
 * 以及一个输入端口（required_zone）接收来自其他区域的依赖连接。
 * 编辑模式：description、can_edit_points 可编辑
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
        pointInPort.setPortCapacity(PortCapacity.MULTIPLE); // 允许多个据点连线到此端口
        // 输出端口 - 发出区域信号（连接到其他区域的 required_zone 输入，表示区域先后关系）
        context.addOutputPort("zone_out", CapturePointTypes.ZONE_SIGNAL)
                .withDisplayName(Component.translatable("node.capture_zone.port.zone_out"))
                .build();
        // 输入端口 - 接收区域依赖信号（该区域依赖的另一个区域）
        context.addInputPort("required_zone", CapturePointTypes.ZONE_SIGNAL)
                .withDisplayName(Component.translatable("node.capture_zone.port.required_zone"))
                .build();
    }
}
