package com.xkmxz.attack_defense_capture_point_xkmxz.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.ICaptureDataAccess;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("capturepoint")
                .requires(source -> source.hasPermission(2))

                // Help
                .then(Commands.literal("help")
                        .executes(ModCommands::showHelp))
                .executes(ModCommands::showHelp)

                // Open GUI (only usable by players)
                .then(Commands.literal("gui")
                        .executes(ModCommands::openGui))

                // Point commands
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ModCommands::createPoint)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(ModCommands::suggestPoints)
                                .executes(ModCommands::removePoint)))
                .then(Commands.literal("setowner")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(ModCommands::suggestPoints)
                                .then(Commands.argument("owner", StringArgumentType.string())
                                        .executes(ModCommands::setOwner))))
                .then(Commands.literal("clearowner")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(ModCommands::suggestPoints)
                                .executes(ctx -> setOwner(ctx, null))))
                .then(Commands.literal("list")
                        .executes(ModCommands::listAll))

                // Zone commands
                .then(Commands.literal("zone")
                        .then(Commands.literal("create")
                                .then(Commands.argument("zoneName", StringArgumentType.word())
                                        .executes(ctx -> createZone(ctx, null))
                                        .then(Commands.argument("requiredZone", StringArgumentType.word())
                                                .suggests(ModCommands::suggestZones)
                                                .executes(ctx -> createZone(ctx, StringArgumentType.getString(ctx, "requiredZone"))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("zoneName", StringArgumentType.word())
                                        .suggests(ModCommands::suggestZones)
                                        .executes(ModCommands::removeZone)))
                        .then(Commands.literal("addpoint")
                                .then(Commands.argument("zoneName", StringArgumentType.word())
                                        .suggests(ModCommands::suggestZones)
                                        .then(Commands.argument("pointName", StringArgumentType.word())
                                                .suggests(ModCommands::suggestPoints)
                                                .executes(ModCommands::addPointToZone))))
                        .then(Commands.literal("removepoint")
                                .then(Commands.argument("zoneName", StringArgumentType.word())
                                        .suggests(ModCommands::suggestZones)
                                        .then(Commands.argument("pointName", StringArgumentType.word())
                                                .executes(ModCommands::removePointFromZone))))
                        .then(Commands.literal("setrequired")
                                .then(Commands.argument("zoneName", StringArgumentType.word())
                                        .suggests(ModCommands::suggestZones)
                                        .then(Commands.argument("requiredZone", StringArgumentType.word())
                                                .suggests(ModCommands::suggestZones)
                                                .executes(ModCommands::setZoneRequired)))
                                .then(Commands.argument("zoneName", StringArgumentType.word())
                                        .suggests(ModCommands::suggestZones)
                                        .then(Commands.literal("clear")
                                                .executes(ctx -> setZoneRequired(ctx, null)))))
                        .then(Commands.literal("status")
                                .executes(ModCommands::zoneStatus)))


                // ---- 方块相关命令 ----


                .then(Commands.literal("createat")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(ctx -> createPointAt(ctx, 5.0))
                                                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1, 100))
                                                                .executes(ctx -> createPointAt(ctx, DoubleArgumentType.getDouble(ctx, "radius")))))))))

                .then(Commands.literal("setradius")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(ModCommands::suggestPoints)
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1, 100))
                                        .executes(ModCommands::setPointRadius))))

                .then(Commands.literal("setcolor")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(ModCommands::suggestPoints)
                                .then(Commands.argument("color", IntegerArgumentType.integer(0, 0xFFFFFFFF))
                                        .executes(ModCommands::setPointColor))))

                .then(Commands.literal("settoggle")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(ModCommands::suggestPoints)
                                .then(Commands.argument("show", BoolArgumentType.bool())
                                        .executes(ModCommands::togglePointRange))))

                .then(Commands.literal("removefromallzones")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(ModCommands::suggestPoints)
                                .executes(ModCommands::removePointFromAllZones)))

                .then(Commands.literal("relationships")
                        .executes(ModCommands::showRelationships))

                // ---- Editor save (server-side stub; actual save uses direct API in singleplayer or custom packet) ----
                .then(Commands.literal("savegraph")
                        .executes(ModCommands::saveGraph))
        );
    }

    // ---- Help ----

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.header"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.create"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.remove"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.setowner"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.clearowner"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.list"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.zone_create"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.zone_remove"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.zone_addpoint"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.zone_removepoint"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.zone_status"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.gui"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.createat"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.setradius"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.setcolor"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.settoggle"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.zone_setrequired"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.relationships"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.help.savegraph"), false);
        return 1;
    }

    // ---- GUI ----

    /**
     * 打开据点管理 GUI。
     * 服务端无法直接打开客户端屏幕，因此：
     * - 单机模式下，给客户端发送提示手动使用管理器物品
     * - 专用服务器模式下，同样提示玩家
     * 客户端玩家可用 /capturepoint gui 命令 + 手持管理器物品打开界面。
     */
    private static int openGui(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.capturepoint.error.player_only"));
            return 0;
        }
        // 发送操作指引（客户端收到后可在 chat 中单击补全命令）
        source.sendSuccess(() -> Component.translatable("command.capturepoint.gui.hint"), false);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.gui.hint2"), false);
        return 1;
    }

    // ---- savegraph (server-side stub) ----

    /**
     * 保存节点图数据到 CaptureManager。
     * 单机模式下客户端通过直接 API 调用（CapturePointGraphScreen.saveGraph() 已处理）；
     * 专用服务器模式下此命令作为 fallback，但完整图数据太大不适合命令行传递，
     * 仅作为占位提示。完整支持需通过自定义网络包实现。
     */
    private static int saveGraph(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("command.capturepoint.savegraph.hint"), false);
        return 1;
    }

    // ---- Suggestions ----

    private static CompletableFuture<Suggestions> suggestPoints(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            var access = ICaptureDataAccess.server(ctx.getSource().getLevel());
            for (var name : access.getPoints().keySet()) {
                if (name.startsWith(builder.getRemaining())) {
                    builder.suggest(name);
                }
            }
        } catch (Exception ignored) {}
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestZones(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            var access = ICaptureDataAccess.server(ctx.getSource().getLevel());
            for (var name : access.getZones().keySet()) {
                if (name.startsWith(builder.getRemaining())) {
                    builder.suggest(name);
                }
            }
        } catch (Exception ignored) {}
        return builder.buildFuture();
    }

    // ---- Point commands ----

    private static int createPoint(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var name = StringArgumentType.getString(ctx, "name");
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.capturepoint.error.player_only"));
            return 0;
        }

        var level = source.getLevel();
        var pos = player.blockPosition();
        var manager = ICaptureDataAccess.server(level);
        manager.addOrUpdatePoint(name, pos);

        source.sendSuccess(() -> Component.translatable("command.capturepoint.create.success", name, pos.getX(), pos.getY(), pos.getZ()), true);
        return 1;
    }

    private static int removePoint(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var name = StringArgumentType.getString(ctx, "name");
        var manager = ICaptureDataAccess.server(source.getLevel());

        if (manager.getPoints().containsKey(name)) {
            manager.removePoint(name);
            source.sendSuccess(() -> Component.translatable("command.capturepoint.remove.success", name), true);
            return 1;
        } else {
            source.sendFailure(Component.translatable("command.capturepoint.error.not_found", name));
            return 0;
        }
    }

    private static int setOwner(CommandContext<CommandSourceStack> ctx) {
        return setOwner(ctx, StringArgumentType.getString(ctx, "owner"));
    }

    private static int setOwner(CommandContext<CommandSourceStack> ctx, @org.jetbrains.annotations.Nullable String owner) {
        var source = ctx.getSource();
        var name = StringArgumentType.getString(ctx, "name");
        var manager = ICaptureDataAccess.server(source.getLevel());

        if (!manager.getPoints().containsKey(name)) {
            source.sendFailure(Component.translatable("command.capturepoint.error.not_found", name));
            return 0;
        }

        manager.setPointOwner(name, owner);
        if (owner != null) {
            source.sendSuccess(() -> Component.translatable("command.capturepoint.setowner.success", name, owner), true);
        } else {
            source.sendSuccess(() -> Component.translatable("command.capturepoint.clearowner.success", name), true);
        }
        return 1;
    }

    private static int listAll(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var manager = ICaptureDataAccess.server(source.getLevel());

        source.sendSuccess(() -> Component.translatable("command.capturepoint.list.header"), false);

        var points = manager.getPoints();
        if (points.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.capturepoint.list.no_points"), false);
        } else {
            for (var entry : points.values()) {
                var owner = entry.owner() != null ? entry.owner() : "none";
                source.sendSuccess(() -> Component.translatable("command.capturepoint.list.point_entry",
                        entry.name(), entry.pos().getX(), entry.pos().getY(), entry.pos().getZ(), owner), false);
            }
        }

        var zones = manager.getZones();
        if (!zones.isEmpty()) {
            for (var entry : zones.values()) {
                var captured = manager.isZoneCaptured(entry.name());
                var statusKey = captured ? "command.capturepoint.list.captured" : "command.capturepoint.list.not_captured";
                source.sendSuccess(() -> Component.translatable("command.capturepoint.list.zone_entry",
                        entry.name(),
                        Component.translatable(statusKey),
                        String.join(", ", entry.capturePoints())), false);
            }
        }
        return 1;
    }

    private static int createZone(CommandContext<CommandSourceStack> ctx, @org.jetbrains.annotations.Nullable String requiredZone) {
        var source = ctx.getSource();
        var zoneName = StringArgumentType.getString(ctx, "zoneName");
        var manager = ICaptureDataAccess.server(source.getLevel());

        if (manager.getZones().containsKey(zoneName)) {
            source.sendFailure(Component.translatable("command.capturepoint.error.zone_exists", zoneName));
            return 0;
        }

        manager.createZone(zoneName, requiredZone);
        if (requiredZone != null) {
            source.sendSuccess(() -> Component.translatable("command.capturepoint.zone.create_with_req.success", zoneName, requiredZone), true);
        } else {
            source.sendSuccess(() -> Component.translatable("command.capturepoint.zone.create.success", zoneName), true);
        }
        return 1;
    }

    private static int removeZone(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var zoneName = StringArgumentType.getString(ctx, "zoneName");
        var manager = ICaptureDataAccess.server(source.getLevel());

        if (manager.getZones().containsKey(zoneName)) {
            manager.removeZone(zoneName);
            source.sendSuccess(() -> Component.translatable("command.capturepoint.zone.remove.success", zoneName), true);
            return 1;
        } else {
            source.sendFailure(Component.translatable("command.capturepoint.error.zone_not_found", zoneName));
            return 0;
        }
    }

    private static int addPointToZone(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var zoneName = StringArgumentType.getString(ctx, "zoneName");
        var pointName = StringArgumentType.getString(ctx, "pointName");
        var manager = ICaptureDataAccess.server(source.getLevel());

        if (!manager.getZones().containsKey(zoneName)) {
            source.sendFailure(Component.translatable("command.capturepoint.error.zone_not_found", zoneName));
            return 0;
        }
        if (!manager.getPoints().containsKey(pointName)) {
            source.sendFailure(Component.translatable("command.capturepoint.error.not_found", pointName));
            return 0;
        }

        manager.addPointToZone(zoneName, pointName);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.zone.addpoint.success", pointName, zoneName), true);
        return 1;
    }

    private static int removePointFromZone(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var zoneName = StringArgumentType.getString(ctx, "zoneName");
        var pointName = StringArgumentType.getString(ctx, "pointName");
        var manager = ICaptureDataAccess.server(source.getLevel());

        manager.removePointFromZone(zoneName, pointName);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.zone.removepoint.success", pointName, zoneName), true);
        return 1;
    }

    private static int zoneStatus(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var manager = ICaptureDataAccess.server(source.getLevel());
        var zones = manager.getZones();

        if (zones.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.capturepoint.list.no_zones"), false);
            return 1;
        }

        for (var entry : zones.values()) {
            var captured = manager.isZoneCaptured(entry.name());
            var accessible = manager.canAccessZone(entry.name());
            var statusKey = captured ? "command.capturepoint.list.captured" : "command.capturepoint.list.not_captured";
            var accessKey = accessible ? "command.capturepoint.list.accessible" : "command.capturepoint.list.locked";

            source.sendSuccess(() -> Component.translatable("command.capturepoint.zone.status_entry",
                    entry.name(),
                    Component.translatable(statusKey),
                    Component.translatable(accessKey)), false);
        }
        return 1;
    }

    // ---- Block-related commands ----

    /**
     * 在指定坐标创建据点（方块功能1用）。
     */
    private static int createPointAt(CommandContext<CommandSourceStack> ctx, double radius) {
        var source = ctx.getSource();
        var name = StringArgumentType.getString(ctx, "name");
        var x = IntegerArgumentType.getInteger(ctx, "x");
        var y = IntegerArgumentType.getInteger(ctx, "y");
        var z = IntegerArgumentType.getInteger(ctx, "z");

        var manager = ICaptureDataAccess.server(source.getLevel());

        if (manager.getPoints().containsKey(name)) {
            source.sendFailure(Component.translatable("command.capturepoint.error.point_exists", name));
            return 0;
        }

        var pos = new BlockPos(x, y, z);
        manager.addOrUpdatePointWithRadius(name, pos, radius);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.createat.success", name, x, y, z, (int) radius), true);
        return 1;
    }

    /**
     * 设置据点半径。
     */
    private static int setPointRadius(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var name = StringArgumentType.getString(ctx, "name");
        var radius = DoubleArgumentType.getDouble(ctx, "radius");
        var manager = ICaptureDataAccess.server(source.getLevel());

        if (!manager.getPoints().containsKey(name)) {
            source.sendFailure(Component.translatable("command.capturepoint.error.not_found", name));
            return 0;
        }

        manager.setPointRadius(name, radius);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.setradius.success", name, (int) radius), true);
        return 1;
    }

    /**
     * 设置据点显示颜色。
     */
    private static int setPointColor(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var name = StringArgumentType.getString(ctx, "name");
        var color = IntegerArgumentType.getInteger(ctx, "color");
        var manager = ICaptureDataAccess.server(source.getLevel());

        if (!manager.getPoints().containsKey(name)) {
            source.sendFailure(Component.translatable("command.capturepoint.error.not_found", name));
            return 0;
        }

        manager.setPointDisplayColor(name, color);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.setcolor.success", name, String.format("#%08X", color)), true);
        return 1;
    }

    /**
     * 切换据点范围显示。
     */
    private static int togglePointRange(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var name = StringArgumentType.getString(ctx, "name");
        var show = BoolArgumentType.getBool(ctx, "show");
        var manager = ICaptureDataAccess.server(source.getLevel());

        if (!manager.getPoints().containsKey(name)) {
            source.sendFailure(Component.translatable("command.capturepoint.error.not_found", name));
            return 0;
        }

        manager.setPointShowRange(name, show);
        if (show) {
            source.sendSuccess(() -> Component.translatable("command.capturepoint.settoggle.on", name), true);
        } else {
            source.sendSuccess(() -> Component.translatable("command.capturepoint.settoggle.off", name), true);
        }
        return 1;
    }

    /**
     * 将据点从所有区域中移除。
     */
    private static int removePointFromAllZones(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var name = StringArgumentType.getString(ctx, "name");
        var manager = ICaptureDataAccess.server(source.getLevel());

        if (!manager.getPoints().containsKey(name)) {
            source.sendFailure(Component.translatable("command.capturepoint.error.not_found", name));
            return 0;
        }

        String zoneName = manager.findZoneForPoint(name);
        if (zoneName == null) {
            source.sendSuccess(() -> Component.translatable("command.capturepoint.removefromallzones.not_in_zone", name), true);
            return 1;
        }

        manager.removePointFromZone(zoneName, name);
        source.sendSuccess(() -> Component.translatable("command.capturepoint.removefromallzones.success", name, zoneName), true);
        return 1;
    }

    /**
     * 设置或清除区域的依赖区域（requiredZone）。
     */
    private static int setZoneRequired(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var zoneName = StringArgumentType.getString(ctx, "zoneName");
        var requiredZone = StringArgumentType.getString(ctx, "requiredZone");
        return setZoneRequired(ctx, zoneName, requiredZone);
    }

    private static int setZoneRequired(CommandContext<CommandSourceStack> ctx, @org.jetbrains.annotations.Nullable String requiredZone) {
        var source = ctx.getSource();
        var zoneName = StringArgumentType.getString(ctx, "zoneName");
        return setZoneRequired(ctx, zoneName, requiredZone);
    }

    private static int setZoneRequired(CommandContext<CommandSourceStack> ctx, String zoneName, @org.jetbrains.annotations.Nullable String requiredZone) {
        var source = ctx.getSource();
        var manager = ICaptureDataAccess.server(source.getLevel());

        if (!manager.getZones().containsKey(zoneName)) {
            source.sendFailure(Component.translatable("command.capturepoint.error.zone_not_found", zoneName));
            return 0;
        }

        manager.setZoneRequiredZone(zoneName, requiredZone);
        if (requiredZone != null) {
            source.sendSuccess(() -> Component.translatable("command.capturepoint.zone.setrequired.success", zoneName, requiredZone), true);
        } else {
            source.sendSuccess(() -> Component.translatable("command.capturepoint.zone.setrequired.clear", zoneName), true);
        }
        return 1;
    }

    /**
     * 查询据点与区域之间的所有关系。
     */
    private static int showRelationships(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var manager = ICaptureDataAccess.server(source.getLevel());

        source.sendSuccess(() -> Component.translatable("command.capturepoint.relationships.header"), false);

        var points = manager.getPoints();
        if (points.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.capturepoint.list.no_points"), false);
        } else {
            for (var entry : points.values()) {
                String owner = entry.owner() != null ? entry.owner() : "-";
                // 查找此据点所属区域
                String zoneName = manager.findZoneForPoint(entry.name());
                String zoneInfo = zoneName != null ? zoneName : "-";
                source.sendSuccess(() -> Component.translatable(
                        "command.capturepoint.relationships.point_entry",
                        entry.name(), entry.pos().getX(), entry.pos().getY(), entry.pos().getZ(),
                        owner, zoneInfo), false);
            }
        }

        var zones = manager.getZones();
        if (!zones.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.capturepoint.relationships.zone_header"), false);
            for (var entry : zones.values()) {
                String pointsList = entry.capturePoints().isEmpty() ? "-" : String.join(", ", entry.capturePoints());
                String dep = entry.requiredZone() != null ? entry.requiredZone() : "-";
                source.sendSuccess(() -> Component.translatable(
                        "command.capturepoint.relationships.zone_entry",
                        entry.name(), pointsList, dep), false);
            }
        }

        return 1;
    }
}
