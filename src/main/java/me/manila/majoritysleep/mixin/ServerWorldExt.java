package me.manila.majoritysleep.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.GameRules;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.Spawner;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.storage.LevelStorage;
import org.lwjgl.system.CallbackI;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Mixin(ServerWorld.class)
public abstract class ServerWorldExt extends World implements ServerWorldAccess {
    private boolean enoughPlayersSleeping = false;
    private List sleepingPlayers;

    @Shadow
    private List players;

    public ServerWorldExt(MutableWorldProperties mutableWorldProperties, RegistryKey<World> registryKey, RegistryKey<DimensionType> registryKey2, DimensionType dimensionType, Supplier<Profiler> profiler, boolean bl, boolean bl2, long l) {
        super(mutableWorldProperties, registryKey, registryKey2, dimensionType, profiler, bl, bl2, l);
    }

    @Shadow
    private void resetWeather() {}

    @Shadow
    private void wakeSleepingPlayers() {}

    @Shadow
    private void setTimeOfDay(long arg) {}

    @Inject(at = @At("TAIL"), method = "updateSleepingPlayers")
    public void updatePlayersSleeping(CallbackInfo ci)
    {
        if (!this.players.isEmpty()) {
            int i = 0;
            int j = 0;
            Iterator players = this.players.iterator();

            while(players.hasNext()) {
                ServerPlayerEntity player = (ServerPlayerEntity) players.next();
                if (player.isSpectator()) {
                    i++;
                } else if (player.isSleeping()) {
                    j++;
                }
            }

            this.enoughPlayersSleeping = j > 0 && j > (int)(Math.floor((this.players.size() - i) / 2));
        }
    }

    @Inject(at = @At("TAIL"), method = "tick")
    public void isEnoughPlayersSleeping(CallbackInfo ci) {
        if (this.enoughPlayersSleeping && this.players.stream().filter(p -> ((LivingEntity)p).isSleeping()).noneMatch((arg) -> {
            return !((PlayerEntity)arg).isSleepingLongEnough();
        })) {
            this.enoughPlayersSleeping = false;
            if (this.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) {
                long l = this.properties.getTimeOfDay() + 24000L;
                this.setTimeOfDay(l - l % 24000L);
            }

            this.wakeSleepingPlayers();
            if (this.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE)) {
                this.resetWeather();
            }
        }
    }
}
