package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import net.minecraft.network.chat.Component;

/**
 * 逻辑门节点 — 组合多个布尔条件信号，输出合并后的布尔结果。<br>
 * <br>
 * <b>支持的门类型：</b>
 * <ul>
 *   <li>{@code AND} — 所有输入为 true 时输出 true</li>
 *   <li>{@code OR} — 任一输入为 true 时输出 true</li>
 *   <li>{@code NOT} — 反转输入（仅使用 in_a）</li>
 *   <li>{@code XOR} — 恰好一个输入为 true 时输出 true</li>
 * </ul>
 * <br>
 * <b>信号流：</b><br>
 * 接收 BOOLEAN_SIGNAL（in_a, in_b），根据 gate_type 运算，<br>
 * 输出 BOOLEAN_SIGNAL（result）。
 */
public class LogicGateNode extends Node {

    public LogicGateNode() {
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("node.logic_gate.display_name");
    }

    @Override
    public IGuiTexture getNodeIcon() {
        return new ColorRectTexture(0xFF9C27B0); // 紫色
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        super.onDefineOptions(context);

        // gate_type 逻辑门类型
        context.addOption("gate_type", GateType.class)
                .withDefaultValue(GateType.AND)
                .withDisplayName(Component.translatable("node.logic_gate.option.gate_type"))
                .build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);

        // 布尔信号输入
        context.addInputPort("in_a", CapturePointTypes.BOOLEAN_SIGNAL)
                .withDisplayName(Component.translatable("node.logic_gate.port.in_a"))
                .build();

        context.addInputPort("in_b", CapturePointTypes.BOOLEAN_SIGNAL)
                .withDisplayName(Component.translatable("node.logic_gate.port.in_b"))
                .build();

        // 布尔信号输出
        context.addOutputPort("result", CapturePointTypes.BOOLEAN_SIGNAL)
                .withDisplayName(Component.translatable("node.logic_gate.port.result"))
                .build();
    }

    // ================================================================
    //  逻辑门类型枚举
    // ================================================================

    public enum GateType {
        AND("and"),
        OR("or"),
        NOT("not"),
        XOR("xor");

        private final String id;

        GateType(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public String getTranslationKey() {
            return "gate_type.logic_gate." + id;
        }

        public Component getDisplayName() {
            return Component.translatable(getTranslationKey());
        }

        @Override
        public String toString() {
            // 返回本地化文本，用于 LDLib2 枚举下拉菜单显示
            return getDisplayName().getString();
        }

        public String getSerializationId() {
            return id;
        }

        public static GateType fromId(String id) {
            if (id == null || id.isEmpty()) return AND;
            for (var g : values()) {
                if (g.id.equals(id)) return g;
            }
            return AND;
        }

        /**
         * 评估逻辑门运算。
         * @param a 第一个输入值
         * @param b 第二个输入值（NOT 时忽略）
         * @return 运算结果
         */
        public boolean evaluate(boolean a, boolean b) {
            return switch (this) {
                case AND -> a && b;
                case OR -> a || b;
                case NOT -> !a;
                case XOR -> a ^ b;
            };
        }
    }
}
