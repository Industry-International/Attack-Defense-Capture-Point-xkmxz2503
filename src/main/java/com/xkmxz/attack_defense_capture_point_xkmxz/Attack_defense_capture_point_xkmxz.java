package com.xkmxz.attack_defense_capture_point_xkmxz;

import com.xkmxz.attack_defense_capture_point_xkmxz.block.CapturePointBlock;
import com.xkmxz.attack_defense_capture_point_xkmxz.block.entity.CapturePointBlockEntity;
import com.xkmxz.attack_defense_capture_point_xkmxz.command.ModCommands;
import com.xkmxz.attack_defense_capture_point_xkmxz.gui.CapturePointManagerItem;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.CaptureManager;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.ICaptureDataAccess;
import com.xkmxz.attack_defense_capture_point_xkmxz.network.BlockEntityActionPayload;
import com.xkmxz.attack_defense_capture_point_xkmxz.network.CaptureDataSyncPayload;
import com.xkmxz.attack_defense_capture_point_xkmxz.render.CapturePointBlockEntityRenderer;
import com.xkmxz.attack_defense_capture_point_xkmxz.render.CapturePointWorldRenderer;
import com.xkmxz.attack_defense_capture_point_xkmxz.render.CaptureProgressOverlay;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Attack_defense_capture_point_xkmxz.MODID)
public class Attack_defense_capture_point_xkmxz {

    public static final String MODID = "attack_defense_capture_point_xkmxz";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredBlock<CapturePointBlock> CAPTURE_POINT_BLOCK = BLOCKS.register("capture_point_block",
            () -> new CapturePointBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredItem<BlockItem> CAPTURE_POINT_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("capture_point_block", CAPTURE_POINT_BLOCK);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CapturePointBlockEntity>> CAPTURE_POINT_BE = BLOCK_ENTITIES.register("capture_point_block",
            () -> {
                var type = BlockEntityType.Builder.of(CapturePointBlockEntity::new, CAPTURE_POINT_BLOCK.get()).build(null);
                CapturePointBlockEntity.TYPE = type;
                return type;
            });

    public static final DeferredItem<Item> CAPTURE_POINT_MANAGER_ITEM = ITEMS.register("capture_point_manager",
            () -> new CapturePointManagerItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_MODE_TABS.register("tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> CAPTURE_POINT_MANAGER_ITEM.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(CAPTURE_POINT_BLOCK_ITEM.get());
                        output.accept(CAPTURE_POINT_MANAGER_ITEM.get());
                    })
                    .build());

