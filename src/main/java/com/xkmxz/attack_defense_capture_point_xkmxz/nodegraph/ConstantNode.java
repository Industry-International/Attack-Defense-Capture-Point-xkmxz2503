package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import net.minecraft.network.chat.Component;

/**
 * 常量/变量节点 — 提供一个可配置的常量值，用于为其他节点提供比较值。<br>
 * <br>
 * <b>信号流：</b><br>
 * 输出 BOOLEAN_SIGNAL（value），值为 options 中配置的布尔值。<br>
 * 可用于为逻辑门节点提供固定输入，或作为动作节点的开关。<br>
 * <br>
 * <b>使用场景：</b><br>
 * <ul>
 *   <li>为 AND/OR 逻辑门提供常 true/false 输入</li>
 *   <li>作为系统开关控制某条链路是否激活</li>
 * </ul>
 */
public class ConstantNode extends Node {

    public ConstantNode() {
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("node.constant.display_name");
    }

    @Override
    public IGuiTexture getNodeIcon() {
        return new ColorRectTexture(0xFF78909C); // 灰蓝色
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        super.onDefineOptions(context);

        // 常量值（布尔开关）
        context.addOption("constant_value", Boolean.class)
                .withDefaultValue(true)
                .withDisplayName(Component.translatable("node.constant.option.constant_value"))
                .build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);

        // 布尔信号输出
        context.addOutputPort("value", CapturePointTypes.BOOLEAN_SIGNAL)
                .withDisplayName(Component.translatable("node.constant.port.value"))
                .build();
    }
}
