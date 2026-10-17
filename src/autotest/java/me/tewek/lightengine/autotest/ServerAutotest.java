package me.tewek.lightengine.autotest;

import java.util.Optional;
import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightEngineApi;
import me.tewek.lightengine.LightProfileRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dedicated-server half of the multiplayer autotest ({@code runAutotestServer}).
 * Builds the scene, then on a timer counted from the player's login: light
 * removal cycles, op level 2, op level 4, and "[srv]" log lines the client half
 * ({@link JoinAutotest}, same timer) is compared against. Stops the server at
 * the end.
 */
@Mod(value = "lightengine_autotest", dist = Dist.DEDICATED_SERVER)
public class ServerAutotest {
    static final BlockPos[] PROBES = {
            new BlockPos(0, -60, -3), new BlockPos(0, -60, -6), new BlockPos(4, -60, 0), new BlockPos(-8, -60, 0)};

    private MinecraftServer server;
    private ServerPlayer player;
    private int tick = -1;

    public ServerAutotest() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onStarted(ServerStartedEvent event) {
        this.server = event.getServer();
        cmd("time set midnight");
        cmd("fill -12 -61 -12 12 -61 12 minecraft:stone");
        cmd("fill -12 -60 -12 12 -55 12 minecraft:air");
        cmd("setblock 0 -60 0 minecraft:soul_lantern");
        LightEngine.LOGGER.info("[srv] scene ready");
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            this.player = p;
            this.tick = 0;
        }
    }

    @SubscribeEvent
    public void onTick(ServerTickEvent.Post event) {
        if (this.tick < 0 || this.player == null) {
            return;
        }
        int t = this.tick++;
        switch (t) {
            case 20 -> {
                cmd("gamemode spectator @a");
                cmd("tp @a 0.5 -56 -9.5 0 30");
            }
            case 200 -> probes("join");
            case 240, 300 -> {
                cmd("setblock 0 -60 0 minecraft:stone");
                cmd("setblock 0 -59 0 minecraft:soul_lantern");
            }
            case 270, 330 -> cmd("setblock 0 -60 0 minecraft:air");
            case 400 -> probes("removed");
            case 500 -> op(PermissionLevel.GAMEMASTERS);
            case 600 -> op(PermissionLevel.OWNERS);
            case 720 -> {
                var torch = LightProfileRegistry.snapshot().get(Identifier.withDefaultNamespace("torch"));
                LightEngine.LOGGER.info("[srv] torch radius={}", torch == null ? -1 : torch.lightRadius());
                cmd("setblock 0 -60 0 minecraft:soul_lantern");
            }
            case 800 -> probes("final");
            case 1000 -> {
                LightEngine.LOGGER.info("[srv] done");
                this.server.halt(false);
            }
            default -> {
            }
        }
    }

    private void op(PermissionLevel level) {
        this.server.getPlayerList().op(this.player.nameAndId(),
                Optional.of(LevelBasedPermissionSet.forLevel(level)), Optional.empty());
        LightEngine.LOGGER.info("[srv] opped {}", level);
    }

    private void probes(String tag) {
        StringBuilder sb = new StringBuilder();
        for (BlockPos p : PROBES) {
            sb.append(p.toShortString()).append('=').append(LightEngineApi.getLightLevel(this.server.overworld(), p)).append("  ");
        }
        LightEngine.LOGGER.info("[srv] {} {}", tag, sb);
    }

    private void cmd(String command) {
        this.server.getCommands().performPrefixedCommand(this.server.createCommandSourceStack(), command);
    }
}
