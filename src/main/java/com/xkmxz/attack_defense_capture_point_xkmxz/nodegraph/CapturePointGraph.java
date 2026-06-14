package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;

import java.util.List;

/**
 * 据点管理图 - 包含据点节点和区域节点，
 * 用于可视化和编辑攻防战中的占领点与区域关系。
 * <p>
 * 重写 createGraphModel() 以返回自定义的 CapturePointGraphModel，
 * 确保节点间连线（OUTPUT → INPUT 方向）的创建和类型兼容性检查正确。
 */
public class CapturePointGraph extends Graph {

    private static final List<Class<? extends Node>> SUPPORT_NODES = List.of(
            CapturePointNode.class,
            CaptureZoneNode.class
    );

    private static final List<TypeHandle> SUPPORT_TYPES = List.of(
            CapturePointTypes.POINT_SIGNAL,
            CapturePointTypes.ZONE_SIGNAL
    );

    @Override
    public List<Class<? extends Node>> getSupportNodes() {
        return SUPPORT_NODES;
    }

    @Override
    public List<TypeHandle> getSupportTypes() {
        return SUPPORT_TYPES;
    }

    /**
     * 创建自定义图模型实例。
     * 覆盖此方法以使 Graph 构造函数使用我们的 CapturePointGraphModel，
     * 其 createWire/canAssignTo 覆盖了父类中端口方向交换和类型兼容性的默认行为。
     */
    @Override
    protected CustomGraphModelImpl createGraphModel() {
        return new CapturePointGraphModel(this);
    }
}
