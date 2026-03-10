package de.tert0.containerclaims.mixin;

import com.mojang.datafixers.schemas.Schema;
import de.tert0.containerclaims.ContainerClaimMod;
import net.minecraft.util.filefix.FileFix;
import net.minecraft.util.filefix.access.FileRelation;
import net.minecraft.util.filefix.fixes.DimensionStorageFileFix;
import net.minecraft.util.filefix.operations.FileFixOperations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(DimensionStorageFileFix.class)
public abstract class DimensionStorageFileFixMixin extends FileFix {
    public DimensionStorageFileFixMixin(Schema schema) {
        super(schema);
    }

    @Inject(method = "makeFixer", at = @At("HEAD"))
    void makeFixer(CallbackInfo ci) {
        // Move dimensional claim data to new directories
        this.addFileFixOperation(
                FileFixOperations.groupMove(
                        Map.of(
                        "data",
                        "dimensions/minecraft/overworld/data/" + ContainerClaimMod.MOD_ID,
                        "DIM-1/data",
                        "dimensions/minecraft/the_nether/data/" + ContainerClaimMod.MOD_ID,
                        "DIM1/data",
                        "dimensions/minecraft/the_end/data/" + ContainerClaimMod.MOD_ID
                        ),
                        List.of(
                                FileFixOperations.move("cclaims.dat", "claims.dat")
                        )
                )
        );
        // Move global group data to namespaced path
        this.addFileFixOperation(
                FileFixOperations.applyInFolders(
                        FileRelation.DATA,
                        List.of(
                                FileFixOperations.move("cclaims_groups.dat", ContainerClaimMod.MOD_ID + "/groups.dat")
                        )
                )
        );
    }
}
