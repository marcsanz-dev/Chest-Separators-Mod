package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.Chestseparators;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * Defines the data packet sent from Server to Client.
 * Contains the unique UUID of the container the player just opened.
 */
public record SyncContainerIdPayload(UUID uuid) implements CustomPayload {

    // 1. Define the Unique Identifier for this packet channel
    public static final CustomPayload.Id<SyncContainerIdPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Chestseparators.MOD_ID, "sync_id"));

    // 2. Define how to Encode/Decode the data (The Codec)
    // This tells Minecraft: "To send this, take the UUID. To receive, read a UUID and make a new Payload."
    public static final PacketCodec<RegistryByteBuf, SyncContainerIdPayload> CODEC =
            PacketCodec.tuple(Uuids.PACKET_CODEC, SyncContainerIdPayload::uuid, SyncContainerIdPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}