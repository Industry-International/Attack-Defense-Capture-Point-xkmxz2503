package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

/**
 * 通知层 - 管理所有活跃通知的动画生命周期（滑入、停留、滑出）。
 * 需要由 GraphView 的 screenTick() 驱动。
 * 大小跟随 GUI 缩放自动适配，滑出方向为向右。
 */
public class ToastLayer {

    private static final int SLIDE_IN_TICKS = 10;  // 滑入动画帧数
    private static final int SHOW_TICKS = 60;      // 停留帧数（约3秒）
    private static final int SLIDE_OUT_TICKS = 10; // 滑出动画帧数
    private static final int TOTAL_TICKS = SLIDE_IN_TICKS + SHOW_TICKS + SLIDE_OUT_TICKS;
    private static final int TOAST_GAP = 6;        // 气泡间距

    private final UIElement parent;
    private final List<ActiveBubble> activeBubbles = new ArrayList<>();

    public ToastLayer(UIElement parent) {
        this.parent = parent;
    }

    /** 获取当前动态的气泡宽度（委托给 ToastNotification） */
    private int tw() { return ToastNotification.getDynamicWidth(); }
    /** 获取当前动态的气泡高度 */
    private int th() { return ToastNotification.getDynamicHeight(); }

    /**
     * 每帧调用，处理待显示通知和动画更新。
     */
    public void tick() {
        int toastW = tw();
        int toastH = th();

        // 从队列中获取新通知
        Queue<ToastNotification.PendingToast> pending = ToastNotification.drainPending();
        if (!pending.isEmpty()) {
            int baseOffset = computeTotalHeight();
            for (var pt : pending) {
                var bubble = ToastNotification.createBubble(parent, pt.type(), pt.message());
                activeBubbles.add(new ActiveBubble(bubble, 0, baseOffset));
                baseOffset += toastH + TOAST_GAP;
            }
        }

        // 更新所有活跃气泡的动画
        var it = activeBubbles.iterator();
        while (it.hasNext()) {
            var ab = it.next();
            ab.tick++;

            if (ab.tick < SLIDE_IN_TICKS) {
                // 滑入阶段: right 从 -(toastW+20) 到 10（从右侧滑入）
                float progress = (float) ab.tick / SLIDE_IN_TICKS;
                float right = -(toastW + 20) * (1 - progress) + 10 * progress;
                ab.element.layout(l -> l.right(right));
            } else if (ab.tick < SLIDE_IN_TICKS + SHOW_TICKS) {
                // 停留阶段: right = 10
                ab.element.layout(l -> l.right(10f));
                // 更新垂直偏移（适应新的通知）
                float top = 8 + ab.baseOffset;
                ab.element.layout(l -> l.top(top));
            } else if (ab.tick < TOTAL_TICKS) {
                // 滑出阶段: right 从 10 回到 -(toastW+20)（向右滑出，不与右上角按钮重叠）
                float progress = (float) (ab.tick - SLIDE_IN_TICKS - SHOW_TICKS) / SLIDE_OUT_TICKS;
                float right = 10 + (-(toastW + 20) - 10) * progress;
                ab.element.layout(l -> l.right(right));
            } else {
                // 结束: 从父容器移除
                parent.removeChild(ab.element);
                it.remove();
            }
        }
    }

    /**
     * 计算当前所有通知占用的总高度。
     */
    private int computeTotalHeight() {
        int total = 0;
        for (var ab : activeBubbles) {
            total += th() + TOAST_GAP;
        }
        return total;
    }

    /**
     * 活跃气泡数据。
     */
    private static class ActiveBubble {
        final UIElement element;
        int tick;
        final int baseOffset;

        ActiveBubble(UIElement element, int tick, int baseOffset) {
            this.element = element;
            this.tick = tick;
            this.baseOffset = baseOffset;
        }
    }
}
