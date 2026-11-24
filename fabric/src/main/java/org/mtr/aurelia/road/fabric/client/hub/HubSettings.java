/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.hub;

import net.minecraft.text.Text;
import org.mtr.aurelia.road.fabric.client.compat.IrisCompat;
import org.mtr.aurelia.road.fabric.client.preview.StratosPreview;
import org.mtr.aurelia.road.fabric.client.ui.HubIcon;
import org.mtr.aurelia.road.preview.PreviewChoices.ShaderFaces;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The pages of the hub's Settings, the gear's pop-up: the Preview page first, then any another part of ATR adds
 * ({@link #add}). A page is a list of rows the hub draws in one style: choices, switches, a note, a measurement. Rows read
 * their values every frame and change them at once; the page is asked for its rows each time it is shown.
 */
public final class HubSettings {
    /** One row of a page. */
    public sealed interface Row permits Choice, Toggle, Note, Measure {
        String id();
    }

    /** A label and a few side-by-side options, one chosen. */
    public record Choice(String id, Text label, Text tip, List<Text> options, List<Text> optionTips, IntSupplier chosen, IntConsumer choose)
            implements Row {
        public Choice {
            options = List.copyOf(options);
            optionTips = List.copyOf(optionTips);
        }
    }

    /** A label and a switch. */
    public record Toggle(String id, Text label, Text tip, BooleanSupplier on, Consumer<Boolean> set) implements Row {
    }

    /** A muted note across the page, under the row it explains. */
    public record Note(String id, Text text) implements Row {
    }

    /** A label, what was measured ({@code status}, read every frame) and a button that measures again. */
    public record Measure(String id, Text label, Text tip, Supplier<Text> status, Text button, Runnable press) implements Row {
    }

    /** A page: its id, its name and icon in the list, its rows, and optionally what "Reset all" does. */
    public record Page(String id, Text title, HubIcon icon, Supplier<List<Row>> rows, Optional<Runnable> resetAll) {
        public Page {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(rows, "rows");
            Objects.requireNonNull(resetAll, "resetAll");
        }
    }

    /** The Preview page's id. */
    public static final String PREVIEW = "preview";

    private static final List<Page> ADDED = new CopyOnWriteArrayList<>();

    private HubSettings() {
    }

    /** Adds a page after the Preview page (once, from a client initialiser). */
    public static void add(Page page) {
        ADDED.removeIf(added -> added.id().equals(page.id()));
        ADDED.add(page);
    }

    /** Every page, Preview first. */
    public static List<Page> pages() {
        List<Page> pages = new ArrayList<>();
        pages.add(preview());
        pages.addAll(ADDED);
        return pages;
    }

    private static Page preview() {
        return new Page(PREVIEW, Text.translatable("stratos.hub.settings.preview"), HubIcon.EYE, HubSettings::previewRows, Optional.empty());
    }

    private static List<Row> previewRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Choice("faces", Text.translatable("stratos.hub.settings.faces"), Text.translatable("stratos.hub.settings.faces_tip"),
                List.of(Text.translatable("stratos.hub.settings.faces.shown"), Text.translatable("stratos.hub.settings.faces.outlines_only")),
                List.of(), () -> StratosPreview.outlinesOnly() ? 1 : 0, chosen -> StratosPreview.setOutlinesOnly(chosen == 1)));
        List<Text> drawn = new ArrayList<>();
        List<Text> drawnTips = new ArrayList<>();
        for (ShaderFaces faces : ShaderFaces.values()) {
            String key = "stratos.hub.settings.shader_faces." + faces.name().toLowerCase(Locale.ROOT);
            drawn.add(Text.translatable(key));
            drawnTips.add(Text.translatable(key + "_tip"));
        }
        rows.add(new Choice("shaderFaces", Text.translatable("stratos.hub.settings.shader_faces"), shaderFacesTip(), drawn, drawnTips,
                () -> StratosPreview.shaderFaces().ordinal(), chosen -> StratosPreview.setShaderFaces(ShaderFaces.values()[chosen])));
        rows.add(new Note("shaderFacesNote", Text.translatable("stratos.hub.settings.shader_faces_note")));
        rows.add(new Measure("drawTime", Text.translatable("stratos.hub.settings.draw_time"), Text.translatable("stratos.hub.settings.draw_time_tip"),
                HubSettings::drawTime, Text.translatable("stratos.hub.settings.measure"), StratosPreview::measure));
        return rows;
    }

    /** What the row does, and what it does with the pack in use now. */
    private static Text shaderFacesTip() {
        Text now;
        if (IrisCompat.shaderPackInUse()) {
            String pack = Objects.requireNonNullElse(IrisCompat.shaderPackName(), "?");
            boolean once = StratosPreview.shaderFaces().once(pack);
            now = Text.translatable("stratos.hub.settings.shader_faces.now", pack,
                    Text.translatable(once ? "stratos.hub.settings.shader_faces.once" : "stratos.hub.settings.shader_faces.twice"));
        } else {
            now = Text.translatable("stratos.hub.settings.shader_faces.no_pack");
        }
        return Text.translatable("stratos.hub.settings.shader_faces_tip").append("\n").append(now);
    }

    /** The draw time Measure found, or how far it has got. */
    private static Text drawTime() {
        if (StratosPreview.measuring()) {
            int frames = StratosPreview.measuredFrames();
            return frames == 0 ? Text.translatable("stratos.hub.settings.draw_time.waiting")
                    : Text.translatable("stratos.hub.settings.draw_time.measuring", frames);
        }
        return StratosPreview.measured().map(ms -> (Text) Text.translatable("stratos.hub.settings.draw_time.value",
                String.format(Locale.ROOT, "%.1f", ms[0]), String.format(Locale.ROOT, "%.1f", ms[1])))
                .orElse(Text.translatable("stratos.hub.settings.draw_time.none"));
    }
}
