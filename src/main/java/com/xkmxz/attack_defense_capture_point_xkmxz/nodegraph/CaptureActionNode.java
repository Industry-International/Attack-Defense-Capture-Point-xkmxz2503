package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import net.minecraft.network.chat.Component;

/**
 * 动作节点 — 当收到 true 信号时执行指定的动作。<br>
 * <br>
 * <b>信号流：</b><br>
 * 接收 BOOLEAN_SIGNAL（trigger），当值为 true 时执行配置的动作。<br>
 * <br>
 * <b>支持的动作类型：</b>
 * <ul>
 *   <li>{@code set_captured} — 设置据点的占领状态</li>
 *   <li>{@code set_owner_team} — 设置据点的占领队伍</li>
 *   <li>{@code set_zone_captured} — 设置区域的占领状态</li>
 *   <li>{@code notify} — 发送通知气泡</li>
 *   <li>{@code add_to_zone} — 将据点添加到区域</li>
 *   <li>{@code remove_from_zone} — 从区域移除据点</li>
 * </ul>
 */
public class CaptureActionNode extends Node {

    public CaptureActionNode() {
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("node.capture_action.display_name");
    }

    @Override
    public IGuiTexture getNodeIcon() {
        return new ColorRectTexture(0xFFE53935); // 红色
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        super.onDefineOptions(context);

        // action_type 动作类型
        context.addOption("action_type", ActionType.class)
                .withDefaultValue(ActionType.SET_CAPTURED)
                .withDisplayName(Component.translatable("node.capture_action.option.action_type"))
                .build();

        // target_name 目标名称（据点名或区域名）
        context.addOption("target_name", String.class)
                .withDefaultValue("")
                .withDisplayName(Component.translatable("node.capture_action.option.target_name"))
                .build();

        // action_value 动作值（队伍名、true/false 等）
        context.addOption("action_value", String.class)
                .withDefaultValue("true")
                .withDisplayName(Component.translatable("node.capture_action.option.action_value"))
                .build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);

        // 触发输入（布尔信号）
        context.addInputPort("trigger", CapturePointTypes.BOOLEAN_SIGNAL)
                .withDisplayName(Component.translatable("node.capture_action.port.trigger"))
                .build();
    }

    // ================================================================
    //  动作类型枚举
    // ================================================================

    public enum ActionType {
        SET_CAPTURED("set_captured"),
        SET_OWNER_TEAM("set_owner_team"),
        SET_ZONE_CAPTURED("set_zone_captured"),
        NOTIFY("notify"),
        ADD_TO_ZONE("add_to_zone"),
        REMOVE_FROM_ZONE("remove_from_zone");

        private final String id;

        ActionType(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public String getTranslationKey() {
            return "action_type.capture_action." + id;
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

        public static ActionType fromId(String id) {
            if (id == null || id.isEmpty()) return SET_CAPTURED;
            for (var a : values()) {
                if (a.id.equals(id)) return a;
            }
            return SET_CAPTURED;
        }
    }
}
