package com.blockesp;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

public class EspScreen extends Screen {
    private static final int ROWS = 6;
    private static final int TOP = 34;

    private static int page = 0;
    private static String custom = "";
    private String status = "";

    public EspScreen() {
        super(Component.literal("Block ESP"));
    }

    private static String onOff(boolean v) {
        return v ? "ON" : "OFF";
    }

    private int pageCount() {
        return Math.max(1, (Config.entries.size() + ROWS - 1) / ROWS);
    }

    private static String blockName(Config.Entry e) {
        Identifier id = Identifier.tryParse(e.id);
        if (id != null) {
            Block b = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (b != null) return b.getName().getString();
        }
        return e.id;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = TOP;

        this.addRenderableWidget(Button.builder(Component.literal("ESP: " + onOff(Config.master)), b -> {
            Config.master = !Config.master;
            Config.save();
            this.rebuildWidgets();
        }).bounds(cx - 129, y, 84, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Outline: " + onOff(Config.outline)), b -> {
            Config.outline = !Config.outline;
            Config.save();
            this.rebuildWidgets();
        }).bounds(cx - 43, y, 84, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Tracers: " + onOff(Config.tracers)), b -> {
            Config.tracers = !Config.tracers;
            Config.save();
            this.rebuildWidgets();
        }).bounds(cx + 45, y, 84, 20).build());
        y += 24;

        this.addRenderableWidget(Button.builder(Component.literal("Scan range: " + Config.range + " blocks"), b -> {
            int idx = 0;
            for (int i = 0; i < Config.RANGES.length; i++) {
                if (Config.RANGES[i] == Config.range) idx = i;
            }
            Config.range = Config.RANGES[(idx + 1) % Config.RANGES.length];
            Config.save();
            this.rebuildWidgets();
        }).bounds(cx - 129, y, 258, 20).build());
        y += 24;

        page = Math.max(0, Math.min(page, pageCount() - 1));
        int start = page * ROWS;
        int end = Math.min(Config.entries.size(), start + ROWS);
        for (int i = start; i < end; i++) {
            final Config.Entry e = Config.entries.get(i);

            this.addRenderableWidget(Button.builder(
                Component.literal((e.enabled ? "[ON] " : "[OFF] ") + blockName(e)), b -> {
                    e.enabled = !e.enabled;
                    Config.save();
                    this.rebuildWidgets();
                }).bounds(cx - 129, y, 150, 20).build());

            int ci = Config.paletteIndex(e.color);
            String colorName = ci >= 0 ? Config.PALETTE_NAMES[ci] : "Custom";
            final int shown = e.color;
            this.addRenderableWidget(Button.builder(
                Component.literal(colorName).withStyle(s -> s.withColor(shown)), b -> {
                    int idx = Config.paletteIndex(e.color);
                    e.color = Config.PALETTE[(idx + 1) % Config.PALETTE.length];
                    Config.save();
                    this.rebuildWidgets();
                }).bounds(cx + 25, y, 80, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("x"), b -> {
                Config.entries.remove(e);
                Config.save();
                this.rebuildWidgets();
            }).bounds(cx + 109, y, 20, 20).build());

            y += 22;
        }
        y = TOP + 48 + ROWS * 22;

        Button prev = Button.builder(Component.literal("< Prev"), b -> {
            page--;
            this.rebuildWidgets();
        }).bounds(cx - 129, y, 60, 20).build();
        prev.active = page > 0;
        this.addRenderableWidget(prev);

        Button next = Button.builder(Component.literal("Next >"), b -> {
            page++;
            this.rebuildWidgets();
        }).bounds(cx + 69, y, 60, 20).build();
        next.active = page < pageCount() - 1;
        this.addRenderableWidget(next);
        y += 24;

        EditBox field = new EditBox(this.font, cx - 129, y, 150, 20, Component.empty());
        field.setMaxLength(100);
        field.setValue(custom);
        field.setHint(Component.literal("minecraft:block_id"));
        field.setResponder(s -> custom = s);
        this.addRenderableWidget(field);

        this.addRenderableWidget(Button.builder(Component.literal("Add block"), b -> addCustom())
            .bounds(cx + 25, y, 104, 20).build());
    }

    private void addCustom() {
        String raw = custom.trim().toLowerCase();
        if (raw.isEmpty()) return;
        if (!raw.contains(":")) raw = "minecraft:" + raw;
        Identifier id = Identifier.tryParse(raw);
        if (id == null || BuiltInRegistries.BLOCK.getOptional(id).isEmpty()) {
            status = "Unknown block: " + raw;
            return;
        }
        if (id.getPath().endsWith("air")) {
            status = "Air can't be scanned";
            return;
        }
        for (Config.Entry e : Config.entries) {
            if (e.id.equals(raw)) {
                status = "Already in the list";
                return;
            }
        }
        Config.entries.add(new Config.Entry(raw, true,
            Config.PALETTE[Config.entries.size() % Config.PALETTE.length]));
        Config.save();
        custom = "";
        status = "";
        page = pageCount() - 1;
        this.rebuildWidgets();
    }

    private void centered(GuiGraphics graphics, String text, int y, int argb) {
        graphics.drawString(this.font, text, this.width / 2 - this.font.width(text) / 2, y, argb, true);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        centered(graphics, "Block ESP  (F6 to close)", 8, 0xFFFFFFFF);

        String seedLine;
        int seedColor;
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        ServerLevel overworld = server != null ? server.overworld() : null;
        if (overworld == null) {
            seedLine = "Not singleplayer: scanning loaded chunks only";
            seedColor = 0xFFAAAAAA;
        } else if (overworld.getSeed() == Config.SEED) {
            seedLine = "World seed matches " + Config.SEED;
            seedColor = 0xFF55FF55;
        } else {
            seedLine = "This world's seed is " + overworld.getSeed() + " (not " + Config.SEED + ")";
            seedColor = 0xFFFF5555;
        }
        centered(graphics, seedLine, 21, seedColor);

        int labelY = TOP + 48 + ROWS * 22 + 6;
        centered(graphics, (page + 1) + " / " + pageCount(), labelY, 0xFFAAAAAA);

        if (!status.isEmpty()) {
            centered(graphics, status, labelY + 48, 0xFFFF5555);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (BlockEspClient.openKey.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
