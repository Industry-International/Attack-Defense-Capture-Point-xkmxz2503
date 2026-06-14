package com.xkmxz.attack_defense_capture_point_xkmxz;

import com.xkmxz.attack_defense_capture_point_xkmxz.block.CapturePointBlock;
import com.xkmxz.attack_defense_capture_point_xkmxz.block.entity.CapturePointBlockEntity;
import com.xkmxz.attack_defense_capture_point_xkmxz.command.ModCommands;
import com.xkmxz.attack_defense_capture_point_xkmxz.gui.CapturePointManagerItem;
import com.xkmxz.attack_defense_capture_point_xkmxz.network.BlockEntityActionPayload;
import com.xkmxz.attack_defense_capture_point_xkmxz.network.CaptureDataSyncPayload;
import com.xkmxz.attack_defense_capture_point_xkmxz.render.CapturePointBlockEntityRenderer;
import com.xkmxz.attack_defense_capture_point_xkmxz.render.CapturePointWorldRenderer;
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
        private static final int SYNC_INTERVAL = 40; // 每 40 tick (~2秒) 同步一次

        @SubscribeEvent
        public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
            tickCounter++;
            if (tickCounter % SYNC_INTERVAL != 0) return;

            var server = event.getServer();
            for (var level : server.getAllLevels()) {
                if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                    CapturePointBlockEntity.syncAllBoundBlocks(sl);
                }
            }
        }
    }

    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // 注册据点方块实体渲染器（仅渲染方块模型本身）
            BlockEntityRenderers.register(CAPTURE_POINT_BE.get(), CapturePointBlockEntityRenderer::new);
            // 注册世界级据点区域渲染器（在据点实际中心坐标画范围圆环）
            NeoForge.EVENT_BUS.register(CapturePointWorldRenderer.class);
        }
    }
}
