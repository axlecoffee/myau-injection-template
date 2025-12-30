package coffee.axle.myauinjectiontemplate.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import coffee.axle.myauinjectiontemplate.hooks.ClientHook;
import net.minecraft.client.gui.GuiMainMenu;

@Mixin(GuiMainMenu.class)
public class MixinGuiMainMenu {
    private final ClientHook myau = ClientHook.getInstance();

    @Inject(method = "initGui", at = @At("HEAD"))
    public void onInitGui(CallbackInfo ci) throws Exception {
        System.out.println("Hello from Main Menu!" + myau.getClientName() + myau.getClientVersion());
    }
}
