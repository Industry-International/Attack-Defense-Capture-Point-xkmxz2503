package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.xkmxz.attack_defense_capture_point_xkmxz.block.entity.CapturePointBlockEntity;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.CaptureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CaptureGraphRuntime {
    private CaptureGraphRuntime() {
    }

    public static void tick(ServerLevel level, CaptureManager manager) {
        var nodeOptions = manager.getNodeOptions();
        var wires = manager.getGraphWires();
        if (nodeOptions.isEmpty() || wires.isEmpty()) {
            return;
        }

        var outgoing = buildOutgoingMap(wires);
        var incoming = buildIncomingMap(wires);
        var conditionNodes = collectNodesWithOption(nodeOptions, "property");
        var actionNodes = collectNodesWithOption(nodeOptions, "action_type");
        var zoneLockChanges = evaluateZoneLocks(manager, nodeOptions, incoming, new HashSet<>());

        if (conditionNodes.isEmpty() || actionNodes.isEmpty()) {
            if (zoneLockChanges) {
                CapturePointBlockEntity.syncAllBoundBlocks(level);
            }
            return;
        }

        var executed = new HashSet<String>();
        boolean changed = zoneLockChanges;

        for (var conditionNode : conditionNodes) {
            var targets = resolveConditionTargets(conditionNode, incoming, manager);
            if (targets.isEmpty() && hasBooleanInputWire(conditionNode, incoming)) {
                targets = List.of(new GraphTarget(null, null));
            }
            var contexts = new ArrayList<GraphContext>();
            for (var target : targets) {
                var evalContext = GraphContext.fromTarget(target, manager);
                if (evalContext != null) {
                    contexts.add(evalContext);
                }
            }
            if (contexts.isEmpty()) {
                continue;
            }

            boolean aggregate = true;
            for (var context : contexts) {
                Boolean result = evaluateConditionNode(conditionNode, context, nodeOptions);
                if (result == null || !result) {
                    aggregate = false;
                    break;
                }
            }

            for (var context : contexts) {
                changed |= propagateFromCondition(conditionNode, aggregate, context, nodeOptions, outgoing, incoming,
                        manager, level, executed, new HashSet<>());
            }
        }

        if (changed) {
            CapturePointBlockEntity.syncAllBoundBlocks(level);
        }
    }

    private static Map<String, List<CaptureManager.GraphWireData>> buildOutgoingMap(List<CaptureManager.GraphWireData> wires) {
        var map = new HashMap<String, List<CaptureManager.GraphWireData>>();
        for (var wire : wires) {
            map.computeIfAbsent(wire.fromNode(), k -> new ArrayList<>()).add(wire);
        }
        return map;
    }

    private static Map<String, List<CaptureManager.GraphWireData>> buildIncomingMap(List<CaptureManager.GraphWireData> wires) {
        var map = new HashMap<String, List<CaptureManager.GraphWireData>>();
        for (var wire : wires) {
            map.computeIfAbsent(wire.toNode(), k -> new ArrayList<>()).add(wire);
        }
        return map;
    }

    private static Set<String> collectNodesWithOption(Map<String, Map<String, String>> nodeOptions, String optionId) {
        var result = new LinkedHashSet<String>();
        for (var entry : nodeOptions.entrySet()) {
            var opts = entry.getValue();
            if (opts != null && opts.containsKey(optionId)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private static boolean hasBooleanInputWire(String conditionNode,
                                               Map<String, List<CaptureManager.GraphWireData>> incoming) {
        var inputs = incoming.get(conditionNode);
        if (inputs == null) {
            return false;
        }
        for (var wire : inputs) {
            if ("bool_in".equals(wire.toPort())) {
                return true;
            }
        }
        return false;
    }

    private static List<GraphTarget> resolveConditionTargets(String conditionNode,
                                                             Map<String, List<CaptureManager.GraphWireData>> incoming,
                                                             CaptureManager manager) {
        var result = new ArrayList<GraphTarget>();
        var inputs = incoming.get(conditionNode);
        if (inputs == null) {
            return result;
        }

        for (var wire : inputs) {
            if ("point_target".equals(wire.toPort())) {
                var point = manager.getPoints().get(wire.fromNode());
                if (point != null) {
                    result.add(GraphTarget.forPoint(wire.fromNode()));
                }
            } else if ("zone_target".equals(wire.toPort())) {
                var zone = manager.getZones().get(wire.fromNode());
                if (zone != null) {
                    result.add(GraphTarget.forZone(wire.fromNode()));
                }
            }
        }
        return result;
    }

    @Nullable
    private static Boolean evaluateConditionNode(String nodeName,
                                                 GraphContext context,
                                                 Map<String, Map<String, String>> nodeOptions) {
        var opts = nodeOptions.get(nodeName);
        if (opts == null) return null;

        var property = CaptureConditionNode.PropertyType.fromId(opts.get("property"));
        var operator = CaptureConditionNode.OperatorType.fromId(opts.get("operator"));
        String compareValue = opts.getOrDefault("compare_value", "");
        boolean propertyResult = evaluateCondition(property, operator, compareValue, context);
        return propertyResult;
    }

    private static boolean evaluateCondition(CaptureConditionNode.PropertyType property,
                                             CaptureConditionNode.OperatorType operator,
                                             String compareValue,
                                             GraphContext context) {
        if (property == null || operator == null) {
            return false;
        }

        String actualValue = getPropertyValue(property, context);
        String expectedValue = compareValue != null ? compareValue : "";
        return operator.evaluate(actualValue, expectedValue);
    }

    private static boolean propagateFromCondition(String conditionNode,
                                                  boolean result,
                                                  GraphContext context,
                                                  Map<String, Map<String, String>> nodeOptions,
                                                  Map<String, List<CaptureManager.GraphWireData>> outgoing,
                                                  Map<String, List<CaptureManager.GraphWireData>> incoming,
                                                  CaptureManager manager,
                                                  ServerLevel level,
                                                  Set<String> executed,
                                                  Set<String> recursionGuard) {
        if (!recursionGuard.add("cond:" + conditionNode + ":" + context.key() + ":" + result)) {
            return false;
        }

        boolean changed = false;
        var nextWires = outgoing.get(conditionNode);
        if (nextWires == null) {
            return false;
        }

        String expectedPort = result ? "true_out" : "false_out";
        for (var wire : nextWires) {
            if (!expectedPort.equals(wire.fromPort())) continue;
            changed |= propagateBooleanToNode(wire.toNode(), context, nodeOptions, outgoing, incoming, manager, level,
                    executed, recursionGuard);
        }
        return changed;
    }

    private static boolean propagateBooleanToNode(String nodeName,
                                                  GraphContext context,
                                                  Map<String, Map<String, String>> nodeOptions,
                                                  Map<String, List<CaptureManager.GraphWireData>> outgoing,
                                                  Map<String, List<CaptureManager.GraphWireData>> incoming,
                                                  CaptureManager manager,
                                                  ServerLevel level,
                                                  Set<String> executed,
                                                  Set<String> recursionGuard) {
        var opts = nodeOptions.get(nodeName);
        if (opts == null) return false;

        if (opts.containsKey("gate_type")) {
            boolean gateResult = evaluateGateNode(nodeName, context, nodeOptions, incoming, recursionGuard);
            if (!gateResult) {
                return false;
            }

            boolean changed = false;
            var nextWires = outgoing.get(nodeName);
            if (nextWires != null) {
                for (var wire : nextWires) {
                    if ("result".equals(wire.fromPort())) {
                        changed |= propagateBooleanToNode(wire.toNode(), context, nodeOptions, outgoing, incoming,
                                manager, level, executed, recursionGuard);
                    }
                }
            }
            return changed;
        }

        if (opts.containsKey("action_type")) {
            String execKey = nodeName + "|" + context.key();
            if (!executed.add(execKey)) {
                return false;
            }
            return executeAction(nodeName, opts, context, manager, level);
        }

        return false;
    }

    private static boolean evaluateConditionWithBooleanInputs(String nodeName,
                                                              GraphContext context,
                                                              Map<String, Map<String, String>> nodeOptions,
                                                              Map<String, List<CaptureManager.GraphWireData>> incoming,
                                                              Set<String> recursionGuard) {
        Boolean self = evaluateConditionNode(nodeName, context, nodeOptions);
        if (self == null || !self) {
            return false;
        }

        var wires = incoming.get(nodeName);
        if (wires == null) {
            return true;
        }

        for (var wire : wires) {
            if (!"bool_in".equals(wire.toPort())) continue;
            boolean upstream = evaluateBooleanOutput(wire.fromNode(), wire.fromPort(), context, nodeOptions, incoming, recursionGuard);
            if (!upstream) {
                return false;
            }
        }
        return true;
    }

    private static boolean evaluateGateNode(String nodeName,
                                            GraphContext context,
                                            Map<String, Map<String, String>> nodeOptions,
                                            Map<String, List<CaptureManager.GraphWireData>> incoming,
                                            Set<String> recursionGuard) {
        String guardKey = "gate:" + nodeName + ":" + context.key();
        if (!recursionGuard.add(guardKey)) {
            return false;
        }

        try {
            var opts = nodeOptions.get(nodeName);
            if (opts == null) return false;

            var gateType = LogicGateNode.GateType.fromId(opts.get("gate_type"));
            boolean inputA = false;
            boolean inputB = false;

            var wires = incoming.get(nodeName);
            if (wires != null) {
                for (var wire : wires) {
                    boolean value = evaluateBooleanOutput(wire.fromNode(), wire.fromPort(), context, nodeOptions, incoming, recursionGuard);
                    if ("in_a".equals(wire.toPort())) {
                        inputA = value;
                    } else if ("in_b".equals(wire.toPort())) {
                        inputB = value;
                    }
                }
            }

            return gateType.evaluate(inputA, gateType == LogicGateNode.GateType.NOT ? false : inputB);
        } finally {
            recursionGuard.remove(guardKey);
        }
    }

    private static boolean evaluateBooleanOutput(String fromNode,
                                                 String fromPort,
                                                 GraphContext context,
                                                 Map<String, Map<String, String>> nodeOptions,
                                                 Map<String, List<CaptureManager.GraphWireData>> incoming,
                                                 Set<String> recursionGuard) {
        var opts = nodeOptions.get(fromNode);
        if (opts == null) return false;

        if (opts.containsKey("property")) {
            boolean condition = evaluateConditionWithBooleanInputs(fromNode, context, nodeOptions, incoming, recursionGuard);
            return "true_out".equals(fromPort) ? condition : !condition;
        }
        if (opts.containsKey("gate_type")) {
            return "result".equals(fromPort) && evaluateGateNode(fromNode, context, nodeOptions, incoming, recursionGuard);
        }
        if (opts.containsKey("constant_value")) {
            return parseBoolean(opts.get("constant_value"));
        }
        return false;
    }

    private static boolean evaluateZoneLocks(CaptureManager manager,
                                             Map<String, Map<String, String>> nodeOptions,
                                             Map<String, List<CaptureManager.GraphWireData>> incoming,
                                             Set<String> recursionGuard) {
        boolean changed = false;
        for (var zoneEntry : manager.getZones().entrySet()) {
            String zoneName = zoneEntry.getKey();
            var zoneNodeIncoming = incoming.get(zoneName);
            if (zoneNodeIncoming == null || zoneNodeIncoming.isEmpty()) {
                changed |= applyZoneLockState(manager, zoneName, false);
                continue;
            }

            var context = GraphContext.fromTarget(GraphTarget.forZone(zoneName), manager);
            if (context == null) continue;

            boolean hasUnlockInput = false;
            boolean anyUnlockTrue = false;
            boolean anyLockTrue = false;

            for (var wire : zoneNodeIncoming) {
                if ("unlock_in".equals(wire.toPort())) {
                    hasUnlockInput = true;
                    if (evaluateBooleanOutput(wire.fromNode(), wire.fromPort(), context, nodeOptions, incoming, recursionGuard)) {
                        anyUnlockTrue = true;
                    }
                } else if ("lock_in".equals(wire.toPort())) {
                    if (evaluateBooleanOutput(wire.fromNode(), wire.fromPort(), context, nodeOptions, incoming, recursionGuard)) {
                        anyLockTrue = true;
                    }
                }
            }

            boolean locked = anyLockTrue || (hasUnlockInput && !anyUnlockTrue);
            changed |= applyZoneLockState(manager, zoneName, locked);
        }
        return changed;
    }

    private static boolean applyZoneLockState(CaptureManager manager, String zoneName, boolean locked) {
        var zone = manager.getZones().get(zoneName);
        if (zone == null || zone.locked() == locked) {
            return false;
        }
        manager.setZoneLocked(zoneName, locked);
        return true;
    }

    private static boolean executeAction(String nodeName,
                                         Map<String, String> opts,
                                         GraphContext context,
                                         CaptureManager manager,
                                         ServerLevel level) {
        var actionType = CaptureActionNode.ActionType.fromId(opts.get("action_type"));
        String targetName = normalize(opts.get("target_name"));
        String actionValue = normalize(opts.get("action_value"));
        boolean silent = parseBoolean(opts.get("silent"));

        return switch (actionType) {
            case SET_CAPTURED -> applySetCaptured(targetName, actionValue, context, manager);
            case SET_OWNER_TEAM -> applySetOwnerTeam(targetName, actionValue, context, manager);
            case SET_ZONE_CAPTURED -> applySetZoneCaptured(targetName, actionValue, context, manager);
            case NOTIFY -> applyNotify(level, nodeName, targetName, actionValue, context);
            case ADD_TO_ZONE -> applyAddToZone(targetName, actionValue, context, manager);
            case REMOVE_FROM_ZONE -> applyRemoveFromZone(targetName, actionValue, context, manager);
            case RUN_COMMAND -> applyRunCommand(level, targetName, actionValue, context, silent);
        };
    }

    private static boolean applySetCaptured(String targetName, String actionValue, GraphContext context, CaptureManager manager) {
        String pointName = !targetName.isEmpty() ? targetName : context.pointName();
        if (pointName == null || !manager.getPoints().containsKey(pointName)) return false;
        boolean captured = parseBoolean(actionValue.isEmpty() ? "true" : actionValue);
        var point = manager.getPoints().get(pointName);
        if (point == null || point.captured() == captured) return false;
        manager.setPointCaptured(pointName, captured);
        return true;
    }

    private static boolean applySetOwnerTeam(String targetName, String actionValue, GraphContext context, CaptureManager manager) {
        String pointName = !targetName.isEmpty() ? targetName : context.pointName();
        if (pointName == null || !manager.getPoints().containsKey(pointName)) return false;
        String newOwner = actionValue.isEmpty() ? null : actionValue;
        var point = manager.getPoints().get(pointName);
        if (point == null) return false;
        if ((point.ownerTeam() == null && newOwner == null) || (point.ownerTeam() != null && point.ownerTeam().equals(newOwner))) {
            return false;
        }
        manager.setPointOwnerTeam(pointName, newOwner);
        return true;
    }

    private static boolean applySetZoneCaptured(String targetName, String actionValue, GraphContext context, CaptureManager manager) {
        String zoneName = !targetName.isEmpty() ? targetName : context.zoneName();
        if (zoneName == null || !manager.getZones().containsKey(zoneName)) return false;
        boolean captured = parseBoolean(actionValue.isEmpty() ? "true" : actionValue);
        var zone = manager.getZones().get(zoneName);
        if (zone == null || zone.captured() == captured) return false;
        manager.setZoneCaptured(zoneName, captured);
        return true;
    }

    private static boolean applyNotify(ServerLevel level,
                                       String nodeName,
                                       String targetName,
                                       String actionValue,
                                       GraphContext context) {
        String message = !actionValue.isEmpty() ? actionValue : (!targetName.isEmpty() ? targetName : nodeName);
        if (message.isEmpty()) return false;
        level.players().forEach(player -> player.displayClientMessage(Component.literal(message), true));
        return true;
    }

    private static boolean applyAddToZone(String targetName, String actionValue, GraphContext context, CaptureManager manager) {
        String zoneName = !targetName.isEmpty() ? targetName : context.zoneName();
        String pointName = !actionValue.isEmpty() ? actionValue : context.pointName();
        if (zoneName == null || pointName == null) return false;
        if (!manager.getZones().containsKey(zoneName) || !manager.getPoints().containsKey(pointName)) return false;
        var zone = manager.getZones().get(zoneName);
        if (zone != null && zone.capturePoints().contains(pointName)) return false;
        manager.addPointToZone(zoneName, pointName);
        return true;
    }

    private static boolean applyRemoveFromZone(String targetName, String actionValue, GraphContext context, CaptureManager manager) {
        String pointName = !targetName.isEmpty() ? targetName : (!actionValue.isEmpty() ? actionValue : context.pointName());
        if (pointName == null || !manager.getPoints().containsKey(pointName)) return false;
        String zoneName = manager.findZoneForPoint(pointName);
        if (zoneName == null) return false;
        manager.removePointFromZone(zoneName, pointName);
        return true;
    }

    private static boolean applyRunCommand(ServerLevel level,
                                           String targetName,
                                           String actionValue,
                                           GraphContext context,
                                           boolean silent) {
        String command = !actionValue.isEmpty() ? actionValue : targetName;
        if (command.isEmpty()) return false;

        command = command
                .replace("{point}", context.pointName() != null ? context.pointName() : "")
                .replace("{zone}", context.zoneName() != null ? context.zoneName() : "");
        if (context.point() != null) {
            command = command
                    .replace("{x}", String.valueOf(context.point().pos().getX()))
                    .replace("{y}", String.valueOf(context.point().pos().getY()))
                    .replace("{z}", String.valueOf(context.point().pos().getZ()));
        }

        String finalCommand = command.startsWith("/") ? command.substring(1) : command;
        var server = level.getServer();
        if (server == null) return false;

        var source = server.createCommandSourceStack()
                .withLevel(level)
                .withPermission(2)
                .withSuppressedOutput();
        try {
            server.getCommands().performPrefixedCommand(source, finalCommand);
            if (!silent) {
                var message = Component.translatable("toast.capture_graph.command.success", finalCommand);
                level.players().forEach(player -> player.displayClientMessage(message, true));
            }
            return true;
        } catch (Exception e) {
            if (!silent) {
                var message = Component.translatable("toast.capture_graph.command.failed", finalCommand);
                level.players().forEach(player -> player.displayClientMessage(message, true));
            }
            return false;
        }
    }

    private static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static String getPropertyValue(CaptureConditionNode.PropertyType property, GraphContext context) {
        return switch (property) {
            case CAPTURED -> String.valueOf(context.point() != null && context.point().captured());
            case OWNER_TEAM -> context.point() != null && context.point().ownerTeam() != null ? context.point().ownerTeam() : "";
            case CAPTURING_TEAM -> context.point() != null && context.point().capturingTeam() != null ? context.point().capturingTeam() : "";
            case PROGRESS -> String.valueOf(context.point() != null ? context.point().captureProgress() : 0);
            case ZONE_CAPTURED -> String.valueOf(context.zone() != null && context.zone().captured());
            case ZONE_OWNER_TEAM -> context.zone() != null && context.zone().ownerTeam() != null ? context.zone().ownerTeam() : "";
            case ZONE_ACCESSIBLE -> String.valueOf(context.zoneName() != null && context.manager().canAccessZone(context.zoneName()));
        };
    }

    private record GraphTarget(@Nullable String pointName, @Nullable String zoneName) {
        static GraphTarget forPoint(String pointName) {
            return new GraphTarget(pointName, null);
        }

        static GraphTarget forZone(String zoneName) {
            return new GraphTarget(null, zoneName);
        }
    }

    private record GraphContext(@Nullable String pointName,
                                @Nullable String zoneName,
                                @Nullable CaptureManager.CapturePointEntry point,
                                @Nullable CaptureManager.ZoneEntry zone,
                                CaptureManager manager) {
        static GraphContext fromTarget(GraphTarget target, CaptureManager manager) {
            var point = target.pointName() != null ? manager.getPoints().get(target.pointName()) : null;
            var zone = target.zoneName() != null ? manager.getZones().get(target.zoneName()) : null;
            if (target.pointName() != null && point == null) {
                return null;
            }
            if (target.zoneName() != null && zone == null) {
                return null;
            }
            return new GraphContext(target.pointName(), target.zoneName(), point, zone, manager);
        }

        String key() {
            if (pointName != null) return "point:" + pointName;
            if (zoneName != null) return "zone:" + zoneName;
            return "none";
        }
    }
}
