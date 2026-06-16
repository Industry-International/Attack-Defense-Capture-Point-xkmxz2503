package com.xkmxz.attack_defense_capture_point_xkmxz.network;

import com.xkmxz.attack_defense_capture_point_xkmxz.Attack_defense_capture_point_xkmxz;
import com.xkmxz.attack_defense_capture_point_xkmxz.block.entity.CapturePointBlockEntity;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.ICaptureDataAccess;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端→服务端 方块操作请求包。
 * 所有方块实体UI的操作都通过此包发送到服务端，
 * 由服务端 CaptureManager 统一处理，实现客户端-服务端严格分离。
 *
 * <p>动作字符串常量定义：
 * <ul>
 *   <li>{@link #ACTION_CREATE_POINT_AT} — 在指定坐标创建据点</li>
 *   <li>{@link #ACTION_SET_RADIUS} — 设置据点半径</li>
 *   <li>{@link #ACTION_SET_COLOR} — 设置据点颜色</li>
 *   <li>{@link #ACTION_TOGGLE_RANGE} — 切换范围显示</li>
 *   <li>{@link #ACTION_SET_CAPTURED} — 设置据点占领状态</li>
 *   <li>{@link #ACTION_ZONE_SET_CAPTURED} — 设置区域占领状态</li>
 *   <li>{@link #ACTION_ADD_TO_ZONE} — 据点加入区域</li>
 *   <li>{@link #ACTION_REMOVE_FROM_ZONE} — 据点移出区域</li>
 *   <li>{@link #ACTION_BLOCK_UNBIND} — 方块解除据点绑定</li>
 * </ul>
 */
public record BlockEntityActionPayload(
        BlockPos blockPos,
        String action,
        String data
) implements CustomPacketPayload {

    // ---- 动作字符串常量 ----

    /** 在指定坐标创建据点并绑定 */
    public static final String ACTION_CREATE_POINT_AT = "create_point_at";
    /** 设置据点半径 */
    public static final String ACTION_SET_RADIUS = "set_radius";
    /** 设置据点颜色 */
    public static final String ACTION_SET_COLOR = "set_color";
    /** 切换范围显示 */
    public static final String ACTION_TOGGLE_RANGE = "toggle_range";
    /** 设置据点占领状态 */
    public static final String ACTION_SET_CAPTURED = "set_captured";
    /** 设置区域占领状态 */
    public static final String ACTION_ZONE_SET_CAPTURED = "zone_set_captured";
    /** 据点加入区域 */
    public static final String ACTION_ADD_TO_ZONE = "add_to_zone";
    /** 据点移出区域 */
    public static final String ACTION_REMOVE_FROM_ZONE = "remove_from_zone";
    /** 方块解除据点绑定 */
    public static final String ACTION_BLOCK_UNBIND = "block_unbind";

    public static final CustomPacketPayload.Type<BlockEntityActionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(Attack_defense_capture_point_xkmxz.MODID, "block_action"));

    public static final StreamCodec<ByteBuf, BlockEntityActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, BlockEntityActionPayload::blockPos,
                    ByteBufCodecs.STRING_UTF8, BlockEntityActionPayload::action,
                    ByteBufCodecs.STRING_UTF8, BlockEntityActionPayload::data,
                    BlockEntityActionPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 服务端处理入口 — 操作 CaptureManager → 同步 BE 缓存 → 同步到客户端 */
    public static void handleOnServer(BlockEntityActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            var level = serverPlayer.serverLevel();
            if (!(level instanceof ServerLevel serverLevel)) return;

            var access = ICaptureDataAccess.server(serverLevel);

            String[] parts = payload.data().split(",", -1);

            switch (payload.action()) {
                case "create_point" -> {
                    String name = parts[0];
                    if (!name.isEmpty() && !access.getPoints().containsKey(name)) {
                        access.addOrUpdatePoint(name, payload.blockPos());
                        // 设置服务端方块实体的绑定名（否则存盘会丢失绑定）
                        if (serverLevel.getBlockEntity(payload.blockPos()) instanceof CapturePointBlockEntity be) {
                            be.setBoundPointNameFromServer(name);
                        }
                    }
                }
                case "create_point_at" -> {
                    String name = parts[0];
                    if (parts.length >= 5 && !name.isEmpty() && !access.getPoints().containsKey(name)) {
                        try {
                            int x = Integer.parseInt(parts[1]);
                            int y = Integer.parseInt(parts[2]);
                            int z = Integer.parseInt(parts[3]);
                            double rad = Double.parseDouble(parts[4]);
                            access.addOrUpdatePointWithRadius(name, new BlockPos(x, y, z), rad);
                            // 设置服务端方块实体的绑定名（否则存盘会丢失绑定）
                            if (serverLevel.getBlockEntity(payload.blockPos()) instanceof CapturePointBlockEntity be) {
                                be.setBoundPointNameFromServer(name);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "set_radius" -> {
                    String name = parts[0];
                    if (parts.length >= 2) {
                        try {
                            double rad = Double.parseDouble(parts[1]);
                            access.setPointRadius(name, rad);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "set_color" -> {
                    String name = parts[0];
                    if (parts.length >= 2) {
                        try {
                            int color = Integer.parseInt(parts[1]);
                            access.setPointDisplayColor(name, color);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                case "set_captured" -> {
                    String name = parts[0];
                    if (parts.length >= 2) {
                        boolean captured = Boolean.parseBoolean(parts[1]);
                        access.setPointCaptured(name, captured);
                    }
                }
                case "zone_set_captured" -> {
                    String zoneName = parts[0];
                    if (parts.length >= 2) {
                        boolean captured = Boolean.parseBoolean(parts[1]);
                        access.setZoneCaptured(zoneName, captured);
                    }
                }
                case "toggle_range" -> {
                    String name = parts[0];
                    if (parts.length >= 2) {
                        boolean show = Boolean.parseBoolean(parts[1]);
                        access.setPointShowRange(name, show);
                    }
                }
                case "add_to_zone" -> {
                    if (parts.length >= 2) {
                        String zoneName = parts[0];
                        String pointName = parts[1];
                        access.addPointToZone(zoneName, pointName);
                    }
                }
                case "remove_from_zone" -> {
                    String name = parts[0];
                    String zoneName = access.findZoneForPoint(name);
                    if (zoneName != null) {
                        access.removePointFromZone(zoneName, name);
                    }
                }
                case "block_unbind" -> {
                    // 仅清除方块实体的绑定，不删除服务端据点
                    if (serverLevel.getBlockEntity(payload.blockPos()) instanceof CapturePointBlockEntity be) {
                        be.setBoundPointNameFromServer("");
                    }
                }
            }

            // 操作完成后，通知发起操作的方块实体从 CaptureManager 同步最新数据
            if (serverLevel.getBlockEntity(payload.blockPos()) instanceof CapturePointBlockEntity be) {
                be.syncFromManager();
            }

            // 广播最新据点数据到所有客户端（使世界渲染器更新）
            CaptureDataSyncPayload.broadcastToAll(serverLevel);
        });
    }
}
