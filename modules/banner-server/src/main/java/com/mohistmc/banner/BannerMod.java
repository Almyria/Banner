package com.mohistmc.banner;

import com.mohistmc.banner.eventhandler.BannerEventDispatcherRegistry;
import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BannerMod implements DedicatedServerModInitializer {

    public static final String MOD_ID = "banner";

    public static final Logger LOGGER =
            LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeServer() {
        BannerEventDispatcherRegistry.registerEventDispatchers();
    }

    public static String getVersion() {
        return System.getProperty("banner.version");
    }
}