    public Attack_defense_capture_point_xkmxz(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloadHandlers);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::addCreative);

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> ModCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(ServerTickHandler::onServerTick);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    /** 注册网络包处理器 — 客户端→服务端 + 服务端→客户端严格分离 */
    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(MODID).versioned("1.0");
        registrar.playToServer(
                BlockEntityActionPayload.TYPE,
                BlockEntityActionPayload.STREAM_CODEC,
                BlockEntityActionPayload::handleOnServer
        );
        registrar.playToClient(
                CaptureDataSyncPayload.TYPE,
                CaptureDataSyncPayload.STREAM_CODEC,
                CaptureDataSyncPayload::handleOnClient
        );
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(CAPTURE_POINT_BLOCK_ITEM);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    /** 玩家加入世界时，发送据点数据同步包到客户端 */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            var level = serverPlayer.serverLevel();
            CaptureDataSyncPayload.sendToPlayer(level, serverPlayer);
        }
    }

    // ================================================================
    //  服务端 Tick 事件 — 周期性同步方块数据（安全网）
    // ================================================================

    public static class ServerTickHandler {
        private static int tickCounter = 0;
        private static final int SYNC_INTERVAL = 40;

        @SubscribeEvent
        public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
            tickCounter++;
            var server = event.getServer();

            // 每 10 tick (~0.5秒) 处理占领逻辑
            if (tickCounter % 10 == 0) {
                for (var level : server.getAllLevels()) {
                    if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                        processCaptureLogic(sl);
                    }
                }
            }

            // 每 40 tick (~2秒) 同步客户端缓存
            if (tickCounter % SYNC_INTERVAL == 0) {
                for (var level : server.getAllLevels()) {
                    if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                        CapturePointBlockEntity.syncAllBoundBlocks(sl);
                    }
                }
            }
        }

        private static void processCaptureLogic(net.minecraft.server.level.ServerLevel level) {
            var access = ICaptureDataAccess.server(level);
            String defender = access.getDefenderTeam();
            for (var entry : access.getPoints().values()) {
                if (!entry.showRange()) continue;
                var pos = entry.pos();
                double radiusSq = entry.radius() * entry.radius();

                var nearbyPlayers = level.players().stream()
                        .filter(p -> p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= radiusSq)
                        .toList();

                if (nearbyPlayers.isEmpty()) {
                    if (entry.capturingTeam() != null) {
                        access.setPointCapturingTeam(entry.name(), null);
                    }
                    continue;
                }

                // 统计各队伍人数（防守方不计入）
                var teamCount = new java.util.HashMap<String, Integer>();
                for (var p : nearbyPlayers) {
                    String t = p.getTeam() != null ? p.getTeam().getName() : "__no_team__";
                    if (defender != null && defender.equals(t)) continue;
                    teamCount.merge(t, 1, Integer::sum);
                }

                // 排除无队伍的玩家
                teamCount.remove("__no_team__");
                if (teamCount.isEmpty()) continue;

                // 取人数最多的队伍
                var maxEntry = teamCount.entrySet().stream()
                        .max(java.util.Comparator.comparingInt(java.util.Map.Entry::getValue))
                        .get();
                String dominantTeam = maxEntry.getKey();

                // 防守方模式下检查区域可达性（逐层占领）
                if (defender != null) {
                    String zoneName = access.findZoneForPoint(entry.name());
                    if (zoneName != null && !access.canAccessZone(zoneName)) {
                        if (entry.capturingTeam() != null) {
                            access.setPointCapturingTeam(entry.name(), null);
                        }
                        continue;
                    }
                }

                // 如果已被此队伍占领，保持满进度
                if (java.util.Objects.equals(entry.ownerTeam(), dominantTeam)) {
                    if (entry.captureProgress() < CaptureManager.CapturePointEntry.MAX_PROGRESS) {
                        access.setPointCaptureProgress(entry.name(), CaptureManager.CapturePointEntry.MAX_PROGRESS);
                    }
                    continue;
                }

                // 如果据点被其他队伍占领 → 先清除（进度从100减少到0）
                if (entry.ownerTeam() != null) {
                    int newProgress = entry.captureProgress() - 2;
                    if (entry.capturingTeam() == null || !entry.capturingTeam().equals(dominantTeam)) {
                        access.setPointCapturingTeam(entry.name(), dominantTeam);
                    }
                    if (newProgress <= 0) {
                        // 清除完毕 → 变为中立
                        access.setPointOwnerTeam(entry.name(), null);
                        access.setPointCaptureProgress(entry.name(), 0);
                    } else {
                        access.setPointCaptureProgress(entry.name(), newProgress);
                    }
                    continue;
                }

                // 中立据点 → 推进占领进度（从0到100）
                int newProgress = entry.captureProgress() + 2;
                if (entry.capturingTeam() == null || !entry.capturingTeam().equals(dominantTeam)) {
                    access.setPointCapturingTeam(entry.name(), dominantTeam);
                }
                access.setPointCaptureProgress(entry.name(), newProgress);
            }
        }
    }

    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            BlockEntityRenderers.register(CAPTURE_POINT_BE.get(), CapturePointBlockEntityRenderer::new);
            NeoForge.EVENT_BUS.register(CapturePointWorldRenderer.class);
            NeoForge.EVENT_BUS.register(CaptureProgressOverlay.class);
        }
    }
}
