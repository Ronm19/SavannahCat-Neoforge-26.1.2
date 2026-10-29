package net.ronm19.savannahcat.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.ronm19.savannahcat.SavannahCatMod;
import net.ronm19.savannahcat.client.renderer.SavannahCatRenderer;
import net.ronm19.savannahcat.registry.ModEntities;

/** Registered only on the physical client; common code has no rendering imports. */
@EventBusSubscriber(modid = SavannahCatMod.MOD_ID, value = Dist.CLIENT)
public final class SavannahCatClient {
    private SavannahCatClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SAVANNAH_CAT.get(), SavannahCatRenderer::new);
    }
}
