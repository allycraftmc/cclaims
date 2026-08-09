package de.tert0.containerclaims.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.tert0.containerclaims.ClaimCommand;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    @Shadow
    @Final
    private static Logger LOGGER;

    @Inject(method = "handleCustomClickAction", at = @At("TAIL"))
    void handleCustomClickAction(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        //noinspection ConstantValue
        if (!((Object) this instanceof ServerGamePacketListenerImpl serverGamePacketListener)) return;
        ServerPlayer player = serverGamePacketListener.player;

        if(ClaimCommand.ListChangePageAction.IDENTIFIER.equals(packet.id())) {
            packet.payload().ifPresentOrElse(
                tag -> ClaimCommand.ListChangePageAction.CODEC.parse(NbtOps.INSTANCE, tag).ifSuccess(
                        data -> {
                            try {
                                ClaimCommand.listCommand(player.createCommandSourceStack(), data.dimension(), data.page());
                            } catch (CommandSyntaxException e) {
                                player.createCommandSourceStack().sendFailure(ComponentUtils.fromMessage(e.getRawMessage()));
                            }
                        })
                        .ifError(error -> LOGGER.warn("Received invalid custom click action {}: {}", packet.id(), error)),
                () -> LOGGER.warn("Received invalid custom click action {}: Missing Payload ", packet.id())
            );
        } else if(ClaimCommand.GroupListChangePageAction.IDENTIFIER.equals(packet.id())) {
            packet.payload().ifPresentOrElse(
                    tag -> ClaimCommand.GroupListChangePageAction.CODEC.parse(NbtOps.INSTANCE, tag).ifSuccess(
                            data -> {
                                try {
                                    ClaimCommand.groupListCommand(player.createCommandSourceStack(), data.page());
                                } catch (CommandSyntaxException e) {
                                    player.createCommandSourceStack().sendFailure(ComponentUtils.fromMessage(e.getRawMessage()));
                                }
                            }
                    ).ifError(error -> LOGGER.warn("Received invalid custom click action {}: {}", packet.id(), error)),
                    () -> LOGGER.warn("Received invalid custom click action {}: Missing Payload", packet.id())
            );
        }
    }
}
