package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * 通知气泡 - 从右侧滑入的 QQ 聊天气泡风格提示。
 * 支持成功(绿色)、错误(红色)、信息(蓝色)三种类型。
 */
public class ToastNotification {

    /** 通知类型 */
    public enum Type {
        SUCCESS(0xFF2E7D32, 0xFF66BB6A),
        ERROR(0xFFC62828, 0xFFEF5350),
        INFO(0xFF1565C0, 0xFF42A5F5);

        final int bgColor;
        final int borderColor;

        Type(int bgColor, int borderColor) {
            this.bgColor = bgColor;
            this.borderColor = borderColor;
        }
    }

    private static final Queue<PendingToast> PENDING_QUEUE = new ArrayDeque<>();
    private static final int TOAST_SHOW_TICKS = 80; // 约4秒 (20 ticks/秒)
    private static final int TOAST_WIDTH = 260;
    private static final int TOAST_HEIGHT = 36;

    /**
     * 添加一个待显示的通知。
     */
    public static void push(Type type, String message) {
        push(type, Component.literal(message));
    }

    public static void push(Type type, Component message) {
        synchronized (PENDING_QUEUE) {
            if (PENDING_QUEUE.size() < 5) { // 最多缓存5条
                PENDING_QUEUE.add(new PendingToast(type, message));
            }
        }
    }

    /**
     * 获取所有待处理通知并清空队列。
     */
    static Queue<PendingToast> drainPending() {
        var queue = new ArrayDeque<PendingToast>();
        synchronized (PENDING_QUEUE) {
            queue.addAll(PENDING_QUEUE);
            PENDING_QUEUE.clear();
        }
        return queue;
    }

    /**
     * 创建通知气泡的 UIElement 并添加到指定容器。
     * @param parent 父容器
     * @param type 通知类型
     * @param message 消息文本
     * @return 创建的气泡元素
     */
    static UIElement createBubble(UIElement parent, Type type, Component message) {
        var bubble = new UIElement()
                .layout(l -> {
                    l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE);
                    l.right(-TOAST_WIDTH - 20); // 初始在屏幕外（右侧）
                    l.top(10);
                    l.width(TOAST_WIDTH).height(TOAST_HEIGHT);
                    l.paddingAll(6);
                })
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(type.bgColor)));

        // 左边色条
        var colorBar = new UIElement()
                .layout(l -> {
                    l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE);
                    l.left(0).top(2).bottom(2).width(4);
                })
                .style(s -> s.backgroundTexture(new ColorRectTexture(type.borderColor)));

        // 消息文本
        var label = new Label().setText(message);
        label.layout(l -> {
            l.widthPercent(100).heightPercent(100);
            l.paddingLeft(8);
        });
        label.textStyle(s -> {
            s.fontSize(12.0f);
            s.textColor(0xFFFFFFFF);
            s.textShadow(true);
        });

        bubble.addChildren(colorBar, label);
        parent.addChildren(bubble);

        return bubble;
    }

    /**
     * 待显示的通知数据。
     */
    record PendingToast(Type type, Component message) {}
}
