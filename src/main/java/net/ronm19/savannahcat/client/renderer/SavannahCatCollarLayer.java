package net.ronm19.savannahcat.client.renderer;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import java.util.function.BiConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import org.jspecify.annotations.Nullable;

/** Separate cloth attachment; the supplied cat model and coat atlases remain untouched. */
public final class SavannahCatCollarLayer extends GeoRenderLayer<SavannahCatEntity, Void, SavannahCatRenderState> {
    private static final Identifier CLOTH = Identifier.withDefaultNamespace("textures/block/white_wool.png");
    private final ModelPart collar = createAttachment();
    private final ModelPart babyCollar = createBabyAttachment();

    public SavannahCatCollarLayer(SavannahCatRenderer renderer) { super(renderer); }

    public static ModelPart createAttachment() {
        MeshDefinition mesh = new MeshDefinition();
        // Local to the chest pivot (0, 13, -7), in the model's Y-up coordinates.
        // The four narrow strips clear the original six-pixel chest by .08px.
        mesh.getRoot().addOrReplaceChild("collar", CubeListBuilder.create().texOffs(0, 0)
            .addBox(-3.10F, 2.02F, -1.60F, 6.20F, 0.96F, 0.18F)
            .addBox(-3.10F, 2.02F, 1.42F, 6.20F, 0.96F, 0.18F)
            .addBox(-3.10F, 2.02F, -1.42F, 0.18F, 0.96F, 2.84F)
            .addBox(2.92F, 2.02F, -1.42F, 0.18F, 0.96F, 2.84F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("collar");
    }

    public static ModelPart createBabyAttachment() {
        MeshDefinition mesh=new MeshDefinition();
        // Baby chest pivot (0,7,-5); five-pixel torso, same three-pixel depth.
        mesh.getRoot().addOrReplaceChild("collar",CubeListBuilder.create().texOffs(0,0)
            .addBox(-2.59F,1.12F,-1.60F,5.18F,.72F,.18F)
            .addBox(-2.59F,1.12F,1.42F,5.18F,.72F,.18F)
            .addBox(-2.59F,1.12F,-1.42F,.18F,.72F,2.84F)
            .addBox(2.41F,1.12F,-1.42F,.18F,.72F,2.84F),PartPose.ZERO);
        return LayerDefinition.create(mesh,16,16).bakeRoot().getChild("collar");
    }

    @Override
    public void addRenderData(SavannahCatEntity entity, @Nullable Void related, SavannahCatRenderState state, float partialTick) {
        state.collarVisible = entity.isTamed();
        state.collarColor = entity.getCollarColor().getTextureDiffuseColor();
        state.babyModel = entity.isBaby();
    }

    @Override
    public void addPerBoneRender(RenderPassInfo<SavannahCatRenderState> info,
            BiConsumer<GeoBone, PerBoneRender<SavannahCatRenderState>> consumer) {
        if (!info.renderState().collarVisible || !info.willRender()) return;
        info.model().getBone("chest").ifPresent(chest -> consumer.accept(chest, (pass, bone, collector) ->
            collector.submitModelPart(pass.renderState().babyModel?babyCollar:collar, pass.poseStack(), RenderTypes.entityCutout(CLOTH),
                pass.packedLight(), pass.packedOverlay(), null, false, false, pass.renderState().collarColor, null,
                pass.renderState().outlineColor)));
    }
}
