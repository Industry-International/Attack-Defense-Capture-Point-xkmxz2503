package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import net.minecraft.network.chat.Component;

/**
 * 据点节点 - 代表一个占领点。
 * 具有一个输出端口（point_signal），可连接到 CaptureZoneNode 的输入端口。
 * 普通模式：owner（可编辑）、position（只读显示）、captured（只读显示）
 * 编辑模式：radius、displayColor、showRange 均可编辑
 */
public class CapturePointNode extends Node {

    public CapturePointNode() {
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("node.capture_point.display_name");
    }

    @Override
    public IGuiTexture getNodeIcon() {
        return new ColorRectTexture(0xFF66BB6A); // 绿色图标
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        super.onDefineOptions(context);
        // captured 状态选项（检查器显示）
        context.addOption("captured", Boolean.class)
                .withDefaultValue(false)
                .withDisplayName(Component.translatable("node.capture_point.option.captured"))
                .withoutConfigurator() // 不在节点体内显示，仅在检查器显示
                .build();
        // owner 所属者 - 字符串类型（可编辑）
        context.addOption("owner", String.class)
                .withDefaultValue("")
                .withDisplayName(Component.translatable("node.capture_point.option.owner"))
                .build();
        // position 位置 - 只读坐标信息
        context.addOption("position", String.class)
                .withDefaultValue("0, 0, 0")
                .withDisplayName(Component.translatable("node.capture_point.option.position"))
                .build();
        // ---- 以下为编辑模式可用选项 ----
        // radius 半径（编辑模式下可编辑）
        context.addOption("radius", Double.class)
                .withDefaultValue(5.0)
                .withDisplayName(Component.translatable("node.capture_point.option.radius"))
                .build();
        // displayColor 显示颜色（编辑模式下可编辑）
        context.addOption("display_color", Integer.class)
                .withDefaultValue(0xFFFF4444)
                .withDisplayName(Component.translatable("node.capture_point.option.display_color"))
                .build();
        // showRange 是否显示范围轮廓（编辑模式下可编辑）
        context.addOption("show_range", Boolean.class)
                .withDefaultValue(false)
                .withDisplayName(Component.translatable("node.capture_point.option.show_range"))
                .build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);
        // 输出端口 - 据点信号
        context.addOutputPort("point_signal", CapturePointTypes.POINT_SIGNAL)
                .withDisplayName(Component.translatable("node.capture_point.port.point_signal"))
                .build();
    }
}
