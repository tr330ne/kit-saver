package treeone.kitsaver.mixin;

import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import treeone.kitsaver.module.KitSaver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.gui.tooltip.Tooltip;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;

@Mixin(InventoryScreen.class)
public abstract class ScreenMixin extends RecipeBookScreen<PlayerScreenHandler>
        implements RecipeBookProvider {

    public ScreenMixin(PlayerScreenHandler handler, RecipeBookWidget<?> recipeBook,
                       PlayerInventory inventory, Text title) {
        super(handler, recipeBook, inventory, title);
    }

    @Unique @Nullable
    private KitSaver kitSaver = null;
    @Unique @Nullable private ButtonWidget saveBtn = null;
    @Unique @Nullable private ButtonWidget loadBtn = null;

    @Unique
    private KitSaver ks() {
        if (kitSaver == null) {
            Modules mods = Modules.get();
            if (mods != null) kitSaver = mods.get(KitSaver.class);
        }
        return kitSaver;
    }

    @Unique
    private void onSave(ButtonWidget btn) {
        KitSaver k = ks();
        if (k == null) return;
        k.saveKit();
        btn.setMessage(Text.literal("Saved"));
    }

    @Unique
    private void onLoad(ButtonWidget btn) {
        KitSaver k = ks();
        if (k == null) return;
        k.loadKit();
        btn.setMessage(Text.literal("Loaded"));
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        KitSaver k = ks();
        if (k == null) return;

        saveBtn = this.addDrawableChild(
                ButtonWidget.builder(Text.literal("§fSave"), this::onSave)
                        .dimensions(this.width / 2 - 44, this.height / 2 + 83, 44, 16)
                        .tooltip(Tooltip.of(Text.literal("§7Save current inventory as kit.")))
                        .build()
        );

        loadBtn = this.addDrawableChild(
                ButtonWidget.builder(Text.literal("§fLoad"), this::onLoad)
                        .dimensions(this.width / 2, this.height / 2 + 83, 44, 16)
                        .tooltip(Tooltip.of(Text.literal("§7Load latest kit into inventory.")))
                        .build()
        );

        updateVisibility(k);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(CallbackInfo ci) {
        KitSaver k = ks();
        if (k == null) return;
        updateVisibility(k);
    }

    @Inject(method = "handledScreenTick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        KitSaver k = ks();
        if (k == null) return;
        if (k.isActive() && !k.isSorted) {
            if (saveBtn != null) saveBtn.setMessage(Text.literal("§fSave"));
            if (loadBtn != null) loadBtn.setMessage(Text.literal("§fLoad"));
        }
    }

    @Unique
    private void updateVisibility(KitSaver k) {
        boolean visible = k.isActive();
        if (saveBtn != null) saveBtn.visible = visible;
        if (loadBtn != null) loadBtn.visible = visible;
    }
}