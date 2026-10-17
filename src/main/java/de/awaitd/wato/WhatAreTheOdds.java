package de.awaitd.wato;

import com.mojang.serialization.JavaOps;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
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
    private static final ArrayList<Challenge> challenges = new ArrayList<>();

    // Class to represent a challenge
    private static void startChallenge(ServerPlayer sourcePlayer, ServerPlayer targetPlayer, String challengeText) {
        // each player can only have one incoming challenge; replacing an existing one notifies its challenger
        challenges.stream().filter(challenge -> challenge.targetPlayer == targetPlayer).findFirst()
                .ifPresent(oldChallenge -> cancelChallenge(oldChallenge, targetPlayer,
                        String.format("%s wurde von einem anderen Spieler herausgefordert. Deine Herausforderung wurde abgebrochen.", targetPlayer.getName().getString())));
        Challenge challenge = new Challenge(sourcePlayer, targetPlayer, challengeText);
        challenges.add(challenge);

        // Send challenge message to players
        sourcePlayer.sendSystemMessage(title());
        sourcePlayer.sendSystemMessage(regularMessage(String.format("Du hast %s zu einer Odds herausgefordert. Dieser kann nun mit /odds respond <seine Odds> antworten", targetPlayer.getName().getString())));
        sourcePlayer.sendSystemMessage(regularMessage(String.format("\n%s", challengeText)));

        targetPlayer.sendSystemMessage(title());
        targetPlayer.sendSystemMessage(regularMessage(String.format("%s hat dich zu einer Odds herausgefordert.", sourcePlayer.getName().getString())));
        targetPlayer.sendSystemMessage(respondPrompt());
        targetPlayer.sendSystemMessage(regularMessage(String.format("\n%s", challengeText)));
    }

    private static Component respondPrompt() {
        Component link = Component.literal("Hier").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand("/odds")));
        Component rest = Component.literal(" klicken oder /odds respond <deine Odds>").withStyle(ChatFormatting.GRAY);
        return Component.literal("").append(link).append(rest);
    }

    private static Component guessPrompt(int limit) {
        Component intro = regularMessage(String.format("\nSchreibe eine Zahl zwischen 1 und %s oder klicke ", limit));
        Component link = Component.literal("hier").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand("/odds")));
        return Component.literal("").append(intro).append(link);
    }

    private static void handleGuess(ServerPlayer sender, Challenge challenge, int parsedInt) {
        if (parsedInt < 1 || parsedInt > challenge.limit) {
            sender.sendSystemMessage(regularMessage(String.format("Du musst eine Zahl zwischen 1 und %s", challenge.limit)));
            return;
        }
        sender.sendSystemMessage(regularMessage(String.format("Deine Odds: %s", parsedInt)));

        if (sender == challenge.targetPlayer) {
            challenge.targetPlayerGuess = OptionalInt.of(parsedInt);
            challenge.check();
        }
        if (sender == challenge.sourcePlayer) {
            challenge.sourcePlayerGuess = OptionalInt.of(parsedInt);
            challenge.check();
        }
    }

    private static void cancelChallenge(Challenge challenge, ServerPlayer canceller, String otherMessage) {
        challenges.remove(challenge);
        ServerPlayer other = challenge.sourcePlayer == canceller ? challenge.targetPlayer : challenge.sourcePlayer;
        other.sendSystemMessage(title());
        other.sendSystemMessage(regularMessage(otherMessage));
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

    private static Component title() {
        return Component.literal("==== Was sind die Odds? ====").withStyle(ChatFormatting.AQUA);
    }

    private static Component regularMessage(String input) {
        return Component.literal(input).withStyle(ChatFormatting.GRAY);
    }

    // ---- Dialog UI ----

    private static ParsedTemplate commandTemplate(String raw) {
        return ParsedTemplate.CODEC.parse(JavaOps.INSTANCE, raw).getOrThrow();
    }

    private static ActionButton actionButton(String label, String commandTemplate) {
        return new ActionButton(
                new CommonButtonData(Component.literal(label), 200),
                Optional.of(new CommandTemplate(commandTemplate(commandTemplate)))
        );
    }

    private static ActionButton cancelButton() {
        return new ActionButton(new CommonButtonData(Component.literal("Abbrechen"), 200), Optional.empty());
    }

    private static CommonDialogData commonData(String bodyText, List<Input> inputs) {
        return new CommonDialogData(
                title(),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                List.of(new PlainMessage(regularMessage(bodyText), 200)),
                inputs
        );
    }

    private static CommonDialogData commonData(String bodyText) {
        return commonData(bodyText, List.of());
    }

    private static void showDialog(ServerPlayer player, Dialog dialog) {
        player.openDialog(Holder.direct(dialog));
    }

    private static void showNotice(ServerPlayer player, String message) {
        showDialog(player, new NoticeDialog(commonData(message), NoticeDialog.DEFAULT_ACTION));
    }

    private static void showNewChallengeDialog(ServerPlayer player, List<ServerPlayer> onlinePlayers) {
        List<ServerPlayer> others = onlinePlayers.stream().filter(p -> p != player).toList();
        if (others.isEmpty()) {
            showNotice(player, "Es ist kein anderer Spieler online, den du herausfordern könntest.");
            return;
        }

        List<ActionButton> targetButtons = others.stream()
                .map(other -> new ActionButton(
                        new CommonButtonData(other.getName(), 200),
                        Optional.of(new StaticAction(new ClickEvent.ShowDialog(Holder.direct(buildChallengeMessageDialog(other)))))
                ))
                .toList();

        CommonDialogData common = commonData("Wähle den Spieler, den du herausfordern möchtest.");
        showDialog(player, new MultiActionDialog(common, targetButtons, Optional.of(cancelButton()), 1));
    }

    private static Dialog buildChallengeMessageDialog(ServerPlayer target) {
        List<Input> inputs = List.of(
                new Input("text", new TextInput(200, Component.literal("Deine Herausforderung"), true, "", Integer.MAX_VALUE,
                        Optional.of(new TextInput.MultilineOptions(Optional.of(3), Optional.empty()))))
        );

        ActionButton confirmButton = actionButton("Bestätigen", String.format("/odds challenge %s \"$(text)\"", target.getName().getString()));
        CommonDialogData common = commonData(String.format("Schreib deine Odds-Nachricht an %s.", target.getName().getString()), inputs);
        return new MultiActionDialog(common, List.of(cancelButton(), confirmButton), Optional.empty(), 2);
    }

    private static void showAcceptDialog(ServerPlayer player, Challenge challenge) {
        List<Input> inputs = List.of(
                new Input("number", new TextInput(200, Component.literal("Odds (Obergrenze)"), true, "", 9, Optional.empty()))
        );
        CommonDialogData common = commonData(String.format("%s hat dich herausgefordert:\n%s", challenge.sourcePlayer.getName().getString(), challenge.challengeText), inputs);
        ActionButton confirmButton = actionButton("Odds festlegen", "/odds respond $(number)");
        showDialog(player, new MultiActionDialog(common, List.of(cancelButton(), confirmButton), Optional.empty(), 2));
    }

    private static void showGuessDialog(ServerPlayer player, Challenge challenge) {
        List<Input> inputs = List.of(
                new Input("number", new TextInput(200, Component.literal("Deine Zahl"), true, "", 9, Optional.empty()))
        );
        CommonDialogData common = commonData(String.format("Wähle eine Zahl zwischen 1 und %s.\n%s", challenge.limit, challenge.challengeText), inputs);
        ActionButton confirmButton = actionButton("Zahl abschicken", "/odds guess $(number)");
        showDialog(player, new MultiActionDialog(common, List.of(cancelButton(), confirmButton), Optional.empty(), 2));
    }

    private static void openOddsDialog(ServerPlayer player, List<ServerPlayer> onlinePlayers) {
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
                    int parsedInt = Integer.parseInt(message.signedContent());
                    handleGuess(sender, result.get(), parsedInt);
                    return false;
                } catch (NumberFormatException e) {
                    sender.sendSystemMessage(Component.literal(String.format("Du musst eine Zahl zwischen 1 und %s eingeben", result.get().limit)).withStyle(ChatFormatting.RED));
                }

            }
            return true;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            challenges.stream()
                    .filter(challenge -> challenge.sourcePlayer == player || challenge.targetPlayer == player)
                    .findFirst()
                    .ifPresent(challenge -> cancelChallenge(challenge, player, String.format("%s hat den Server verlassen. Die Herausforderung wurde abgebrochen.", player.getName().getString())));
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) ->
                dispatcher.register(Commands.literal("odds")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player == null) {
                                context.getSource().sendFailure(Component.literal("Dieser Befehl kann nur von Spielern verwendet werden"));
                                return 0;
                            }
                            openOddsDialog(player, context.getSource().getServer().getPlayerList().getPlayers());
                            return 1;
                        })
                        .then(Commands.literal("challenge").then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("text", StringArgumentType.string())
                                        .executes(context -> {
                                            ServerPlayer sourcePlayer = context.getSource().getPlayer();
                                            ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "target");

                                            if (sourcePlayer != null && targetPlayer != null && !sourcePlayer.equals(targetPlayer)) {
                                                // Start a new challenge
                                                startChallenge(sourcePlayer, targetPlayer, StringArgumentType.getString(context, "text"));
                                            } else {
                                                context.getSource().sendFailure(Component.literal("Ungültige Spielerauswahl. Du kannst dich nicht selbst herausfordern"));
                                            }

                                            return 1;
                                        }))))
                        .then(Commands.literal("respond").then(Commands.argument("number", StringArgumentType.word())
                                        .executes(context -> {
                                            // search for the challenge
                                            Optional<Challenge> result = challenges.stream().filter(challenge -> challenge.targetPlayer == context.getSource().getPlayer() && !challenge.challengeActive).findFirst();
                                            if (result.isEmpty()) {
                                                context.getSource().sendFailure(Component.literal("Du hast keine aktiven Odds"));
                                                return 0;
                                            }
                                            OptionalInt parsed = parseLenientInt(StringArgumentType.getString(context, "number"));
                                            if (parsed.isEmpty()) {
                                                context.getSource().sendFailure(Component.literal("Du musst eine Zahl eingeben"));
                                                return 0;
                                            }
                                            if (parsed.getAsInt() < 2) {
                                                context.getSource().sendFailure(Component.literal("Die Odds müssen mindestens 2 sein"));
                                                return 0;
                                            }
                                            Challenge res = result.get();
                                            int intArgument = parsed.getAsInt();
                                            res.setLimit(intArgument);
                                            res.sourcePlayer.sendSystemMessage(title());
                                            res.sourcePlayer.sendSystemMessage(regularMessage(String.format("%s hat die Odds akzeptiert", res.targetPlayer.getName().getString())));
                                            res.sourcePlayer.sendSystemMessage(guessPrompt(res.limit));

                                            res.targetPlayer.sendSystemMessage(title());
                                            res.targetPlayer.sendSystemMessage(regularMessage("Du hast die Odds akzeptiert"));
                                            res.targetPlayer.sendSystemMessage(guessPrompt(intArgument));

                                            return 1;
                                        }))

                                .executes(context -> {
                                    context.getSource().sendFailure(Component.literal("Richtige Benutzung:"));
                                    context.getSource().sendFailure(Component.literal("/odds challenge <Spieler> <Text der Odds>"));
                                    context.getSource().sendFailure(Component.literal("/odds respond <deine Odds>"));
                                    return 0;
                                })
                        )
                        .then(Commands.literal("guess").then(Commands.argument("number", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayer sender = context.getSource().getPlayer();
                                    if (sender == null) {
                                        context.getSource().sendFailure(Component.literal("Dieser Befehl kann nur von Spielern verwendet werden"));
                                        return 0;
                                    }
                                    Optional<Challenge> result = challenges.stream().filter(challenge -> challenge.challengeActive && (sender == challenge.sourcePlayer || sender == challenge.targetPlayer)).findFirst();
                                    if (result.isEmpty()) {
                                        context.getSource().sendFailure(Component.literal("Du hast keine aktiven Odds"));
                                        return 0;
                                    }
                                    OptionalInt parsed = parseLenientInt(StringArgumentType.getString(context, "number"));
                                    if (parsed.isEmpty()) {
                                        context.getSource().sendFailure(Component.literal("Du musst eine Zahl eingeben"));
                                        return 0;
                                    }
                                    handleGuess(sender, result.get(), parsed.getAsInt());
                                    return 1;
                                })))
                        .then(Commands.literal("cancel").executes(context -> {
                            ServerPlayer sender = context.getSource().getPlayer();
                            if (sender == null) {
                                context.getSource().sendFailure(Component.literal("Dieser Befehl kann nur von Spielern verwendet werden"));
                                return 0;
                            }
                            Optional<Challenge> result = challenges.stream().filter(challenge -> challenge.sourcePlayer == sender || challenge.targetPlayer == sender).findFirst();
                            if (result.isEmpty()) {
                                context.getSource().sendFailure(Component.literal("Du hast keine aktiven Odds"));
                                return 0;
                            }
                            cancelChallenge(result.get(), sender, String.format("%s hat die Herausforderung abgebrochen.", sender.getName().getString()));
                            sender.sendSystemMessage(regularMessage("Du hast die Herausforderung abgebrochen."));
                            return 1;
                        }))
                ));

        LOGGER.info("What are the odds? wurde erfolgreich geladen");
    }

    private static class Challenge {
        private final ServerPlayer sourcePlayer;
        private final ServerPlayer targetPlayer;
        private final String challengeText;
        private OptionalInt sourcePlayerGuess;
        private OptionalInt targetPlayerGuess;
        private int limit;
        private boolean challengeActive;

        public Challenge(ServerPlayer sourcePlayer, ServerPlayer targetPlayer, String challengeText) {
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
                    this.sourcePlayer.sendSystemMessage(title());
                    this.sourcePlayer.sendSystemMessage(Component.literal(String.format("%s muss die Odds machen!", this.targetPlayer.getName().getString())).withStyle(ChatFormatting.GREEN));
                    this.sourcePlayer.sendSystemMessage(regularMessage(String.format("\n%s", this.challengeText)));

                    this.targetPlayer.sendSystemMessage(title());
                    this.targetPlayer.sendSystemMessage(Component.literal("Du musst die Odds machen!").withStyle(ChatFormatting.RED));
                    this.targetPlayer.sendSystemMessage(regularMessage(String.format("\n%s", this.challengeText)));
                    this.challengeActive = false;

                } else if (this.sourcePlayerGuess.getAsInt() + this.targetPlayerGuess.getAsInt() == limit) {
                    this.sourcePlayer.sendSystemMessage(title());
                    this.sourcePlayer.sendSystemMessage(Component.literal("Beide müssen die Odds machen").withStyle(ChatFormatting.RED));
                    this.sourcePlayer.sendSystemMessage(Component.literal(String.format("\n%s", this.challengeText)).withStyle(ChatFormatting.GRAY));

                    this.targetPlayer.sendSystemMessage(title());
                    this.targetPlayer.sendSystemMessage(Component.literal("Beide müssen die Odds machen").withStyle(ChatFormatting.RED));
                    this.targetPlayer.sendSystemMessage(Component.literal(String.format("\n%s", this.challengeText)).withStyle(ChatFormatting.GRAY));
                    this.challengeActive = false;

                } else if ((this.sourcePlayerGuess.getAsInt() == 1 && this.targetPlayerGuess.getAsInt() == limit)
                        || (this.targetPlayerGuess.getAsInt() == 1 && this.sourcePlayerGuess.getAsInt() == limit)) {
                    this.targetPlayer.sendSystemMessage(title());
                    this.targetPlayer.sendSystemMessage(Component.literal(String.format("%s muss die Odds machen!", this.sourcePlayer.getName().getString())).withStyle(ChatFormatting.GREEN));
                    this.targetPlayer.sendSystemMessage(regularMessage(String.format("\n%s", this.challengeText)));

                    this.sourcePlayer.sendSystemMessage(title());
                    this.sourcePlayer.sendSystemMessage(Component.literal("Du hast die Odds verloren. Du musst die Odds machen!").withStyle(ChatFormatting.RED));
                    this.sourcePlayer.sendSystemMessage(regularMessage(String.format("\n%s", this.challengeText)));
                    this.challengeActive = false;

                } else {
                    this.sourcePlayer.sendSystemMessage(title());
                    this.sourcePlayer.sendSystemMessage(Component.literal("Nichts passiert").withStyle(ChatFormatting.YELLOW));
                    this.sourcePlayer.sendSystemMessage(regularMessage(String.format("\nDeine Odds waren: %s", this.sourcePlayerGuess.getAsInt())));
                    this.sourcePlayer.sendSystemMessage(regularMessage(String.format("%s Odds waren: %s", this.targetPlayer.getName().getString(), this.targetPlayerGuess.getAsInt())));

                    this.targetPlayer.sendSystemMessage(title());
                    this.targetPlayer.sendSystemMessage(Component.literal("Nichts passiert").withStyle(ChatFormatting.YELLOW));
                    this.targetPlayer.sendSystemMessage(regularMessage(String.format("\nDeine Odds waren: %s", this.targetPlayerGuess.getAsInt())));
                    this.targetPlayer.sendSystemMessage(regularMessage(String.format("%s Odds waren: %s", this.sourcePlayer.getName().getString(), this.sourcePlayerGuess.getAsInt())));
                    this.challengeActive = false;
                }

                challenges.remove(this);
            }
        }
    }
}
