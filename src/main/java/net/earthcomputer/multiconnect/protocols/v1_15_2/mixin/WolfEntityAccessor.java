package net.earthcomputer.multiconnect.protocols.v1_15_2.mixin;

import net.earthcomputer.multiconnect.impl.MixinHelper;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.passive.WolfEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WolfEntity.class)
public interface WolfEntityAccessor {
    @Accessor("ANGER_TIME")
    static TrackedData<Integer> getAngerTime() {
        return MixinHelper.fakeInstance();
    }
}
