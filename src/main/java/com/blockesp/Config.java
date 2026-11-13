package com.blockesp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Config {
    public static final long SEED = 6608149111735331168L;

    public static final int[] PALETTE = {
        0xFF3B30, 0xFF9500, 0xFFE600, 0x30D158, 0x00E5FF, 0x3B82F6, 0xB44CFF, 0xFF5CA8, 0xFFFFFF
    };
    public static final String[] PALETTE_NAMES = {
        "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink", "White"
    };
    public static final int[] RANGES = {32, 64, 96, 128};

    public static final class Entry {
        public String id = "minecraft:stone";
        public boolean enabled = true;
        public int color = 0xFFFFFF;

        public Entry() {}

        public Entry(String id, boolean enabled, int color) {
            this.id = id;
            this.enabled = enabled;
            this.color = color;
        }

        /** The block itself, plus its deepslate variant for ores. */
        public List<Block> blocks() {
            List<Block> out = new ArrayList<>();
            Identifier i = Identifier.tryParse(id);
            if (i == null) return out;
            BuiltInRegistries.BLOCK.getOptional(i).ifPresent(out::add);
            if (out.isEmpty()) return out;
            String p = i.getPath();
            if (p.endsWith("_ore") && !p.startsWith("deepslate_")) {
                Identifier d = Identifier.fromNamespaceAndPath(i.getNamespace(), "deepslate_" + p);
                BuiltInRegistries.BLOCK.getOptional(d).ifPresent(out::add);
            }
            return out;
        }
    }

    private static final class Data {
        boolean master = true;
        boolean outline = true;
        boolean tracers = true;
        int range = 64;
        List<Entry> entries = new ArrayList<>();
    }

    public static boolean master = true;
    public static boolean outline = true;
    public static boolean tracers = true;
    public static int range = 64;
    public static final List<Entry> entries = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("blockesp.json");
    }

    private static void defaults() {
        entries.clear();
        entries.add(new Entry("minecraft:diamond_ore", true, 0x00E5FF));
        entries.add(new Entry("minecraft:ancient_debris", true, 0xFF9500));
        entries.add(new Entry("minecraft:emerald_ore", true, 0x30D158));
        entries.add(new Entry("minecraft:gold_ore", true, 0xFFE600));
        entries.add(new Entry("minecraft:iron_ore", false, 0xFFFFFF));
        entries.add(new Entry("minecraft:coal_ore", false, 0x3B82F6));
        entries.add(new Entry("minecraft:copper_ore", false, 0xFF9500));
        entries.add(new Entry("minecraft:redstone_ore", false, 0xFF3B30));
        entries.add(new Entry("minecraft:lapis_ore", false, 0x3B82F6));
        entries.add(new Entry("minecraft:nether_gold_ore", false, 0xFFE600));
        entries.add(new Entry("minecraft:nether_quartz_ore", false, 0xFFFFFF));
        entries.add(new Entry("minecraft:spawner", false, 0xB44CFF));
        entries.add(new Entry("minecraft:chest", false, 0xFF5CA8));
    }

    public static void load() {
        defaults();
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            Data d = GSON.fromJson(Files.readString(f), Data.class);
            if (d == null) return;
            master = d.master;
            outline = d.outline;
            tracers = d.tracers;
            range = d.range > 0 ? d.range : 64;
            if (d.entries != null && !d.entries.isEmpty()) {
                entries.clear();
                entries.addAll(d.entries);
            }
        } catch (Exception e) {
            System.err.println("[BlockESP] Could not read config, using defaults: " + e);
        }
    }

    public static void save() {
        try {
            Data d = new Data();
            d.master = master;
            d.outline = outline;
            d.tracers = tracers;
            d.range = range;
            d.entries = new ArrayList<>(entries);
            Files.writeString(file(), GSON.toJson(d));
        } catch (Exception e) {
            System.err.println("[BlockESP] Could not save config: " + e);
        }
        Scanner.dirty = true;
    }

    public static int paletteIndex(int color) {
        for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == color) return i;
        return -1;
    }
}
