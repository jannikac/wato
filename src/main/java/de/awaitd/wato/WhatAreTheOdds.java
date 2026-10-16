package de.awaitd.wato;

import com.mojang.serialization.JavaOps;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.dialog.AfterAction;
import net.minecraft.dialog.DialogActionButtonData;
import net.minecraft.dialog.DialogButtonData;
import net.minecraft.dialog.DialogCommonData;
import net.minecraft.dialog.action.DynamicRunCommandDialogAction;
import net.minecraft.dialog.action.ParsedTemplate;
import net.minecraft.dialog.action.SimpleDialogAction;
import net.minecraft.dialog.body.PlainMessageDialogBody;
import net.minecraft.dialog.input.TextInputControl;
import net.minecraft.dialog.type.Dialog;
import net.minecraft.dialog.type.DialogInput;
import net.minecraft.dialog.type.MultiActionDialog;
import net.minecraft.dialog.type.NoticeDialog;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public class WhatAreTheOdds implements ModInitializer {
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger("WhatAreTheOdds");
    // Map to store ongoing challenges
    private static final ArrayList<Challenge> challenges = new ArrayList<Challenge>() {
    };

    // Class to represent a challenge
    private static void startChallenge(ServerPlayerEntity sourcePlayer, ServerPlayerEntity targetPlayer, String challengeText) {
        Optional<Challenge> result = challenges.stream().filter(challenge -> challenge.targetPlayer == targetPlayer).findFirst();
        // remove challenge from array if it already exists. each player can only have one challange
        if (result.isPresent()) {
            challenges.remove(result.get());
        }
        Challenge challenge = new Challenge(sourcePlayer, targetPlayer, challengeText);
        challenges.add(challenge);

        // Send challenge message to players
        sourcePlayer.sendMessage(Title());
        sourcePlayer.sendMessage(RegularMessage(String.format("Du hast %s zu einer Odds herausgefordert. Dieser kann nun mit /odds respond <seine Odds> antworten", targetPlayer.getName().getString())));
        sourcePlayer.sendMessage(RegularMessage(String.format("\n%s", challengeText)));

        targetPlayer.sendMessage(Title());
        targetPlayer.sendMessage(RegularMessage(String.format("%s hat dich zu einer Odds herausgefordert.", sourcePlayer.getName().getString())));
        targetPlayer.sendMessage(RespondPrompt());
        targetPlayer.sendMessage(RegularMessage(String.format("\n%s", challengeText)));
    }

    private static Text RespondPrompt() {
        Text link = Text.literal("Hier").styled(style -> style
                .withColor(Formatting.GREEN)
                .withUnderline(true)
                .withClickEvent(new ClickEvent.RunCommand("/odds")));
        Text rest = Text.literal(" klicken oder /odds respond <deine Odds>").formatted(Formatting.GRAY);
        return Text.literal("").append(link).append(rest);
    }

    private static Text GuessPrompt(int limit) {
        Text intro = RegularMessage(String.format("\nSchreibe eine Zahl zwischen 1 und %s oder klicke ", limit));
        Text link = Text.literal("hier").styled(style -> style
                .withColor(Formatting.GREEN)
                .withUnderline(true)
                .withClickEvent(new ClickEvent.RunCommand("/odds")));
        return Text.literal("").append(intro).append(link);
    }

    private static void handleGuess(ServerPlayerEntity sender, Challenge challenge, int parsedInt) {
        if (parsedInt < 1 || parsedInt > challenge.limit) {
            sender.sendMessage(RegularMessage(String.format("Du musst eine Zahl zwischen 1 und %s", challenge.limit)));
            return;
        }
        sender.sendMessage(RegularMessage(String.format("Deine Odds: %s", parsedInt)));

        if (sender == challenge.targetPlayer) {
            challenge.targetPlayerGuess = OptionalInt.of(parsedInt);
            challenge.check();
        }
        if (sender == challenge.sourcePlayer) {
            challenge.sourcePlayerGuess = OptionalInt.of(parsedInt);
            challenge.check();
        }
    }

    private static OptionalInt parseLenientInt(String raw) {
        try {
            return OptionalInt.of(Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            try {
                return OptionalInt.of((int) Math.round(Double.parseDouble(raw)));
            } catch (NumberFormatException e2) {
                return OptionalInt.empty();
            }
        }
    }

    private static Text Title() {
        return Text.literal("==== Was sind die Odds? ====").formatted(Formatting.AQUA);
    }

    private static Text RegularMessage(String input) {
        return Text.literal(input).formatted(Formatting.GRAY);
    }

    // ---- Dialog UI ----

    private static ParsedTemplate commandTemplate(String raw) {
        return ParsedTemplate.CODEC.parse(JavaOps.INSTANCE, raw).getOrThrow();
    }

    private static DialogActionButtonData actionButton(String label, String commandTemplate) {
        return new DialogActionButtonData(
                new DialogButtonData(Text.literal(label), 200),
                Optional.of(new DynamicRunCommandDialogAction(commandTemplate(commandTemplate)))
        );
    }

    private static void showDialog(ServerPlayerEntity player, Dialog dialog) {
        player.openDialog(RegistryEntry.of(dialog));
    }

    private static void showNotice(ServerPlayerEntity player, String message) {
        DialogCommonData common = new DialogCommonData(
                Title(),
                Optional.empty(),
                true,
                false,
                AfterAction.CLOSE,
                List.of(new PlainMessageDialogBody(RegularMessage(message), 200)),
                List.of()
        );
        showDialog(player, new NoticeDialog(common, NoticeDialog.OK_BUTTON));
    }

    private static void showNewChallengeDialog(ServerPlayerEntity player, List<ServerPlayerEntity> onlinePlayers) {
        List<ServerPlayerEntity> others = onlinePlayers.stream().filter(p -> p != player).toList();
        if (others.isEmpty()) {
            showNotice(player, "Es ist kein anderer Spieler online, den du herausfordern könntest.");
            return;
        }

        List<DialogActionButtonData> targetButtons = others.stream()
                .map(other -> new DialogActionButtonData(
                        new DialogButtonData(other.getName(), 200),
                        Optional.of(new SimpleDialogAction(new ClickEvent.ShowDialog(RegistryEntry.of(buildChallengeMessageDialog(other)))))
                ))
                .toList();

        DialogActionButtonData cancelButton = new DialogActionButtonData(new DialogButtonData(Text.literal("Abbrechen"), 200), Optional.empty());

        DialogCommonData common = new DialogCommonData(
                Title(),
                Optional.empty(),
                true,
                false,
                AfterAction.CLOSE,
                List.of(new PlainMessageDialogBody(RegularMessage("Wähle den Spieler, den du herausfordern möchtest."), 200)),
                List.of()
        );
        showDialog(player, new MultiActionDialog(common, targetButtons, Optional.of(cancelButton), 1));
    }

    private static Dialog buildChallengeMessageDialog(ServerPlayerEntity target) {
        List<DialogInput> inputs = List.of(
                new DialogInput("text", new TextInputControl(200, Text.literal("Deine Herausforderung"), true, "", Integer.MAX_VALUE,
                        Optional.of(new TextInputControl.Multiline(Optional.of(3), Optional.empty()))))
        );

        DialogActionButtonData confirmButton = actionButton("Bestätigen", String.format("/odds challenge %s \"$(text)\"", target.getName().getString()));
        DialogActionButtonData cancelButton = new DialogActionButtonData(new DialogButtonData(Text.literal("Abbrechen"), 200), Optional.empty());

        DialogCommonData common = new DialogCommonData(
                Title(),
                Optional.empty(),
                true,
                false,
                AfterAction.CLOSE,
                List.of(new PlainMessageDialogBody(RegularMessage(String.format("Schreib deine Odds-Nachricht an %s.", target.getName().getString())), 200)),
                inputs
        );
        return new MultiActionDialog(common, List.of(cancelButton, confirmButton), Optional.empty(), 2);
    }

    private static void showAcceptDialog(ServerPlayerEntity player, Challenge challenge) {
        List<DialogInput> inputs = List.of(
                new DialogInput("number", new TextInputControl(200, Text.literal("Odds (Obergrenze)"), true, "", 9, Optional.empty()))
        );
        DialogCommonData common = new DialogCommonData(
                Title(),
                Optional.empty(),
                true,
                false,
                AfterAction.CLOSE,
                List.of(new PlainMessageDialogBody(RegularMessage(String.format("%s hat dich herausgefordert:\n%s", challenge.sourcePlayer.getName().getString(), challenge.challengeText)), 200)),
                inputs
        );
        DialogActionButtonData confirmButton = actionButton("Odds festlegen", "/odds respond $(number)");
        DialogActionButtonData cancelButton = new DialogActionButtonData(new DialogButtonData(Text.literal("Abbrechen"), 200), Optional.empty());
        showDialog(player, new MultiActionDialog(common, List.of(cancelButton, confirmButton), Optional.empty(), 2));
    }

    private static void showGuessDialog(ServerPlayerEntity player, Challenge challenge) {
        List<DialogInput> inputs = List.of(
                new DialogInput("number", new TextInputControl(200, Text.literal("Deine Zahl"), true, "", 9, Optional.empty()))
        );
        DialogCommonData common = new DialogCommonData(
                Title(),
                Optional.empty(),
                true,
                false,
                AfterAction.CLOSE,
                List.of(new PlainMessageDialogBody(RegularMessage(String.format("Wähle eine Zahl zwischen 1 und %s.\n%s", challenge.limit, challenge.challengeText)), 200)),
                inputs
        );
        showDialog(player, new NoticeDialog(common, actionButton("Zahl abschicken", "/odds guess $(number)")));
    }

    private static void openOddsDialog(ServerPlayerEntity player, List<ServerPlayerEntity> onlinePlayers) {
        Optional<Challenge> asTargetPending = challenges.stream()
                .filter(c -> c.targetPlayer == player && !c.challengeActive)
                .findFirst();
        if (asTargetPending.isPresent()) {
            showAcceptDialog(player, asTargetPending.get());
            return;
        }

        Optional<Challenge> asSourcePending = challenges.stream()
                .filter(c -> c.sourcePlayer == player && !c.challengeActive)
                .findFirst();
        if (asSourcePending.isPresent()) {
            showNotice(player, String.format("Warte darauf, dass %s die Odds festlegt.", asSourcePending.get().targetPlayer.getName().getString()));
            return;
        }

        Optional<Challenge> active = challenges.stream()
                .filter(c -> c.challengeActive && (c.sourcePlayer == player || c.targetPlayer == player))
                .findFirst();
        if (active.isPresent()) {
            Challenge challenge = active.get();
            boolean alreadyGuessed = (challenge.sourcePlayer == player && challenge.sourcePlayerGuess.isPresent())
                    || (challenge.targetPlayer == player && challenge.targetPlayerGuess.isPresent());
            if (alreadyGuessed) {
                showNotice(player, "Warte auf die Zahl des anderen Spielers.");
            } else {
                showGuessDialog(player, challenge);
            }
            return;
        }

        showNewChallengeDialog(player, onlinePlayers);
    }

    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.
        // Register the "/odds" command
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            Optional<Challenge> result = challenges.stream().filter(challenge -> challenge.challengeActive && (sender == challenge.sourcePlayer || sender == challenge.targetPlayer)).findFirst();
            if (result.isPresent()) {
                try {
                    int parsedInt = Integer.parseInt(message.getContent().getString());
                    handleGuess(sender, result.get(), parsedInt);
                    return false;
                } catch (NumberFormatException e) {
                    sender.sendMessage(Text.literal(String.format("Du musst eine Zahl zwischen 1 und %s eingeben", result.get().limit)).formatted(Formatting.RED));
                }

            }
            return true;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) ->
                dispatcher.register(CommandManager.literal("odds")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) {
                                context.getSource().sendError(Text.literal("Dieser Befehl kann nur von Spielern verwendet werden"));
                                return 0;
                            }
                            openOddsDialog(player, context.getSource().getServer().getPlayerManager().getPlayerList());
                            return 1;
                        })
                        .then(CommandManager.literal("challenge").then(CommandManager.argument("target", EntityArgumentType.player())
                                .then(CommandManager.argument("text", StringArgumentType.string())
                                        .executes(context -> {
                                            ServerPlayerEntity sourcePlayer = context.getSource().getPlayer();
                                            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "target");

                                            if (sourcePlayer != null && targetPlayer != null && !sourcePlayer.equals(targetPlayer)) {
                                                // Start a new challenge
                                                startChallenge(sourcePlayer, targetPlayer, StringArgumentType.getString(context, "text"));
                                            } else {
                                                context.getSource().sendError(Text.literal("Ungültige Spielerauswahl. Du kannst dich nicht selbst herausfordern"));
                                            }

                                            return 1;
                                        }))))
                        .then(CommandManager.literal("respond").then(CommandManager.argument("number", StringArgumentType.word())
                                        .executes(context -> {
                                            // search for the challenge
                                            Optional<Challenge> result = challenges.stream().filter(challenge -> challenge.targetPlayer == context.getSource().getPlayer()).findFirst();
                                            if (result.isEmpty()) {
                                                context.getSource().sendError(Text.literal("Du hast keine aktiven Odds"));
                                                return 0;
                                            }
                                            OptionalInt parsed = parseLenientInt(StringArgumentType.getString(context, "number"));
                                            if (parsed.isEmpty()) {
                                                context.getSource().sendError(Text.literal("Du musst eine Zahl eingeben"));
                                                return 0;
                                            }
                                            if (parsed.getAsInt() < 2) {
                                                context.getSource().sendError(Text.literal("Die Odds müssen mindestens 2 sein"));
                                                return 0;
                                            }
                                            Challenge res = result.get();
                                            int intArgument = parsed.getAsInt();
                                            res.setLimit(intArgument);
                                            res.sourcePlayer.sendMessage(Title());
                                            res.sourcePlayer.sendMessage(RegularMessage(String.format("%s hat die Odds akzeptiert", res.targetPlayer.getName().getString())));
                                            res.sourcePlayer.sendMessage(GuessPrompt(res.limit));

                                            res.targetPlayer.sendMessage(Title());
                                            res.targetPlayer.sendMessage(RegularMessage("Du hast die Odds akzeptiert"));
                                            res.targetPlayer.sendMessage(GuessPrompt(intArgument));

                                            return 1;
                                        }))

                                .executes(context -> {
                                    context.getSource().sendError(Text.literal("Richtige Benutzung:"));
                                    context.getSource().sendError(Text.literal("/odds challenge <Spieler> <Text der Odds>"));
                                    context.getSource().sendError(Text.literal("/odds respond <deine Odds>"));
                                    return 0;
                                })
                        )
                        .then(CommandManager.literal("guess").then(CommandManager.argument("number", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayerEntity sender = context.getSource().getPlayer();
                                    if (sender == null) {
                                        context.getSource().sendError(Text.literal("Dieser Befehl kann nur von Spielern verwendet werden"));
                                        return 0;
                                    }
                                    Optional<Challenge> result = challenges.stream().filter(challenge -> challenge.challengeActive && (sender == challenge.sourcePlayer || sender == challenge.targetPlayer)).findFirst();
                                    if (result.isEmpty()) {
                                        context.getSource().sendError(Text.literal("Du hast keine aktiven Odds"));
                                        return 0;
                                    }
                                    OptionalInt parsed = parseLenientInt(StringArgumentType.getString(context, "number"));
                                    if (parsed.isEmpty()) {
                                        context.getSource().sendError(Text.literal("Du musst eine Zahl eingeben"));
                                        return 0;
                                    }
                                    handleGuess(sender, result.get(), parsed.getAsInt());
                                    return 1;
                                })))
                ));

        LOGGER.info("What are the odds? wurde erfolgreich geladen");
    }

    private static class Challenge {
        private final ServerPlayerEntity sourcePlayer;
        private final ServerPlayerEntity targetPlayer;
        private final String challengeText;
        private OptionalInt sourcePlayerGuess;
        private OptionalInt targetPlayerGuess;
        private int limit;
        private boolean challengeActive;

        public Challenge(ServerPlayerEntity sourcePlayer, ServerPlayerEntity targetPlayer, String challengeText) {
            this.sourcePlayer = sourcePlayer;
            this.targetPlayer = targetPlayer;
            this.challengeText = challengeText;
            this.challengeActive = false;
            this.sourcePlayerGuess = OptionalInt.empty();
            this.targetPlayerGuess = OptionalInt.empty();
        }

        public void setLimit(int i) {
            this.challengeActive = true;
            this.limit = i;
        }

        public void check() {
            if (this.sourcePlayerGuess.isPresent() && this.targetPlayerGuess.isPresent()) {
                if (this.sourcePlayerGuess.getAsInt() == this.targetPlayerGuess.getAsInt()) {
                    this.sourcePlayer.sendMessage(Title());
                    this.sourcePlayer.sendMessage(Text.literal(String.format("%s muss die Odds machen!", this.targetPlayer.getName().getString())).formatted(Formatting.GREEN));
                    this.sourcePlayer.sendMessage(RegularMessage(String.format("\n%s", this.challengeText)));

                    this.targetPlayer.sendMessage(Title());
                    this.targetPlayer.sendMessage(Text.literal("Du musst die Odds machen!").formatted(Formatting.RED));
                    this.targetPlayer.sendMessage(RegularMessage(String.format("\n%s", this.challengeText)));
                    this.challengeActive = false;

                } else if (this.sourcePlayerGuess.getAsInt() + this.targetPlayerGuess.getAsInt() == limit) {
                    this.sourcePlayer.sendMessage(Title());
                    this.sourcePlayer.sendMessage(Text.literal("Beide müssen die Odds machen").formatted(Formatting.RED));
                    this.sourcePlayer.sendMessage(Text.literal(String.format("\n%s", this.challengeText)).formatted(Formatting.GRAY));

                    this.targetPlayer.sendMessage(Title());
                    this.targetPlayer.sendMessage(Text.literal("Beide müssen die Odds machen").formatted(Formatting.RED));
                    this.targetPlayer.sendMessage(Text.literal(String.format("\n%s", this.challengeText)).formatted(Formatting.GRAY));
                    this.challengeActive = false;

                } else if ((this.sourcePlayerGuess.getAsInt() == 1 && this.targetPlayerGuess.getAsInt() == limit)
                        || (this.targetPlayerGuess.getAsInt() == 1 && this.sourcePlayerGuess.getAsInt() == limit)) {
                    this.targetPlayer.sendMessage(Title());
                    this.targetPlayer.sendMessage(Text.literal(String.format("%s muss die Odds machen!", this.sourcePlayer.getName().getString())).formatted(Formatting.GREEN));
                    this.targetPlayer.sendMessage(RegularMessage(String.format("\n%s", this.challengeText)));

                    this.sourcePlayer.sendMessage(Title());
                    this.sourcePlayer.sendMessage(Text.literal("Du hast die Odds verloren. Du musst die Odds machen!").formatted(Formatting.RED));
                    this.sourcePlayer.sendMessage(RegularMessage(String.format("\n%s", this.challengeText)));
                    this.challengeActive = false;

                } else {
                    this.sourcePlayer.sendMessage(Title());
                    this.sourcePlayer.sendMessage(Text.literal("Nichts passiert").formatted(Formatting.YELLOW));
                    this.sourcePlayer.sendMessage(RegularMessage(String.format("\nDeine Odds waren: %s", this.sourcePlayerGuess.getAsInt())));
                    this.sourcePlayer.sendMessage(RegularMessage(String.format("%s Odds waren: %s", this.targetPlayer.getName().getString(), this.targetPlayerGuess.getAsInt())));

                    this.targetPlayer.sendMessage(Title());
                    this.targetPlayer.sendMessage(Text.literal("Nichts passiert").formatted(Formatting.YELLOW));
                    this.targetPlayer.sendMessage(RegularMessage(String.format("\nDeine Odds waren: %s", this.targetPlayerGuess.getAsInt())));
                    this.targetPlayer.sendMessage(RegularMessage(String.format("%s Odds waren: %s", this.sourcePlayer.getName().getString(), this.sourcePlayerGuess.getAsInt())));
                    this.challengeActive = false;
                }

                challenges.remove(this);
            }
        }
    }
}
