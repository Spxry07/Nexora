package net.spxry.nexora.object;

import net.spxry.nexora.edit.Binding;
import net.spxry.nexora.edit.Schema;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HoloLine {
    static final String TEXT = "TEXT";
    static final String ITEM = "ITEM";
    static final String BLOCK = "BLOCK";

    public static final Map<String, Binding<HoloLine>> BINDINGS = createBindings();

    String type = TEXT;
    String text = "";
    String material = "";
    int frameInterval = 1;
    String effect = "NONE";
    int effectSpeed = 1;
    String colorA = "#FFFFFF";
    String colorB = "#FFFFFF";
    int scrollWidth = 16;
    double scale = 1;
    double offsetY;
    String billboard = "CENTER";
    String itemTransform = "FIXED";
    boolean backgroundDefault = true;
    String background = "#40000000";
    boolean shadow = true;
    boolean seeThrough;
    String alignment = "CENTER";
    int lineWidth = 200;
    int opacity = 255;
    boolean glow;
    String glowColor = "#FFFFFF";
    int brightness = -1;
    private volatile String itemData;

    public HoloLine() {}

    private static Map<String, Binding<HoloLine>> createBindings() {
        Map<String, Binding<HoloLine>> map = new LinkedHashMap<>();
        map.put("type", Binding.text(l -> l.type, (l, v) -> l.type = v));
        map.put("text", Binding.text(l -> l.text, (l, v) -> l.text = v));
        map.put("material", Binding.text(l -> l.material, (l, v) -> l.material = v));
        map.put("frame-interval", Binding.integer(l -> l.frameInterval, (l, v) -> l.frameInterval = v));
        map.put("effect", Binding.text(l -> l.effect, (l, v) -> l.effect = v));
        map.put("effect-speed", Binding.integer(l -> l.effectSpeed, (l, v) -> l.effectSpeed = v));
        map.put("color-a", Binding.text(l -> l.colorA, (l, v) -> l.colorA = v));
        map.put("color-b", Binding.text(l -> l.colorB, (l, v) -> l.colorB = v));
        map.put("scroll-width", Binding.integer(l -> l.scrollWidth, (l, v) -> l.scrollWidth = v));
        map.put("scale", Binding.number(l -> l.scale, (l, v) -> l.scale = v));
        map.put("offset-y", Binding.number(l -> l.offsetY, (l, v) -> l.offsetY = v));
        map.put("billboard", Binding.text(l -> l.billboard, (l, v) -> l.billboard = v));
        map.put("item-transform", Binding.text(l -> l.itemTransform, (l, v) -> l.itemTransform = v));
        map.put("background-default", Binding.bool(l -> l.backgroundDefault, (l, v) -> l.backgroundDefault = v));
        map.put("background", Binding.text(l -> l.background, (l, v) -> l.background = v));
        map.put("shadow", Binding.bool(l -> l.shadow, (l, v) -> l.shadow = v));
        map.put("see-through", Binding.bool(l -> l.seeThrough, (l, v) -> l.seeThrough = v));
        map.put("alignment", Binding.text(l -> l.alignment, (l, v) -> l.alignment = v));
        map.put("line-width", Binding.integer(l -> l.lineWidth, (l, v) -> l.lineWidth = v));
        map.put("opacity", Binding.integer(l -> l.opacity, (l, v) -> l.opacity = v));
        map.put("glow", Binding.bool(l -> l.glow, (l, v) -> l.glow = v));
        map.put("glow-color", Binding.text(l -> l.glowColor, (l, v) -> l.glowColor = v));
        map.put("brightness", Binding.integer(l -> l.brightness, (l, v) -> l.brightness = v));
        return Collections.unmodifiableMap(map);
    }

    public Map<String, String> values(Schema schema) {
        return schema.read(BINDINGS, this);
    }

    public List<String> apply(Schema schema, Map<String, String> values) {
        return schema.apply(NexoraObject.LINE, BINDINGS, this, values);
    }

    public HoloLine copy() {
        var copy = new HoloLine();
        BINDINGS.values().forEach(binding -> binding.setter().accept(copy, binding.getter().apply(this)));
        copy.itemData = itemData;
        return copy;
    }

    public String type() { return type; }

    public String text() { return text; }

    public String itemData() { return itemData; }

    public void itemData(String base64OrNull) { itemData = base64OrNull == null || base64OrNull.isEmpty() ? null : base64OrNull; }
}
