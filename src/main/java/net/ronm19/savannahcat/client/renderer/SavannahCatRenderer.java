package net.ronm19.savannahcat.client.renderer;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.base.BoneSnapshots;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.ronm19.savannahcat.client.model.SavannahCatModel;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import net.ronm19.savannahcat.companion.CompanionAction;
import org.jspecify.annotations.Nullable;

public final class SavannahCatRenderer extends GeoEntityRenderer<SavannahCatEntity, SavannahCatRenderState> {
    public SavannahCatRenderer(EntityRendererProvider.Context context) {
        super(context, new SavannahCatModel());
        shadowRadius = 0.4F;
        withRenderLayer(new SavannahCatCollarLayer(this));
    }
    @Override
    public SavannahCatRenderState createRenderState(SavannahCatEntity entity, @Nullable Void related) {
        return new SavannahCatRenderState();
    }

    @Override
    public void addRenderData(SavannahCatEntity entity, @Nullable Void related, SavannahCatRenderState state, float partialTick) {
        state.babyModel=entity.isBaby();
        state.addGeckolibData(SavannahCatEntity.BABY_RENDER_DATA,entity.isBaby());
        state.addGeckolibData(SavannahCatEntity.BABY_RUN_RENDER_DATA,entity.isBaby() && entity.walkAnimation.speed(partialTick)>.45F);
        state.addGeckolibData(SavannahCatEntity.SITTING_RENDER_DATA, entity.isSitting());
        state.addGeckolibData(SavannahCatEntity.SUSAN_TILT_RENDER_DATA, entity.getSusanTiltSide());
        state.addGeckolibData(SavannahCatEntity.ACTION_RENDER_DATA, entity.getAction().getId());
        state.addGeckolibData(SavannahCatEntity.ACTION_SEQUENCE_RENDER_DATA, entity.getActionSequence());
    }

    /** Native custom-geometry submissions do not automatically forward outlineColor. */
    public @Nullable RenderType getOutlinePassType(SavannahCatRenderState state, @Nullable RenderType normalType) {
        return normalType != null && !normalType.isOutline() && state.appearsGlowing()
            ? RenderTypes.outline(getTextureLocation(state)) : null;
    }

    @Override
    public void submitRenderTasks(RenderPassInfo<SavannahCatRenderState> info, OrderedSubmitNodeCollector tasks,
            @Nullable RenderType renderType) {
        super.submitRenderTasks(info, tasks, renderType);
        RenderType outline = getOutlinePassType(info.renderState(), renderType);
        if (outline == null || info.model().isMissingno()) return;
        int color = info.renderState().outlineColor;
        int light = info.packedLight(), overlay = info.packedOverlay();
        // Use the vanilla outline target and postprocessing with the same
        // cached posed geometry. The normal coat pass above stays unchanged.
        tasks.submitCustomGeometry(info.poseStack(), outline, (pose, vertices) -> {
            PoseStack stack = info.poseStack();
            stack.pushPose();
            try {
                stack.last().set(pose);
                info.renderPosed(() -> info.model().render(info, vertices, light, overlay, color));
            } finally { stack.popPose(); }
        });
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<SavannahCatRenderState> info, BoneSnapshots snapshots) {
        var action = CompanionAction.byId(info.renderState().getOrDefaultGeckolibData(SavannahCatEntity.ACTION_RENDER_DATA, 0));
        if (action == CompanionAction.SLEEP || action == CompanionAction.DOWNED || action == CompanionAction.REVIVE
                || action == CompanionAction.EAT || action == CompanionAction.DRINK) return;
        snapshots.ifPresent("head", head -> {
            float yaw=info.renderState().babyModel?6:8,pitch=info.renderState().babyModel?4:5;
            head.setRotY(head.getRotY() - Mth.clamp(info.renderState().yRot, -yaw, yaw) * Mth.DEG_TO_RAD);
            head.setRotX(head.getRotX() - Mth.clamp(info.renderState().xRot, -pitch, pitch) * Mth.DEG_TO_RAD);
        });
    }
}
