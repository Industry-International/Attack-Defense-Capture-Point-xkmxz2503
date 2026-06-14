package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortDirection;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.wire.WireModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import org.joml.Vector2f;

import java.util.UUID;

/**
 * 据点管理图模型 - LDLib2 nodegraphtookit 自定义图模型。
 * <p>
 * 参考 synaxis 的 CircuitLdGraphModel 实现，覆盖 createWire 和 canAssignTo
 * 以正确处理据点节点和区域节点间的端口连接（OUTPUT → INPUT 方向交换及类型兼容性）。
 */
public class CapturePointGraphModel extends CustomGraphModelImpl {

    /**
     * 信号类型标识前缀：用于 canAssignTo 中判断端口是否为"信号"类型连接。
     * 据点/区域的 TypeHandle 使用 "capture:" 前缀，据此区分信号连接与普通数据连接。
     */
    private static final String SIGNAL_TYPE_PREFIX = "capture:";

    public CapturePointGraphModel(Graph graph) {
        super(graph);
    }

    // ================================================================
    //  端口方向自动修正 + 兼容性检查（参考 synaxis CircuitLdGraphModel）
    //
    //  synaxis 的做法：
    //  1. 检测到 from=OUTPUT, to=INPUT 时，交换端口方向（from→INPUT, to→OUTPUT）
    //  2. 用交换后的端口调用 isCompatiblePort(to, from) 做兼容性检查
    //  3. 兼容则调用 super.createWire(wireClass, originalFrom, originalTo, snapToGrid, uuid)
    //     保留原始的 OUTPUT→INPUT 顺序传递给父类
    // ================================================================

    @Override
    public WireModel createWire(Class<? extends WireModel> wireClass,
                                 PortModel fromPort,
                                 PortModel toPort,
                                 boolean snapToGrid,
                                 UUID uuid) {
        // ---- 端口方向修正（与 synaxis 一致） ----
        PortModel actualFrom = fromPort;
        PortModel actualTo = toPort;

        // 如果 from=OUTPUT 且 to=INPUT，说明方向正确，不需要交换
        // 但 base GraphModel 的 createWire 会自动交换 OUTPUT→INPUT → INPUT→OUTPUT，
        // 导致 instantiateWire 收到的端口方向不对。
        // 因此我们保持原始方向，让父类正确接收到 OUTPUT→INPUT。
        if (fromPort != null && toPort != null
                && fromPort.getDirection() == PortDirection.OUTPUT
                && toPort.getDirection() == PortDirection.INPUT) {
            // 方向正确，保留原始顺序
            actualFrom = fromPort;
            actualTo = toPort;
        } else if (fromPort != null && toPort != null
                && fromPort.getDirection() == PortDirection.INPUT
                && toPort.getDirection() == PortDirection.OUTPUT) {
            // 方向反了：INPUT→OUTPUT → 交换为 OUTPUT→INPUT
            actualFrom = toPort;
            actualTo = fromPort;
        }

        // ---- 兼容性检查（参考 synaxis 先在交换后的端口上检查） ----
        if (actualTo == null || actualFrom == null) {
            throw new IllegalArgumentException(
                    "Cannot create wire: ports are null. from=" + portDescription(fromPort)
                            + ", to=" + portDescription(toPort));
        }

        // 用修正后的方向检查兼容性（注意：isCompatiblePort 内部会调用 canAssignTo）
        if (!isCompatiblePort(actualTo, actualFrom)) {
            throw new IllegalArgumentException(
                    "Incompatible ports: cannot connect "
                            + portDescription(actualTo) + " to " + portDescription(actualFrom));
        }

        // ---- 调用父类创建连线（传入原始端口，让父类自己处理交换逻辑） ----
        // 这里传 original fromPort/toPort 而非修正后的端口，
        // 因为父类的 createWire 对 OUTPUT→INPUT 会再次交换并最终通过 setPorts 反转得到正确方向
        return super.createWire(wireClass, fromPort, toPort, snapToGrid, uuid);
    }

    // ================================================================
    //  端口兼容性判断（参考 synaxis CircuitLdGraphModel.canAssignTo）
    //
    //  synaxis 的逻辑：
    //  - 如果两个端口中任意一个是"信号"类型，则只有当类型完全相同时才允许连接
    //  - 否则使用父类的默认 canAssignTo 逻辑
    // ================================================================

    @Override
    public boolean canAssignTo(PortModel from, PortModel to) {
        if (from == null || to == null) {
            return false;
        }

        TypeHandle fromType = from.getDataTypeHandle();
        TypeHandle toType = to.getDataTypeHandle();

        // 如果任一端口是信号类型，则要求类型完全相等
        if (isSignalType(fromType) || isSignalType(toType)) {
            return fromType != null && fromType.equals(toType);
        }

        // 非信号类型：使用父类默认兼容性逻辑
        return super.canAssignTo(from, to);
    }

    // ================================================================
    //  工具方法
    // ================================================================

    /**
     * 判断 TypeHandle 是否为信号类型（以 "capture:" 前缀开头）。
     */
    private static boolean isSignalType(TypeHandle type) {
        if (type == null) return false;
        String id = type.getIdentification();
        return id != null && id.startsWith(SIGNAL_TYPE_PREFIX);
    }

    /**
     * 生成端口的可读描述信息（用于错误消息）。
     */
    private static String portDescription(PortModel port) {
        if (port == null) return "<null>";
        return port.getUniqueName() + "(" + port.getDataTypeHandle().getFriendlyName() + ")";
    }
}
