package coffee.axle.myauinjectiontemplate.features;

import coffee.axle.myauinjectiontemplate.hooks.ClientHook;
import coffee.axle.myauinjectiontemplate.util.MyauLogger;

import java.util.ArrayList;
import java.util.Arrays;

public class TestCommand implements Feature {
    private final ClientHook hook = ClientHook.getInstance();

    @Override
    public String getName() {
        return "Command:Test";
    }

    @Override
    public boolean initialize() {
        try {
            MyauLogger.log(getName(), "FEATURE_INIT");
            ArrayList<String> commandNames = new ArrayList<>(Arrays.asList("test"));
            hook.registerCommand(commandNames, this::handleCommand);
            MyauLogger.log(getName(), "FEATURE_SUCCESS");
            return true;
        } catch (Exception e) {
            MyauLogger.error("FEATURE_FAIL", e);
            return false;
        }
    }

    private void handleCommand(ArrayList<String> args) {
        if (args.size() > 1) {
            String arg = args.get(1);
            hook.sendMessage("  &eYou passed: &f" + arg);
            hook.sendMessage("");
        }
        hook.sendMessage("§dMyau Injection Template");
        hook.sendMessage("§aMade by @axle.coffee on discord §ehttps://github.com/axlecoffee");
    }
}
