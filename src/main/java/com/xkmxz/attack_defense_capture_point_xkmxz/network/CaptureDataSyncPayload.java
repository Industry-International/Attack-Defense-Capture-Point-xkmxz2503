package com.xkmxz.attack_defense_capture_point_xkmxz.network;

import com.xkmxz.attack_defense_capture_point_xkmxz.Attack_defense_capture_point_xkmxz;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.CaptureManager;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.ICaptureDataAccess;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端→客户端 据点数据同步包。
 * 客户端无法直接访问服务端的 CaptureManager，
 * 通过此包将据点渲染数据（坐标、半径、颜色、是否显示）和区域名称列表推送到客户端缓存。
 */
public record CaptureDataSyncPayload(Map<String, PointRenderData> points, List<String> zoneNames) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CaptureDataSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(Attack_defense_capture_point_xkmxz.MODID, "capture_data_sync"));

    public static final StreamCodec<ByteBuf, CaptureDataSyncPayload> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(
                    CaptureDataSyncPayload::fromTag,
                    CaptureDataSyncPayload::toTag
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ---- 版本去重（避免无变更时重复广播） ----

    private static final Map<ResourceKey<Level>, Long> LAST_BROADCAST_VERSION = new HashMap<>();

    // ---- 服务端发送入口 ----

    /**
     * 将 CaptureManager 中所有据点的渲染数据广播给所有在线玩家。
     * 内部自带版本去重——如果 CaptureManager 版本号未变，不重复发送。
     * 在数据变更后调用（GUI 保存、命令修改、方块操作等）。
     */
    public static void broadcastToAll(ServerLevel level) {
        var access = ICaptureDataAccess.server(level);
        long currentVersion = access.getVersion();

        // 版本去重：数据没变就不发
        var dimKey = level.dimension();
        Long lastVersion = LAST_BROADCAST_VERSION.get(dimKey);
        if (lastVersion != null && lastVersion == currentVersion) return;
        LAST_BROADCAST_VERSION.put(dimKey, currentVersion);

        var pointMap = new HashMap<String, PointRenderData>();
        var zoneNameList = new ArrayList<String>();
        for (var entry : access.getPoints().values()) {
            pointMap.put(entry.name(), new PointRenderData(
                    entry.pos(), entry.radius(), entry.displayColor(), entry.showRange(), entry.captured(),
                    entry.ownerTeam(), entry.capturingTeam(), entry.captureProgress()));
        }
        for (var zoneEntry : access.getZones().values()) {
            zoneNameList.add(zoneEntry.name());
        }
        var payload = new CaptureDataSyncPayload(pointMap, zoneNameList);
        for (var player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * 向指定玩家发送完整据点数据（用于玩家加入世界时初始化）。
     */
    public static void sendToPlayer(ServerLevel level, ServerPlayer player) {
        var access = ICaptureDataAccess.server(level);
        var pointMap = new HashMap<String, PointRenderData>();
        var zoneNameList = new ArrayList<String>();
        for (var entry : access.getPoints().values()) {
            pointMap.put(entry.name(), new PointRenderData(
                    entry.pos(), entry.radius(), entry.displayColor(), entry.showRange(), entry.captured(),
                    entry.ownerTeam(), entry.capturingTeam(), entry.captureProgress()));
        }
        for (var zoneEntry : access.getZones().values()) {
            zoneNameList.add(zoneEntry.name());
        }
        PacketDistributor.sendToPlayer(player, new CaptureDataSyncPayload(pointMap, zoneNameList));
    }

    // ---- NBT 序列化 ----

    private static final String TAG_POINTS = "points";
    private static final String TAG_NAME = "name";
    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";
    private static final String TAG_RADIUS = "radius";
    private static final String TAG_COLOR = "color";
    private static final String TAG_SHOW = "show";
    private static final String TAG_CAPTURED = "captured";
    private static final String TAG_OWNER_TEAM = "ownerTeam";
    private static final String TAG_CAPTURING_TEAM = "capturingTeam";
    private static final String TAG_CAPTURE_PROGRESS = "captureProgress";
    private static final String TAG_ZONES = "zones";

    private static CaptureDataSyncPayload fromTag(CompoundTag tag) {
        var points = new HashMap<String, PointRenderData>();
        var list = tag.getList(TAG_POINTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i);
            var name = entry.getString(TAG_NAME);
            var pos = new BlockPos(entry.getInt(TAG_X), entry.getInt(TAG_Y), entry.getInt(TAG_Z));
            points.put(name, new PointRenderData(
                    pos,
                    entry.getDouble(TAG_RADIUS),
                    entry.getInt(TAG_COLOR),
                    entry.getBoolean(TAG_SHOW),
                    entry.contains(TAG_CAPTURED) && entry.getBoolean(TAG_CAPTURED),
                    entry.contains(TAG_OWNER_TEAM) ? entry.getString(TAG_OWNER_TEAM) : null,
                    entry.contains(TAG_CAPTURING_TEAM) ? entry.getString(TAG_CAPTURING_TEAM) : null,
                    entry.contains(TAG_CAPTURE_PROGRESS) ? entry.getInt(TAG_CAPTURE_PROGRESS) : 0
            ));
        }
        var zoneNameList = new ArrayList<String>();
        var zoneList = tag.getList(TAG_ZONES, Tag.TAG_STRING);
        for (int i = 0; i < zoneList.size(); i++) {
            zoneNameList.add(zoneList.getString(i));
        }
        return new CaptureDataSyncPayload(points, zoneNameList);
    }

    private static CompoundTag toTag(CaptureDataSyncPayload payload) {
        var tag = new CompoundTag();
        var list = new ListTag();
        for (var entry : payload.points().entrySet()) {
            var e = new CompoundTag();
            e.putString(TAG_NAME, entry.getKey());
            e.putInt(TAG_X, entry.getValue().pos().getX());
            e.putInt(TAG_Y, entry.getValue().pos().getY());
            e.putInt(TAG_Z, entry.getValue().pos().getZ());
            e.putDouble(TAG_RADIUS, entry.getValue().radius());
            e.putInt(TAG_COLOR, entry.getValue().displayColor());
            e.putBoolean(TAG_SHOW, entry.getValue().showRange());
            e.putBoolean(TAG_CAPTURED, entry.getValue().captured());
            if (entry.getValue().ownerTeam() != null) e.putString(TAG_OWNER_TEAM, entry.getValue().ownerTeam());
            if (entry.getValue().capturingTeam() != null) e.putString(TAG_CAPTURING_TEAM, entry.getValue().capturingTeam());
            e.putInt(TAG_CAPTURE_PROGRESS, entry.getValue().captureProgress());
            list.add(e);
        }
        tag.put(TAG_POINTS, list);
        // 序列化区域名称列表
        var zoneList = new ListTag();
        for (var zoneName : payload.zoneNames()) {
            zoneList.add(net.minecraft.nbt.StringTag.valueOf(zoneName));
        }
        tag.put(TAG_ZONES, zoneList);
        return tag;
    }

    // ---- 客户端处理 ----

    /** 客户端接收包后，将数据写入客户端缓存 */
    public static void handleOnClient(CaptureDataSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.xkmxz.attack_defense_capture_point_xkmxz.render.ClientCaptureDataCache.INSTANCE.updateFrom(payload);
        });
    }

    // ---- 数据记录 ----

    public record PointRenderData(BlockPos pos, double radius, int displayColor, boolean showRange, boolean captured,
                                  @org.jetbrains.annotations.Nullable String ownerTeam,
                                  @org.jetbrains.annotations.Nullable String capturingTeam,
                                  int captureProgress) {}
}
