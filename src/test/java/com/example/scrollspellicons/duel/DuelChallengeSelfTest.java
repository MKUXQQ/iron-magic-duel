package com.example.scrollspellicons.duel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Source-level regression checks for the server-authoritative challenge flow.
 * This deliberately starts no Minecraft server: integration cases remain a
 * manual two-player test because they require live network and world state.
 */
public final class DuelChallengeSelfTest {
    public static void main(String[] args) throws IOException {
        String manager = read("src/main/java/com/example/scrollspellicons/duel/SpellDuelManager.java");
        String group = read("src/main/java/com/example/scrollspellicons/duel/SpellDuelGroup.java");
        String network = read("src/main/java/com/example/scrollspellicons/duel/SpellDuelNetwork.java");
        String commands = read("src/main/java/com/example/scrollspellicons/duel/SpellDuelCommands.java");
        String events = read("src/main/java/com/example/scrollspellicons/duel/SpellDuelSelectionEvents.java");
        String screens = read("src/main/java/com/example/scrollspellicons/client/DuelClientScreens.java");

        require(manager, "CHALLENGE_TIMEOUT_TICKS = 15L * 20L", "15-second pending challenge timeout");
        require(manager, "challengeByPlayer", "one pending/active challenge per player");
        require(manager, "activeDuelPlayers.containsKey", "legacy active duel exclusion");
        require(manager, "reservedChallengeGroups", "atomic challenge point reservation");
        require(manager, "state.accepted", "accept-before-point-selection gate");
        require(manager, "state.token != token", "challenge token validation");
        require(manager, "session.token != token", "interaction token validation");
        require(manager, "session.target.equals(requestedTarget)", "interaction target validation");
        require(manager, "restorePlayers", "temporary roster restoration");
        require(manager, "teleportTeam", "existing duel teleport path reuse");
        require(manager, "restoreSavedState", "existing SavedState restore path");
        require(manager, "forceEndChallenge", "immediate cancellation path for new challenges");
        require(manager, "finishParticipants(group, Set.of(), null, 0L)", "force end has no winner restore delay");
        require(manager, "removePendingWinnerRestore(state.challenger, group.id())", "force end clears same-group challenger restore");
        require(manager, "removePendingWinnerRestore(state.target, group.id())", "force end clears same-group target restore");
        require(manager, "forceEndPendingChallengeRestore(target)", "admin picker clears a new-challenge winner restore");
        require(manager, "challengeForGroup(group.id())", "new-challenge winner source lookup");
        require(manager, "challengeToken", "pending winner restore source marker");
        require(manager, "challenge == null ? 0L : challenge.token", "legacy finish keeps restore unmarked");
        require(manager, "restoreSavedState(player, pending.state())", "pending new-challenge winner restores immediately");
        require(manager, "activeDuelPlayers.remove(player, pending.groupId())", "pending new-challenge winner leaves active index");
        require(manager, "finishParticipants(group, winners, deadPlayer, challenge == null ? 0L : challenge.token)",
                "new-challenge death finish carries the source marker");
        int force = manager.indexOf("private void forceEndChallenge");
        int participants = force < 0 ? -1 : manager.indexOf("finishParticipants(group, Set.of(), null, 0L)", force);
        int roster = participants < 0 ? -1 : manager.indexOf("restoreChallengeRoster(group)", participants);
        int release = roster < 0 ? -1 : manager.indexOf("releaseChallengeForGroup(group.id())", roster);
        if (force < 0 || participants < force || roster < participants || release < roster) {
            throw new AssertionError("force end must restore participants and roster before releasing the challenge");
        }
        require(manager, "closeChallenges", "server-stop cleanup");
        require(manager, "onPlayerChangedDimension", "dimension-transition cleanup");
        require(group, "void restorePlayers(Set<UUID> oldA, Set<UUID> oldB)", "thin group adapter");

        require(commands, "literal(\"duel\")", "duel command");
        require(commands, "StringArgumentType.word()", "game-name-only argument");
        require(commands, "getPlayerByName(targetName)", "online player resolution");
        require(events, "PLAYER_INTERACTOR", "separate interaction item");
        require(events, "!player.hasPermissions(2)", "server-side selector permission gate");
        require(network, "InteractionActionPayload", "interaction action payload");
        require(network, "ChallengeReplyPayload", "accept/reject payload");
        require(network, "ChallengePointChoicePayload", "point choice payload");
        require(network, "context.enqueueWork", "server-thread packet handling");
        require(network, "selector.hasPermissions(2)", "packet-side admin permission gate");
        require(network, "cancelSelectedPlayer(selector.getUUID(), payload.target)", "selector source validation");
        require(manager, "endChallengeForPlayer(target)", "selector only ends the selected player's new challenge");
        int add = manager.indexOf("public boolean addPlayerToEditingGroup");
        int addEnd = manager.indexOf("public boolean cancelSelectedPlayer", add);
        String addFlow = manager.substring(add, addEnd < 0 ? manager.length() : addEnd);
        int forceEnd = addFlow.indexOf("endChallengeForPlayer(target)");
        int pendingRestore = addFlow.indexOf("forceEndPendingChallengeRestore(target)");
        int legacyRoster = addFlow.indexOf("String selectedGroup = selectedGroup(target);");
        if (forceEnd < 0 || pendingRestore < forceEnd || legacyRoster < 0 || pendingRestore > legacyRoster) {
            throw new AssertionError("admin selection must end the selected challenge before legacy roster handling");
        }
        require(manager, "equippedSpellSnapshot", "server-side equipped spellbook snapshot");
        require(manager, "CuriosApi.getCuriosInventory", "server-side Curios inventory access");
        require(manager, "findCurios(\"spellbook\")", "Curios spellbook slot lookup");
        require(manager, "ISpellContainer.get(stack).getActiveSpells()", "configured spellbook slots only");
        require(manager, "spellIds.stream().sorted().toList()", "stable de-duplicated spell ID order");
        require(manager, "snapshot.hasSpellbook()", "explicit no-equipped-spellbook state");
        if (manager.contains("getLearnedSpellData") || manager.contains("learnedSpellIds")) {
            throw new AssertionError("view-spells path must not read learned-spell data");
        }
        require(network, "payload.hasSpellbook", "equipped spellbook state in view payload");
        require(screens, "当前装备法术书中的法术", "equipped spellbook title");
        require(screens, "该玩家未装备法术书", "missing equipped spellbook message");

        int duel = screens.indexOf("Component.literal(\"单挑\")");
        int learned = screens.indexOf("Component.literal(\"查看法术\")");
        if (duel < 0 || learned < 0 || duel >= learned) {
            throw new AssertionError("interaction menu order is not 单挑 -> 查看法术");
        }

        System.out.println("challenge-static: command, token, permission, and online-target checks passed");
        System.out.println("challenge-static: accept -> server point list -> atomic reserve -> existing teleport/restore path passed");
        System.out.println("challenge-static: interaction menu order and server equipped-spellbook snapshot path passed");
        System.out.println("challenge-static: source-only; no server, client, or network integration was started");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static void require(String source, String needle, String check) {
        if (!source.contains(needle)) throw new AssertionError(check + " missing: " + needle);
    }
}
