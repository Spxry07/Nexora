package net.spxry.nexora.edit;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

public record Binding<T>(Function<T, String> getter, BiConsumer<T, String> setter) {

    public static <T> Binding<T> text(Function<T, String> getter, BiConsumer<T, String> setter) {
        return new Binding<>(getter, setter);
    }

    public static <T> Binding<T> number(ToDoubleFunction<T> getter, ObjDoubleConsumer<T> setter) {
        return new Binding<>(t -> Schema.format(getter.applyAsDouble(t)), (t, v) -> setter.accept(t, Double.parseDouble(v)));
    }

    public static <T> Binding<T> integer(ToIntFunction<T> getter, ObjIntConsumer<T> setter) {
        return new Binding<>(t -> String.valueOf(getter.applyAsInt(t)), (t, v) -> setter.accept(t, (int) Math.round(Double.parseDouble(v))));
    }

    public static <T> Binding<T> bool(Predicate<T> getter, BiConsumer<T, Boolean> setter) {
        return new Binding<>(t -> String.valueOf(getter.test(t)), (t, v) -> setter.accept(t, Boolean.parseBoolean(v)));
    }
}
