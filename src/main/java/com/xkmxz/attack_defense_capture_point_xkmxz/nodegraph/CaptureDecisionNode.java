package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import net.minecraft.network.chat.Component;

/**
 * 判断器节点 — 根据条件将据点信号分配到不同输出路径。<br>
 * 接收一个据点信号（POINT_SIGNAL），根据配置的条件判断，
 * 将据点信号输出到 {@code true_out}（条件满足）或 {@code false_out}（条件不满足）端口。<br>
 * <br>
 * <b>条件类型 (condition)：</b>
 * <ul>
 *   <li>{@code captured} — 据点的 captured 为 true</li>
 *   <li>{@code not_captured} — 据点的 captured 为 false</li>
 *   <li>{@code owner_team} — 据点的 ownerTeam 匹配 target_team</li>
 *   <li>{@code capturing} — 据点的 capturingTeam 匹配 target_team</li>
 *   <li>{@code not_capturing} — 据点的 capturingTeam 为 null 或不匹配 target_team</li>
 * </ul>
 */
public class CaptureDecisionNode extends Node {

    public CaptureDecisionNode() {
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("node.capture_decision.display_name");
    }

    @Override
    public IGuiTexture getNodeIcon() {
        return new ColorRectTexture(0xFFFF9800); // 橙色图标
    }

    @Override
    public void onDefineOptions(IOptionDefinitionContext context) {
        super.onDefineOptions(context);

        // condition 判断条件类型
        context.addOption("condition", String.class)
                .withDefaultValue("captured")
                .withDisplayName(Component.translatable("node.capture_decision.option.condition"))
                .build();

        // target_team 目标队伍（用于 owner_team / capturing 判断）
        context.addOption("target_team", String.class)
                .withDefaultValue("")
                .withDisplayName(Component.translatable("node.capture_decision.option.target_team"))
                .build();

        // progress_threshold 进度阈值（预留，用于 future 进度比较）
        context.addOption("progress_threshold", Integer.class)
                .withDefaultValue(50)
                .withDisplayName(Component.translatable("node.capture_decision.option.progress_threshold"))
                .build();
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        super.onDefinePorts(context);

        // 输入端口 - 接收据点信号
        context.addInputPort("target", CapturePointTypes.POINT_SIGNAL)
                .withDisplayName(Component.translatable("node.capture_decision.port.target"))
                .build();

        // 输出端口 - 条件满足时据点信号从此输出
        context.addOutputPort("true_out", CapturePointTypes.POINT_SIGNAL)
                .withDisplayName(Component.translatable("node.capture_decision.port.true_out"))
                .build();

        // 输出端口 - 条件不满足时据点信号从此输出
        context.addOutputPort("false_out", CapturePointTypes.POINT_SIGNAL)
                .withDisplayName(Component.translatable("node.capture_decision.port.false_out"))
                .build();
    }
}
