package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import net.minecraft.network.chat.Component;

/**
 * 条件节点 — 根据配置的条件类型评估据点/区域状态，输出布尔信号到 true_out 或 false_out。<br>
 * <br>
 * <b>信号流：</b><br>
 * 接收 POINT_SIGNAL（point_target）或 ZONE_SIGNAL（zone_target），<br>
 * 根据 condition_type 和 compare_value 评估，输出 BOOLEAN_SIGNAL。<br>
 * <br>
 * <b>支持的条件类型：</b>
 * <ul>
 *   <li>{@code point_captured} — 据点已被占领</li>
 *   <li>{@code point_not_captured} — 据点未被占领</li>
 *   <li>{@code point_owner_team} — 据点 ownerTeam 匹配 compare_value</li>
 *   <li>{@code point_not_owner_team} — 据点 ownerTeam 不匹配 compare_value</li>
 *   <li>{@code point_capturing_team} — 据点 capturingTeam 匹配 compare_value</li>
 *   <li>{@code point_progress_ge} — 据点占领进度 ≥ compare_value</li>
 *   <li>{@code zone_captured} — 区域已被占领</li>
 *   <li>{@code zone_not_captured} — 区域未被占领</li>
 *   <li>{@code zone_owner_team} — 区域 ownerTeam 匹配 compare_value</li>
 *   <li>{@code zone_accessible} — 区域可访问</li>
 * </ul>
 */
public class CaptureConditionNode extends Node {

    public CaptureConditionNode() {
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("node.capture_condition.display_name");
    }

    @Override
    public IGuiTexture getNodeIcon() {
        return new ColorRectTexture(0xFFFF9800); // 橙色
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        super.onDefineOptions(context);

        // condition_type 条件类型
        context.addOption("condition_type", ConditionType.class)
                .withDefaultValue(ConditionType.POINT_CAPTURED)
                .withDisplayName(Component.translatable("node.capture_condition.option.condition_type"))
                .build();

        // compare_value 比较值（用于 team 名称、进度阈值等）
        context.addOption("compare_value", String.class)
                .withDefaultValue("")
                .withDisplayName(Component.translatable("node.capture_condition.option.compare_value"))
                .build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);

        // 据点信号输入
        context.addInputPort("point_target", CapturePointTypes.POINT_SIGNAL)
                .withDisplayName(Component.translatable("node.capture_condition.port.point_target"))
                .build();

        // 区域信号输入
        context.addInputPort("zone_target", CapturePointTypes.ZONE_SIGNAL)
                .withDisplayName(Component.translatable("node.capture_condition.port.zone_target"))
                .build();

        // 布尔信号输出（true / false）
        context.addOutputPort("true_out", CapturePointTypes.BOOLEAN_SIGNAL)
                .withDisplayName(Component.translatable("node.capture_condition.port.true_out"))
                .build();

        context.addOutputPort("false_out", CapturePointTypes.BOOLEAN_SIGNAL)
                .withDisplayName(Component.translatable("node.capture_condition.port.false_out"))
                .build();
    }

    // ================================================================
    //  条件类型枚举 — 可扩展，不再硬编码开关
    // ================================================================

    public enum ConditionType {
        POINT_CAPTURED("point_captured"),
        POINT_NOT_CAPTURED("point_not_captured"),
        POINT_OWNER_TEAM("point_owner_team"),
        POINT_NOT_OWNER_TEAM("point_not_owner_team"),
        POINT_CAPTURING_TEAM("point_capturing_team"),
        POINT_PROGRESS_GE("point_progress_ge"),
        ZONE_CAPTURED("zone_captured"),
        ZONE_NOT_CAPTURED("zone_not_captured"),
        ZONE_OWNER_TEAM("zone_owner_team"),
        ZONE_ACCESSIBLE("zone_accessible");

        private final String id;

        ConditionType(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public String getTranslationKey() {
            return "condition.capture_condition." + id;
        }

        public Component getDisplayName() {
            return Component.translatable(getTranslationKey());
        }

        @Override
        public String toString() {
            return id;
        }

        public static ConditionType fromId(String id) {
            if (id == null || id.isEmpty()) return POINT_CAPTURED;
            for (var mode : values()) {
                if (mode.id.equals(id)) return mode;
            }
            return POINT_CAPTURED;
        }
    }
}
