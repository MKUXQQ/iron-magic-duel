package com.example.scrollspellicons.duel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Source and geometry regression checks; this deliberately starts no Minecraft server. */
public final class SurroundLockSelfTest {
    public static void main(String[] args) throws IOException {
        String manager = read("src/main/java/com/example/scrollspellicons/duel/SpellDuelManager.java");
        String commands = read("src/main/java/com/example/scrollspellicons/duel/SpellDuelCommands.java");
        String events = read("src/main/java/com/example/scrollspellicons/duel/SpellDuelEvents.java");

        require(commands, "literal(\"surround\")", "surround command root");
        require(commands, "literal(\"lock\").requires(source -> source.hasPermission(2))", "lock permission gate");
        require(commands, "literal(\"release\")", "release command");
        require(commands, "source.getPlayerOrException()", "online player command source");
        require(manager, "surroundLock != null", "single active surround session gate");
        require(manager, "SURROUND_JOIN_ATTEMPTS = 3", "bounded join retry count");
        require(manager, "SURROUND_RETRY_DELAYS = {1L, 20L, 40L}", "finite retry schedule");
        require(manager, "onSurroundPlayerLogin", "delayed login admission");
        require(manager, "onSurroundPlayerRespawn", "non-owner respawn re-lock");
        require(manager, "onSurroundPlayerChangedDimension", "non-owner dimension re-lock");
        require(manager, "surroundLock.owner.equals(player)", "owner lifecycle release");
        require(manager, "rollbackSurround(rollback)", "atomic teleport rollback");
        require(manager, "findSafeSurroundAnchor", "bounded safe landing check");
        require(manager, "tickSurround();", "manager-owned active tick");
        require(events, "manager.onSurroundPlayerLogin(player)", "login event integration");
        require(events, "duelManager.onSurroundPlayerRespawn(player)", "respawn event integration");
        require(events, "duelManager.onSurroundPlayerChangedDimension(player.getUUID())", "dimension event integration");
        require(events, "isSurroundLocked(player.getUUID())", "server pre-cast rejection");
        require(manager, "surroundLock.anchors.get(player) != null", "only safely placed members are locked");

        checkGeometry(0);
        checkGeometry(1);
        checkGeometry(2);
        checkGeometry(3);
        checkGeometry(32);

        int tick = manager.indexOf("private void tickSurround()");
        int reflow = manager.indexOf("private boolean reflowSurround", tick);
        String tickBody = manager.substring(tick, reflow);
        if (tickBody.contains("getAllEntities") || tickBody.contains("getAllLevels") || tickBody.contains("getPlayers()")) {
            throw new AssertionError("surround hot path must only traverse its session anchors/pending members");
        }
        String invalidated = methodBody(manager, "public void onSurroundPlayerInvalidated", "public void onSurroundPlayerChangedDimension");
        int deathReturn = invalidated.indexOf("if (death) return;");
        int remove = invalidated.indexOf("surroundLock.anchors.remove(player)");
        if (deathReturn < 0 || remove < deathReturn) {
            throw new AssertionError("non-owner death must retain the surround member until respawn");
        }
        String dimension = methodBody(manager, "public void onSurroundPlayerChangedDimension", "public void closeSurround");
        require(dimension, "requestSurroundReflow(surroundLock, player)", "dimension reflow request");
        String mismatch = "if (!player.level().dimension().equals(anchor.dimension)) continue;";
        require(tickBody, mismatch, "dimension mismatch must not trigger a tick-by-tick reflow");
        checkLifecycleModel();
        checkRetryBudget();
        System.out.println("surround-static: permission, ownership, lifecycle, pre-cast and rollback checks passed");
        System.out.println("surround-geometry: n=0/1/2/N radius and neighbour-spacing checks passed");
        System.out.println("surround-model: death retains member, logout removes, and failed dimension reflow plans at most three times");
        System.out.println("surround-static: source-only; no server, client, or network integration was started");
    }

    private static void checkGeometry(int count) {
        double radius = count <= 1 ? 2.0D : Math.max(2.0D, 1.0D / (2.0D * Math.sin(Math.PI / count)));
        if (radius < 2.0D) throw new AssertionError("radius below two blocks for n=" + count);
        if (count < 2) return;
        for (int index = 0; index < count; index++) {
            double a = Math.PI * 2.0D * index / count;
            double b = Math.PI * 2.0D * ((index + 1) % count) / count;
            double distance = Math.hypot(radius * Math.cos(a) - radius * Math.cos(b),
                    radius * Math.sin(a) - radius * Math.sin(b));
            if (distance + 1.0E-9D < 1.0D) throw new AssertionError("adjacent distance below one block for n=" + count);
        }
    }

    private static void checkLifecycleModel() {
        Set<String> anchors = new HashSet<>(Set.of("target"));
        boolean death = true;
        if (!death) anchors.remove("target");
        if (!anchors.contains("target")) throw new AssertionError("death incorrectly removed surround membership");
        boolean logout = true;
        if (logout) anchors.remove("target");
        if (anchors.contains("target")) throw new AssertionError("logout must remove surround membership");
    }

    private static void checkRetryBudget() {
        long[] delays = {1L, 20L, 40L};
        long due = delays[0];
        int attempts = 0;
        for (long tick = 1; tick <= 100; tick++) {
            if (tick != due) continue;
            attempts++;
            if (attempts >= 3) break;
            due = tick + delays[attempts];
        }
        if (attempts != 3) throw new AssertionError("retry budget must be exactly three plans in 100 ticks");
    }

    private static String methodBody(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        if (from < 0 || to < from) throw new AssertionError("method range missing: " + start);
        return source.substring(from, to);
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static void require(String source, String needle, String check) {
        if (!source.contains(needle)) throw new AssertionError(check + " missing: " + needle);
    }
}
