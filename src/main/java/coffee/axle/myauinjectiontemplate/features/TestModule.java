package coffee.axle.myauinjectiontemplate.features;

import coffee.axle.myauinjectiontemplate.hooks.ClientHook;
import coffee.axle.myauinjectiontemplate.util.MyauLogger;
import coffee.axle.myauinjectiontemplate.util.TestModuleRenderer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;

public class TestModule implements Feature {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final ClientHook hook = ClientHook.getInstance();

    private Object moduleInstance;
    private Object useMixinProperty;

    @Override
    public String getName() {
        return "TestModule";
    }

    @Override
    public boolean initialize() {
        try {
            // Create the module instance
            this.moduleInstance = this.hook.createModule(this.getName());
            this.hook.injectModule(this.moduleInstance, TestModule.class);

            // Create properties
            this.useMixinProperty = this.hook.createBooleanProperty("use-mixin", false);
            this.hook.registerProperties(this.moduleInstance, this.useMixinProperty);

            // Reload module command so it appears in .myau list
            this.hook.reloadModuleCommand();

            // Register this command handler
            ArrayList<String> commandNames = new ArrayList<>();
            commandNames.add("testmodule");
            this.hook.registerCommand(commandNames, this::handleCommand);

            // Register Forge event bus for rendering and tick updates
            MinecraftForge.EVENT_BUS.register(this);

            // Register enable/disable callbacks
            this.hook.registerModuleCallbacks(
                    this.moduleInstance,
                    () -> {
                        TestModuleRenderer.setModuleEnabled(true);
                        MyauLogger.info("TestModule enabled!");
                    },
                    () -> {
                        TestModuleRenderer.setModuleEnabled(false);
                        MyauLogger.info("TestModule disabled!");
                    });

            MyauLogger.info("TestModule initialized successfully");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            MyauLogger.error("MODULE_FAIL", e);
            return false;
        }
    }

    private void handleCommand(ArrayList<String> args) {
        if (args.isEmpty()) {
            boolean useMixin = getUseMixin();
            hook.sendMessage("&7Usage: &f.testmodule <use-mixin> <true/false>");
            hook.sendMessage("&7Current use-mixin: &f" + useMixin);
            hook.sendMessage("&7Box color: " + (useMixin ? "&aGREEN (mixin)" : "&5PURPLE (direct)"));
            return;
        }

        String subCommand = args.get(0).toLowerCase();
        if (subCommand.equals("use-mixin") && args.size() > 1) {
            boolean value = args.get(1).equalsIgnoreCase("true");
            setUseMixin(value);
            TestModuleRenderer.setMixinEnabled(value);
            hook.sendMessage("&aSet use-mixin to: &f" + value);
            hook.sendMessage("&7Box will now be: " + (value ? "&aGREEN (mixin)" : "&5PURPLE (direct)"));
        } else {
            hook.sendMessage("&cUnknown option: &f" + subCommand);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;

        // Sync module enabled state with renderer
        boolean isEnabled = this.hook.isModuleEnabled(this.getName());
        TestModuleRenderer.setModuleEnabled(isEnabled);

        // Sync use-mixin property with renderer
        TestModuleRenderer.setMixinEnabled(getUseMixin());
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        // Only render if NOT using mixin path (purple = direct, green = mixin)
        // The mixin handles green rendering via TestModuleRenderer.renderMixinBox()
        TestModuleRenderer.renderDirectBox(event.partialTicks);
    }

    private boolean getUseMixin() {
        try {
            return (Boolean) this.hook.getPropertyValue(this.useMixinProperty);
        } catch (Exception e) {
            return false;
        }
    }

    private void setUseMixin(boolean value) {
        try {
            if (this.useMixinProperty != null) {
                java.lang.reflect.Method setValueMethod = this.useMixinProperty.getClass().getMethod("a", Object.class);
                setValueMethod.invoke(this.useMixinProperty, value);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void disable() {
        try {
            MinecraftForge.EVENT_BUS.unregister(this);
            TestModuleRenderer.setModuleEnabled(false);
            if (this.moduleInstance != null) {
                this.hook.setModuleEnabled(this.moduleInstance, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
