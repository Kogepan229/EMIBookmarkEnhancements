package net.kogepan.emi_bookmark_enhancements;

import com.mojang.logging.LogUtils;
import net.kogepan.emi_bookmark_enhancements.integration.emi.ClientLifecycleHooks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

@Mod(EmiBookmarkEnhancements.MODID)
public final class EmiBookmarkEnhancements {

    public static final String MODID = "emi_bookmark_enhancements";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            ClientLifecycleHooks.onClientSetup(event);
        }
    }
}
