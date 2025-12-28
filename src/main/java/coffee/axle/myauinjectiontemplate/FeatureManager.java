package coffee.axle.myauinjectiontemplate;

import coffee.axle.myauinjectiontemplate.features.Feature;
import coffee.axle.myauinjectiontemplate.features.TestCommand;
import coffee.axle.myauinjectiontemplate.features.TestModule;
import coffee.axle.myauinjectiontemplate.hooks.ClientHook;
import coffee.axle.myauinjectiontemplate.util.MyauLogger;

import java.util.ArrayList;
import java.util.List;

public class FeatureManager {
    private static final List<Feature> features = new ArrayList<>();
    private final ClientHook clientHook = ClientHook.getInstance();

    public FeatureManager() {
        registerFeature(new TestCommand());
        registerFeature(new TestModule());
    }

    private void registerFeature(Feature feature) {
        features.add(feature);
    }

    public boolean initializeAll() {
        if (!clientHook.initialize()) {
            MyauLogger.log("FM_HOOK_FAIL");
            return false;
        }

        int successCount = 0;

        for (Feature feature : features) {
            try {
                if (feature.initialize()) {
                    successCount++;
                } else {
                    MyauLogger.status(feature.getName(), false);
                }
            } catch (Exception e) {
                MyauLogger.error("FEATURE_FAIL", e);
            }
        }

        MyauLogger.summary(successCount, features.size());
        return successCount > 0;
    }

    public void disableAll() {
        for (Feature feature : features) {
            try {
                feature.disable();
            } catch (Exception e) {
                MyauLogger.info("Failed to disable: " + feature.getName());
            }
        }
    }

    public List<Feature> getFeatures() {
        return features;
    }
}
