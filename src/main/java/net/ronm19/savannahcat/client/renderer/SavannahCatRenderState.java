package net.ronm19.savannahcat.client.renderer;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Explicit Gecko render-state contract; no compile-time interface injection is needed. */
public final class SavannahCatRenderState extends LivingEntityRenderState implements GeoRenderState {
    private final Map<DataTicket<?>, Object> data = new IdentityHashMap<>();
    public boolean collarVisible;
    public int collarColor;
    public boolean babyModel;

    // GeckoLib's EntityRenderState mixin overrides these with its own backing
    // map. Override both so every read and write uses this explicit state map.
    @Override
    public <D> void addGeckolibData(DataTicket<D> ticket, D value) { data.put(ticket, value); }

    @Override
    public boolean hasGeckolibData(DataTicket<?> ticket) { return data.containsKey(ticket); }

    @Override
    public Map<DataTicket<?>, Object> getDataMap() { return data; }

}
