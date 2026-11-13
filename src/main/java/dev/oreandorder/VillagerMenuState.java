package dev.oreandorder;

import java.util.List;
import java.util.UUID;

/** Server-authored presentation data. The client never decides who may control a worker. */
public record VillagerMenuState(UUID entity, String name, boolean worker, boolean owned, boolean canManage,
        boolean canRecruit, boolean canTrade, boolean busy, boolean ready, String status, String notice,
        String storage, String area, String tool, List<Stack> inventory, boolean canUseAi) {
    public record Stack(String item, String name, int count) {}
}
