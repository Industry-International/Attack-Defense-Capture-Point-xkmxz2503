package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;

/**
 * 自定义信号类型，用于据点节点图中的连接。
 * POINT_SIGNAL: 据点→区域的成员关系连接
 * ZONE_SIGNAL: 区域→区域的依赖关系连接（zone_out → required_zone）
 * UNLOCK_SIGNAL: 区域→区域的解锁状态连接（unlock_out → unlock_in），与 zone_signal 独立
 */
public final class CapturePointTypes {

    /** 据点信号 - 用于连接据点到区域（表示该据点属于某个区域） */
    public static final TypeHandle POINT_SIGNAL = TypeHandle.create("capture:point_signal");

    /** 区域信号 - 用于区域之间的依赖连接（表示一个区域依赖另一个区域） */
    public static final TypeHandle ZONE_SIGNAL = TypeHandle.create("capture:zone_signal");

    /** 解锁信号 - 用于区域之间的解锁状态传递（独立于区域依赖接口） */
    public static final TypeHandle UNLOCK_SIGNAL = TypeHandle.create("capture:unlock_signal");

    /** 布尔信号 - 条件节点/逻辑门节点之间传递布尔判断结果 */
    public static final TypeHandle BOOLEAN_SIGNAL = TypeHandle.create("capture:boolean_signal");

    private CapturePointTypes() {}
}
