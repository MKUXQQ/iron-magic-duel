package com.example.scrollspellicons.duel;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Server-side configuration and runtime state for one duel group. */
public final class SpellDuelGroup {
    public enum Team { A, B }

    public record PointLocation(String dimension, double x, double y, double z, float yaw, float pitch) {
    }

    private final String id;
    private final Set<UUID> teamA = new LinkedHashSet<>();
    private final Set<UUID> teamB = new LinkedHashSet<>();
    private PointLocation pointA;
    private PointLocation pointB;
    private boolean active;

    public SpellDuelGroup(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("group id cannot be blank");
        }
        this.id = id;
    }

    public String id() { return id; }
    public Set<UUID> teamA() { return Collections.unmodifiableSet(teamA); }
    public Set<UUID> teamB() { return Collections.unmodifiableSet(teamB); }
    public PointLocation pointA() { return pointA; }
    public PointLocation pointB() { return pointB; }
    public boolean active() { return active; }

    public boolean add(UUID player, Team team) {
        if (active || player == null) return false;
        if (team == null) {
            teamA.remove(player);
            teamB.remove(player);
            return true;
        }
        teamA.remove(player);
        teamB.remove(player);
        return (team == Team.A ? teamA : teamB).add(player);
    }

    public void clearPlayers() {
        if (!active) {
            teamA.clear();
            teamB.clear();
        }
    }

    /** Restores a roster temporarily replaced by the dynamic single-duel adapter. */
    void restorePlayers(Set<UUID> oldA, Set<UUID> oldB) {
        if (active) throw new IllegalStateException("active group cannot restore players");
        teamA.clear();
        teamB.clear();
        teamA.addAll(oldA);
        teamB.addAll(oldB);
    }

    public void setPoint(Team team, PointLocation point) {
        if (active) throw new IllegalStateException("active group cannot change points");
        if (team == Team.A) pointA = point;
        else pointB = point;
    }

    public boolean isComplete() {
        return !teamA.isEmpty() && !teamB.isEmpty() && pointA != null && pointB != null;
    }

    public boolean contains(UUID player) {
        return teamA.contains(player) || teamB.contains(player);
    }

    public Team teamOf(UUID player) {
        if (teamA.contains(player)) return Team.A;
        if (teamB.contains(player)) return Team.B;
        return null;
    }

    public boolean isTeamEliminated(Team team, Set<UUID> livingPlayers) {
        Set<UUID> members = team == Team.A ? teamA : teamB;
        return members.stream().noneMatch(livingPlayers::contains);
    }

    public Team winner(Set<UUID> livingPlayers) {
        boolean aDead = isTeamEliminated(Team.A, livingPlayers);
        boolean bDead = isTeamEliminated(Team.B, livingPlayers);
        if (aDead == bDead) return null;
        return aDead ? Team.B : Team.A;
    }

    public Set<UUID> livingTeam(Team team, Set<UUID> livingPlayers) {
        Set<UUID> members = team == Team.A ? teamA : teamB;
        Set<UUID> result = new LinkedHashSet<>();
        members.stream().filter(livingPlayers::contains).forEach(result::add);
        return result;
    }

    public void setActive(boolean active) { this.active = active; }
}
