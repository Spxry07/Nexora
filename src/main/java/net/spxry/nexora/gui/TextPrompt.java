package net.spxry.nexora.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class TextPrompt {
    record Spec(Component title, Component body, Component label, String initial, int maxLength, boolean multiline) {}

    private static final String INPUT_KEY = "value";
    private static final String CONFIRM_PATH = "prompt.confirm";
    private static final String CANCEL_PATH = "prompt.cancel";

    private TextPrompt() {}

    static void open(GuiManager gui, Player player, Spec spec, Consumer<String> submit, Runnable cancel) {
        var config = gui.plugin().getConfig();
        var initial = spec.initial() == null ? "" : spec.initial();
        var input = DialogInput.text(INPUT_KEY, spec.label())
            .initial(initial)
            .maxLength(Math.max(spec.maxLength(), initial.length()))
            .width(positive(config.getInt("dialogs.input-width")));
        if (spec.multiline()) {
            input = input.multiline(TextDialogInput.MultilineOptions.create(
                positive(config.getInt("dialogs.multiline-max-lines")),
                positive(config.getInt("dialogs.multiline-height"))));
        }
        var options = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(positive(config.getInt("dialogs.callback-lifetime-minutes"))))
            .build();
        var width = positive(config.getInt("dialogs.button-width"));
        var confirm = ActionButton.builder(gui.config().text(GuiConfig.COMMON, CONFIRM_PATH, Map.of()))
            .width(width)
            .action(DialogAction.customClick((view, audience) -> {
                var text = view == null ? null : view.getText(INPUT_KEY);
                gui.hop(player, () -> submit.accept(text));
            }, options))
            .build();
        var back = ActionButton.builder(gui.config().text(GuiConfig.COMMON, CANCEL_PATH, Map.of()))
            .width(width)
            .action(DialogAction.customClick((view, audience) -> gui.hop(player, cancel), options))
            .build();
        List<DialogBody> body = new ArrayList<>();
        if (spec.body() != null) body.add(DialogBody.plainMessage(spec.body()));
        List<DialogInput> inputs = List.of(input.build());
        var base = DialogBase.builder(spec.title()).body(body).inputs(inputs).build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(DialogType.confirmation(confirm, back))));
    }

    private static int positive(int value) {
        return Math.max(1, value);
    }
}
