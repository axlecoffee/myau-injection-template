package coffee.axle.myauinjectiontemplate.features;

public interface Feature {
    boolean initialize() throws Exception;

    String getName();

    default void disable() throws Exception {
    }
}
