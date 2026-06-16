package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import net.minecraft.network.chat.Component;

/**
 * 条件节点 — 评估输入（据点/区域/布尔信号）是否满足指定的条件。<br>
 * <br>
 * <b>新版设计（v2.0）：</b>
 * <ul>
 *   <li><b>property</b> — 要检查的属性（如 captured, owner_team, progress）</li>
 *   <li><b>operator</b> — 比较运算符（==, !=, >=, >, <=, <）</li>
 *   <li><b>compare_value</b> — 要比较的值（如 true, 队伍名, 50 等）</li>
 * </ul>
 * 评估逻辑：<code>输入值 [operator] 比较值</code> → true/false → true_out / false_out<br>
 * <br>
 * <b>条件嵌套：</b>新增 bool_in 端口（BOOLEAN_SIGNAL），可接收其他条件节点或逻辑门的输出，
 * 实现条件链式组合，无需经过逻辑门节点。
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

        // property 属性类型 — 要检查的输入属性
        context.addOption("property", PropertyType.class)
                .withDefaultValue(PropertyType.CAPTURED)
                .withDisplayName(Component.translatable("node.capture_condition.option.property"))
                .build();

        // operator 比较运算符 — 如何比较
        context.addOption("operator", OperatorType.class)
                .withDefaultValue(OperatorType.EQUALS)
                .withDisplayName(Component.translatable("node.capture_condition.option.operator"))
                .build();

        // compare_value 比较值 — 要比较的目标值
        context.addOption("compare_value", String.class)
                .withDefaultValue("true")
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

        // 布尔信号输入（条件嵌套：接收其他条件的输出）
        context.addInputPort("bool_in", CapturePointTypes.BOOLEAN_SIGNAL)
                .withDisplayName(Component.translatable("node.capture_condition.port.bool_in"))
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
    //  属性类型枚举 — 要检查的输入属性
    // ================================================================

    public enum PropertyType {
        CAPTURED("captured"),
        OWNER_TEAM("owner_team"),
        CAPTURING_TEAM("capturing_team"),
        PROGRESS("progress"),
        ZONE_CAPTURED("zone_captured"),
        ZONE_OWNER_TEAM("zone_owner_team"),
        ZONE_ACCESSIBLE("zone_accessible");

        private final String id;

        PropertyType(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public String getTranslationKey() {
            return "property.capture_condition." + id;
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

        public static PropertyType fromId(String id) {
            if (id == null || id.isEmpty()) return CAPTURED;
            for (var p : values()) {
                if (p.id.equals(id)) return p;
            }
            return CAPTURED;
        }
    }

    // ================================================================
    //  运算符类型枚举 — 比较方式
    // ================================================================

    public enum OperatorType {
        EQUALS("=="),
        NOT_EQUALS("!="),
        GREATER_OR_EQUAL(">="),
        GREATER(">"),
        LESS_OR_EQUAL("<="),
        LESS("<");

        private final String symbol;

        OperatorType(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }

        public String getTranslationKey() {
            return "operator.capture_condition." + switch (symbol) {
                case "==" -> "equals";
                case "!=" -> "not_equals";
                case ">=" -> "greater_or_equal";
                case ">" -> "greater";
                case "<=" -> "less_or_equal";
                case "<" -> "less";
                default -> "equals";
            };
        }

        public Component getDisplayName() {
            return Component.translatable(getTranslationKey());
        }

        @Override
        public String toString() {
            return getDisplayName().getString();
        }

        public String getSerializationId() {
            return symbol;
        }

        /**
         * 对两个值执行比较运算。
         * @param actual 实际值（字符串形式）
         * @param expected 比较目标值
         * @return 比较结果
         */
        public boolean evaluate(String actual, String expected) {
            if (actual == null || expected == null) return false;

            return switch (this) {
                case EQUALS -> actual.equals(expected);
                case NOT_EQUALS -> !actual.equals(expected);

                // 数值比较
                case GREATER_OR_EQUAL, GREATER, LESS_OR_EQUAL, LESS -> {
                    try {
                        double a = Double.parseDouble(actual);
                        double e = Double.parseDouble(expected);
                        yield switch (this) {
                            case GREATER_OR_EQUAL -> a >= e;
                            case GREATER -> a > e;
                            case LESS_OR_EQUAL -> a <= e;
                            case LESS -> a < e;
                            default -> false;
                        };
                    } catch (NumberFormatException ex) {
                        yield false;
                    }
                }
            };
        }

        public static OperatorType fromId(String id) {
            if (id == null || id.isEmpty()) return EQUALS;
            for (var o : values()) {
                if (o.symbol.equals(id)) return o;
            }
            return EQUALS;
        }
    }
}
