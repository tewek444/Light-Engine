package me.tewek.lightengine;

import com.mojang.logging.LogUtils;
import me.tewek.lightengine.lightfield.LightField;
import me.tewek.lightengine.network.ModNetwork;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(LightEngine.MODID)
public class LightEngine {
    public static final String MODID = "lightengine";
    public static final String VERSION = "1.0.0-alpha3+1.20.1";
    public static final String LOADER = "forge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LightEngine() {
        // Network channels must exist before any client/server handshake.
        ModNetwork.register();
        // Manual TOML config: single lightengine-common.toml, profile tables preserved.
        LightEngineConfig.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            LightEngineClient.init();
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            LightField.drop(level);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LeDumpCommand.register(event.getDispatcher());
        LeRepairCommand.register(event.getDispatcher());
    }
}
