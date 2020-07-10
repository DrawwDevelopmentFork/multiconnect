package net.earthcomputer.multiconnect.mixin.bridge;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import net.earthcomputer.multiconnect.impl.ConnectionInfo;
import net.earthcomputer.multiconnect.protocols.generic.*;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.class_5455;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.EntityType;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.SynchronizeTagsS2CPacket;
import net.minecraft.tag.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.BuiltinRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Consumer;

@Mixin(value = ClientPlayNetworkHandler.class, priority = -1000)
public class MixinClientPlayNetworkHandler {

    @Inject(method = "onChunkData", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V", shift = At.Shift.AFTER))
    private void preChunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
        CurrentChunkDataPacket.push(packet);
    }

    @Inject(method = "onChunkData", at = @At("RETURN"))
    private void postChunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
        CurrentChunkDataPacket.pop();
    }

    @Inject(method = "onGameJoin", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V", shift = At.Shift.AFTER))
    private void onOnGameJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        RegistryMutator mutator = new RegistryMutator();
        class_5455.class_5457 registries = (class_5455.class_5457) packet.getDimension();
        //noinspection ConstantConditions
        MutableDynamicRegistriesAccessor registriesAccessor = (MutableDynamicRegistriesAccessor) (Object) registries;
        registriesAccessor.setRegistries(new HashMap<>(registriesAccessor.getRegistries())); // make registries mutable
        ConnectionInfo.protocol.mutateDynamicRegistries(mutator, registries);

        for (RegistryKey<? extends Registry<?>> registryKey : class_5455.field_25919.keySet()) {
            if (registryKey != Registry.DIMENSION_TYPE_KEY && class_5455.field_25919.get(registryKey).method_30537()) {
                addMissingValues(getBuiltinRegistry(registryKey), registries);
            }
        }

        registriesAccessor.setRegistries(ImmutableMap.copyOf(registriesAccessor.getRegistries())); // make immutable again (faster)
    }

    @SuppressWarnings("unchecked")
    @Unique
    private static <T, R extends Registry<T>> Registry<?> getBuiltinRegistry(RegistryKey<? extends Registry<?>> registryKey) {
        return ((Registry<R>) BuiltinRegistries.REGISTRIES).get((RegistryKey<R>) registryKey);
    }

    @SuppressWarnings("unchecked")
    @Unique
    private static <T> void addMissingValues(Registry<T> builtinRegistry, class_5455.class_5457 registries) {
        Registry<T> dynamicRegistry =  registries.method_30530(builtinRegistry.getKey());
        ISimpleRegistry<T> iregistry = (ISimpleRegistry<T>) dynamicRegistry;
        for (T val : builtinRegistry) {
            if (dynamicRegistry.getId(val) == null) {
                builtinRegistry.getKey(val).ifPresent(key -> iregistry.register(val, iregistry.getNextId(), key, false));
            }
        }
    }

    @Inject(method = "onSynchronizeTags", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V", shift = At.Shift.AFTER))
    private void onOnSynchronizeTags(SynchronizeTagsS2CPacket packet, CallbackInfo ci) {
        TagRegistry<Block> blockTagRegistry = new TagRegistry<>();
        TagGroup<Block> blockTags = setExtraTags(packet.getTagManager().getBlocks(), blockTagRegistry, ConnectionInfo.protocol::addExtraBlockTags);
        TagGroup<Item> itemTags = setExtraTags(packet.getTagManager().getItems(), new TagRegistry<>(), itemTagRegistry -> ConnectionInfo.protocol.addExtraItemTags(itemTagRegistry, blockTagRegistry));
        TagGroup<Fluid> fluidTags = setExtraTags(packet.getTagManager().getFluids(), new TagRegistry<>(), ConnectionInfo.protocol::addExtraFluidTags);
        TagGroup<EntityType<?>> entityTypeTags = setExtraTags(packet.getTagManager().getEntityTypes(), new TagRegistry<>(), ConnectionInfo.protocol::addExtraEntityTags);
        ((SynchronizeTagsS2CAccessor) packet).setTagManager(TagManager.create(blockTags, itemTags, fluidTags, entityTypeTags));
    }

    @Unique
    private static <T> TagGroup<T> setExtraTags(TagGroup<T> group, TagRegistry<T> tagRegistry, Consumer<TagRegistry<T>> tagsAdder) {
        group.getTags().forEach((id, tag) -> tagRegistry.put(id, new HashSet<>(tag.values())));
        tagsAdder.accept(tagRegistry);
        BiMap<Identifier, Tag<T>> tagBiMap = HashBiMap.create(tagRegistry.size());
        tagRegistry.forEach((id, set) -> tagBiMap.put(id, Tag.of(set)));
        return TagGroup.create(tagBiMap);
    }

    @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
    private void onOnCustomPayload(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        NetworkThreadUtils.forceMainThread(packet, (ClientPlayNetworkHandler) (Object) this, MinecraftClient.getInstance());
        if (packet.getChannel().equals(CustomPayloadHandler.DROP_ID)) {
            ci.cancel();
        } else if (ConnectionInfo.protocolVersion != SharedConstants.getGameVersion().getProtocolVersion()
                && !CustomPayloadHandler.VANILLA_CHANNELS.contains(packet.getChannel())) {
            CustomPayloadHandler.handleCustomPayload(packet);
            ci.cancel();
        }
    }

}
