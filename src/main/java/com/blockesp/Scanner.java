package com.blockesp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Scans the chunks the client currently has loaded for the enabled blocks. */
public final class Scanner {
    public record Hit(int x, int y, int z, int color) {}

    private static final int MAX_HITS = 2000;

    public static volatile List<Hit> hits = List.of();
    public static volatile boolean dirty = true;
    private static int timer = 0;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !Config.master) {
            hits = List.of();
            return;
        }
        if (++timer < 20 && !dirty) return;
        timer = 0;
        dirty = false;
        scan(mc, level);
    }

    private static void scan(Minecraft mc, ClientLevel level) {
        Map<Block, Integer> wanted = new HashMap<>();
        for (Config.Entry e : Config.entries) {
            if (!e.enabled) continue;
            for (Block b : e.blocks()) wanted.put(b, e.color);
        }
        if (wanted.isEmpty()) {
            hits = List.of();
            return;
        }

        BlockPos pp = mc.player.blockPosition();
        int range = Config.range;
        long r2 = (long) range * range;
        int minCx = (pp.getX() - range) >> 4, maxCx = (pp.getX() + range) >> 4;
        int minCz = (pp.getZ() - range) >> 4, maxCz = (pp.getZ() + range) >> 4;

        List<Hit> out = new ArrayList<>();
        outer:
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                // Unloaded chunks come back as an empty chunk on the client, which has no blocks to match.
                LevelChunk chunk = level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection sec = sections[i];
                    if (sec == null || sec.hasOnlyAir()) continue;
                    if (!sec.maybeHas(state -> wanted.containsKey(state.getBlock()))) continue;
                    int baseY = (chunk.getMinSectionY() + i) << 4;
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                Integer color = wanted.get(sec.getBlockState(x, y, z).getBlock());
                                if (color == null) continue;
                                int wx = (cx << 4) + x;
                                int wy = baseY + y;
                                int wz = (cz << 4) + z;
                                long dx = wx - pp.getX(), dy = wy - pp.getY(), dz = wz - pp.getZ();
                                if (dx * dx + dy * dy + dz * dz > r2) continue;
                                out.add(new Hit(wx, wy, wz, color));
                                if (out.size() >= MAX_HITS) break outer;
                            }
                        }
                    }
                }
            }
        }
        hits = out;
    }
}
