package io.szktas.eos.Command;

import com.mojang.brigadier.CommandDispatcher;
import io.szktas.eos.Config;
import io.szktas.eos.DDNS.CloudflareUpdater;
import io.szktas.eos.EOSBinder.EOSNative;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import org.apache.commons.lang3.SystemUtils;

import java.util.Date;
import java.util.Objects;

public class CommandRegister {
    public static MutableComponent format() {
        if (EOSNative.isCanUse()) return Component.translatableWithFallback("command.eosp2p.available", "EOS Service Available");
        MutableComponent baseComponent = Component.translatableWithFallback("command.eosp2p.notavailable", "EOS Service Not Available");
        if (EOSNative.reason != null) {
            baseComponent = baseComponent.append(Component.translatableWithFallback("command.eosp2p.reason", "Reason: %s", Component.translatable("command.eosp2p." + EOSNative.reason.name().toLowerCase())));
            switch (EOSNative.reason) {
                case UNSUPPORTED_OS:
                    baseComponent = baseComponent.append(Component.translatableWithFallback("command.eosp2p.os_hint", "  Current architecture: %s, Accepted: arm64, aarch64, amd64(x86-64)", SystemUtils.OS_NAME));
                    break;
                case UNSUPPORTED_ARCH:
                    baseComponent = baseComponent.append(Component.translatableWithFallback("command.eosp2p.arch_hint", "  Current OS: %s, Expected: Windows, Linux, MacOS(Darwin) >= 11.0", SystemUtils.OS_ARCH));
            }
        }
        if (EOSNative.reasonEOS != null) {
            baseComponent = baseComponent.append(Component.translatableWithFallback("command.eosp2p.reason", "Reason: %s", Component.translatable("command.eosp2p." + EOSNative.reasonEOS.name().toLowerCase())));
            if (EOSNative.reasonEOS == EOSNative.ReasonEOS.SERVICE_INIT_FAIL) {
                baseComponent = baseComponent.append(Component.translatableWithFallback("command.eosp2p.by", "  Caused by: %s", EOSNative.reasonServiceInitFail));
            }
        }
        return baseComponent;
    }

    public static void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("eosp2p")
                        .then(
                                Commands.literal("eosaddress")
                                        .executes(context -> {
                                            if (!EOSNative.isCanUse()) {
                                                context.getSource().sendFailure(Component.translatable("command.eosp2p.notavailable"));
                                                return 0;
                                            }
                                            if (!context.getSource().getServer().isPublished()) {
                                                context.getSource().sendFailure(Component.translatable("command.eosp2p.notyetpublish"));
                                                return 0;
                                            }
                                            context.getSource().sendSuccess(() -> Component.translatableWithFallback(
                                                    "chat.eosp2p.show_connect",
                                                    "Address through EOS: %s",
                                                    ComponentUtils.copyOnClickText("EOS:" + Objects.requireNonNull(EOSNative.getConnectionKey()))
                                            ), true);
                                            return 1;
                                        })
                        ).then(
                                Commands.literal("ddns")
                                        .then(
                                                Commands.literal("update")
                                                        .executes(context -> {
                                                            if (!EOSNative.isCanUse()) {
                                                                context.getSource().sendFailure(Component.translatable("command.eosp2p.notavailable"));
                                                                return 0;
                                                            }
                                                            if (!Config.CLOUDFLARE_ENABLED.get()) {
                                                                context.getSource().sendFailure(Component.translatableWithFallback("command.eosp2p.ddns.disabled", "Cloudflare DDNS is not enabled"));
                                                                return 0;
                                                            }
                                                            context.getSource().sendSuccess(() -> Component.translatableWithFallback("command.eosp2p.ddns.updating", "Updating Cloudflare DNS record..."), true);
                                                            var server = context.getSource().getServer();
                                                            EOSNative.executor.submit(() -> {
                                                                boolean ok = CloudflareUpdater.updateRecord(
                                                                        Config.CLOUDFLARE_API_TOKEN.get(),
                                                                        Config.CLOUDFLARE_ZONE_ID.get(),
                                                                        Config.CLOUDFLARE_DDNS_RECORD.get(),
                                                                        EOSNative.getConnectionKey()
                                                                );
                                                                server.execute(() -> context.getSource().sendSuccess(() -> Component.translatableWithFallback(
                                                                        ok ? "command.eosp2p.ddns.success" : "command.eosp2p.ddns.failed",
                                                                        ok ? "DDNS update successful" : "DDNS update failed"
                                                                ), true));
                                                            });
                                                            return 1;
                                                        })
                                        ).then(
                                                Commands.literal("status")
                                                        .executes(context -> {
                                                            if (!Config.CLOUDFLARE_ENABLED.get()) {
                                                                context.getSource().sendFailure(Component.translatableWithFallback("command.eosp2p.ddns.disabled", "Cloudflare DDNS is not enabled"));
                                                                return 0;
                                                            }
                                                            long lastTime = CloudflareUpdater.getLastUpdatedTime();
                                                            MutableComponent msg = lastTime > 0
                                                                    ? Component.translatableWithFallback("command.eosp2p.ddns.last_update", "Last updated: %s", new Date(lastTime).toString())
                                                                    : Component.translatableWithFallback("command.eosp2p.ddns.no_update", "Not updated yet");
                                                            String lastKey = CloudflareUpdater.getLastUpdatedKey();
                                                            if (lastKey != null) {
                                                                msg = Component.translatableWithFallback("command.eosp2p.ddns.with_key",
                                                                        "Last updated: %s\nCurrent key: %s",
                                                                        lastTime > 0 ? new Date(lastTime).toString() : "N/A", lastKey);
                                                            }
                                                            final MutableComponent finalMsg = msg;
                                                            context.getSource().sendSuccess(() -> finalMsg, false);
                                                            return 1;
                                                        })
                                        )
                        ).then(
                                Commands.literal("status")
                                        .executes(context -> {
                                            context.getSource().sendSuccess(CommandRegister::format, true);
                                            return 1;
                                        })
                        )
        );
    }
}
