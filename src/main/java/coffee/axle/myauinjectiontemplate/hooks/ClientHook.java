package coffee.axle.myauinjectiontemplate.hooks;

import coffee.axle.myauinjectiontemplate.util.MyauLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHook {
    private static ClientHook instance;
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Object moduleManager;
    private LinkedHashMap<Class<?>, Object> modulesMap;
    private Method commandRunMethod;
    private String cachedUsername = null;
    private Method moduleGetNameMethod;
    private Method propertyGetValueMethod;
    private Method propertyGetNameMethod;
    private Method moduleSetEnabledMethod;
    private Object commandManager;
    private Class<?> commandBaseClass;

    private static final Map<String, CommandHandler> commandHandlers = new ConcurrentHashMap<>();
    private static int commandIdCounter = 0;

    private Map<String, Runnable> moduleEnableCallbacks = new ConcurrentHashMap<>();
    private Map<String, Runnable> moduleDisableCallbacks = new ConcurrentHashMap<>();
    private String clientNamePrefix;

    private final Map<String, Object> moduleCache = new ConcurrentHashMap<>();
    private final Map<String, Object> propertyCache = new ConcurrentHashMap<>();
    private final Map<String, Field> fieldCache = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();
    private ClassWriter cachedClassWriter;

    private ClientHook() {
    }

    public static ClientHook getInstance() {
        if (instance == null) {
            synchronized (ClientHook.class) {
                if (instance == null) {
                    instance = new ClientHook();
                }
            }
        }
        return instance;
    }

    public boolean initialize() {
        try {
            MyauLogger.log("HOOK_INIT");
            Class<?> myauClass = getCachedClass("myau.X");
            Field moduleManagerField = getCachedField(myauClass, "j");
            moduleManagerField.setAccessible(true);
            this.moduleManager = moduleManagerField.get(null);

            if (this.moduleManager == null) {
                MyauLogger.log("HOOK_NOT_READY");
                return false;
            }

            Field modulesField = getCachedField(this.moduleManager.getClass(), "E");
            modulesField.setAccessible(true);
            this.modulesMap = (LinkedHashMap<Class<?>, Object>) modulesField.get(this.moduleManager);

            if (this.modulesMap != null && !this.modulesMap.isEmpty()) {
                cacheCommonMethods();
                cacheCommandManager();
                MyauLogger.logMore("HOOK_SUCCESS", this.modulesMap.size() + " modules");
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            MyauLogger.error("HOOK_FAIL", e);
            return false;
        }
    }

    private void cacheCommonMethods() throws Exception {
        Object firstModule = this.modulesMap.values().iterator().next();
        this.moduleGetNameMethod = firstModule.getClass().getMethod("J");

        for (Field f : firstModule.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            Object prop = f.get(firstModule);
            if (prop != null) {
                try {
                    this.propertyGetNameMethod = prop.getClass().getMethod("B");
                    this.propertyGetValueMethod = prop.getClass().getMethod("J");
                    break;
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Class<?> getCachedClass(String className) throws ClassNotFoundException {
        return this.classCache.computeIfAbsent(className, name -> {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Field getCachedField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        String key = clazz.getName() + "." + fieldName;
        Field field = this.fieldCache.get(key);
        if (field == null) {
            field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            this.fieldCache.put(key, field);
        }
        return field;
    }

    private Method findMethodInHierarchy(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        for (Class<?> currentClass = clazz; currentClass != null; currentClass = currentClass.getSuperclass()) {
            try {
                Method method = currentClass.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    public Field findFieldInHierarchy(Class<?> clazz, String fieldName) {
        String key = clazz.getName() + ".hierarchy." + fieldName;
        Field field = this.fieldCache.get(key);
        if (field != null) {
            return field;
        }

        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            try {
                field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                this.fieldCache.put(key, field);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    public Object createModule(String moduleName) throws Exception {
        String className = "coffee.axle.myauinjectiontemplate.generated." + moduleName + "_"
                + System.currentTimeMillis();
        byte[] classBytes = generateModuleClass(className);
        ClassLoader loader = new FastClassLoader(getClass().getClassLoader(), className, classBytes);
        Class<?> generatedClass = loader.loadClass(className);
        Constructor<?> constructor = generatedClass.getDeclaredConstructor(String.class, boolean.class, int.class,
                long.class);
        constructor.setAccessible(true);
        Object instance = constructor.newInstance(moduleName, false, 0, 0L);
        Field nameField = findFieldInHierarchy(instance.getClass(), "o");
        if (nameField != null) {
            nameField.set(instance, moduleName);
        }
        this.moduleCache.put(moduleName, instance);
        return instance;
    }

    public void injectModule(Object module, Class<?> dummyClass) {
        this.modulesMap.put(dummyClass, module);
    }

    public boolean injectProperty(Object module, Object property) {
        return injectPropertyAfter(module, property, null);
    }

    public void sendMessage(String message) {
        try {
            String prefix;
            try {
                prefix = getClientName();
            } catch (Exception e) {
                prefix = "§7[§cMyau§7]§r ";
            }

            String formatted = (prefix + message).replaceAll("&([0-9a-fk-or])", "§$1");
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText(formatted));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cacheCommandManager() throws Exception {
        Class<?> myauClass = getCachedClass("myau.X");
        Field cmField = myauClass.getDeclaredField("i");
        cmField.setAccessible(true);
        this.commandManager = cmField.get(null);
        this.clientNamePrefix = getClientName();

        Field commandsField = this.commandManager.getClass().getDeclaredField("Z");
        commandsField.setAccessible(true);
        ArrayList<?> commands = (ArrayList<?>) commandsField.get(this.commandManager);

        if (!commands.isEmpty()) {
            Object firstCommand = commands.get(0);
            this.commandBaseClass = firstCommand.getClass().getSuperclass();
            this.commandRunMethod = this.commandBaseClass.getDeclaredMethod("J", ArrayList.class, long.class);
        } else {
            throw new Exception("No commands found");
        }
    }

    public static void invokeHandler(String handlerId, ArrayList<String> args) {
        CommandHandler handler = commandHandlers.get(handlerId);
        if (handler != null) {
            try {
                handler.handle(args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void registerCommand(ArrayList<String> commandNames, CommandHandler handler) throws Exception {
        if (this.commandManager == null) {
            cacheCommandManager();
        }

        String handlerId = "cmd_" + commandIdCounter++;
        commandHandlers.put(handlerId, handler);

        String className = "coffee.axle.myauinjectiontemplate.generated.Command_" + commandNames.get(0) + "_"
                + System.currentTimeMillis();
        byte[] classBytes = generateCommandClass(className, handlerId);

        ClassLoader loader = new FastClassLoader(getClass().getClassLoader(), className, classBytes);
        Class<?> generatedClass = loader.loadClass(className);

        Constructor<?> constructor = generatedClass.getDeclaredConstructor(ArrayList.class, String.class);
        constructor.setAccessible(true);
        Object commandInstance = constructor.newInstance(commandNames, handlerId);

        Field commandsField = getCachedField(this.commandManager.getClass(), "Z");
        ArrayList<Object> commands = (ArrayList<Object>) commandsField.get(this.commandManager);
        commands.add(commandInstance);
    }

    public boolean injectPropertyAfter(Object module, Object property, String afterPropertyName) {
        try {
            try {
                Class<?> moduleBaseClass = Class.forName("myau.mD");
                Method setOwnerMethod = property.getClass().getMethod("e", moduleBaseClass);
                setOwnerMethod.invoke(property, module);
            } catch (Exception ignored) {
            }

            Class<?> myauClass = Class.forName("myau.X");
            Field pmField = myauClass.getDeclaredField("e");
            pmField.setAccessible(true);
            Object pm = pmField.get(null);

            if (pm != null) {
                Field mapField = pm.getClass().getDeclaredField("o");
                mapField.setAccessible(true);
                Map<Class<?>, ArrayList<Object>> map = (Map<Class<?>, ArrayList<Object>>) mapField.get(pm);
                ArrayList<Object> props = map.computeIfAbsent(module.getClass(), k -> new ArrayList<>());
                int insertIndex = props.size();

                if (afterPropertyName != null) {
                    for (int i = 0; i < props.size(); i++) {
                        try {
                            String propName = (String) this.propertyGetNameMethod.invoke(props.get(i));
                            if (propName.equals(afterPropertyName)) {
                                insertIndex = i + 1;
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }

                props.add(insertIndex, property);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void registerProperties(Object module, Object... properties) throws Exception {
        if (properties.length == 0)
            return;

        Class<?> moduleBaseClass = getCachedClass("myau.mD");
        Method setOwnerMethod = null;

        for (Object prop : properties) {
            if (setOwnerMethod == null) {
                setOwnerMethod = prop.getClass().getMethod("e", moduleBaseClass);
            }
            setOwnerMethod.invoke(prop, module);
        }

        Class<?> myauClass = getCachedClass("myau.X");
        Field pmField = getCachedField(myauClass, "e");
        Object pm = pmField.get(null);

        if (pm != null) {
            Field mapField = getCachedField(pm.getClass(), "o");
            LinkedHashMap<Class<?>, ArrayList<Object>> propMap = (LinkedHashMap<Class<?>, ArrayList<Object>>) mapField
                    .get(pm);
            Class<?> moduleClass = module.getClass();
            ArrayList<Object> props = propMap.computeIfAbsent(moduleClass, k -> new ArrayList<>());
            Collections.addAll(props, properties);

            for (Object prop : properties) {
                try {
                    String propName = (String) this.propertyGetNameMethod.invoke(prop);
                    this.propertyCache.put(module.hashCode() + "." + propName, prop);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void setClientNamePrefix(String rawPrefix) {
        this.clientNamePrefix = rawPrefix;
    }

    public void setModuleEnabled(Object module, boolean enabled) throws Exception {
        Method setEnabledMethod = module.getClass().getMethod("f", short.class, boolean.class, short.class, int.class);
        setEnabledMethod.invoke(module, (short) 0, enabled, (short) 1, 0);
    }

    public void reloadModuleCommand() throws Exception {
        Class<?> myauClass = getCachedClass("myau.X");
        Field cmField = getCachedField(myauClass, "i");
        Object cm = cmField.get(null);
        Field commandsField = getCachedField(cm.getClass(), "Z");
        ArrayList<Object> commands = (ArrayList<Object>) commandsField.get(cm);
        Object moduleCommand = findModuleCommand(commands);

        if (moduleCommand == null) {
            throw new Exception("ModuleCommand not found");
        }

        Field mmField = getCachedField(myauClass, "j");
        Object mm = mmField.get(null);
        Field modulesField = getCachedField(mm.getClass(), "E");
        LinkedHashMap<?, ?> modules = (LinkedHashMap<?, ?>) modulesField.get(mm);
        Field namesField = moduleCommand.getClass().getField("n");
        ArrayList<String> names = (ArrayList<String>) namesField.get(moduleCommand);
        names.clear();
        names.ensureCapacity(modules.size());

        for (Object module : modules.values()) {
            String moduleName = (String) this.moduleGetNameMethod.invoke(module);
            names.add(moduleName);
        }

        MyauLogger.info("Reloaded ModuleCommand with " + names.size() + " modules");
    }

    private Object findModuleCommand(ArrayList<Object> commands) {
        try {
            Class<?> moduleCommandClass = getCachedClass("myau.KU");
            for (Object cmd : commands) {
                if (moduleCommandClass.isInstance(cmd)) {
                    return cmd;
                }
            }
        } catch (ClassNotFoundException ignored) {
        }

        for (Object cmd : commands) {
            try {
                Field namesField = cmd.getClass().getField("n");
                Object namesObj = namesField.get(cmd);
                if (namesObj instanceof ArrayList) {
                    ArrayList<?> names = (ArrayList<?>) namesObj;
                    if (names.contains("AimAssist")) {
                        return cmd;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public boolean isModuleEnabled(Object module) {
        try {
            Field enabledField = findFieldInHierarchy(module.getClass(), "p");
            if (enabledField != null) {
                enabledField.setAccessible(true);
                return enabledField.getBoolean(module);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public boolean isModuleEnabled(String moduleName) {
        Object module = findModule(moduleName);
        return module != null && isModuleEnabled(module);
    }

    public Object createFloatProperty(String name, float defaultValue, float min, float max) throws Exception {
        Class<?> floatPropertyClass = getCachedClass("myau.P");
        Constructor<?> constructor = null;

        for (Constructor<?> c : floatPropertyClass.getDeclaredConstructors()) {
            Class<?>[] params = c.getParameterTypes();
            if (params.length == 5
                    && params[0] == long.class
                    && params[1] == String.class
                    && params[2] == Float.class
                    && params[3] == Float.class
                    && params[4] == Float.class) {
                constructor = c;
                break;
            }
        }

        if (constructor == null) {
            throw new NoSuchMethodException("Could not find FloatProperty constructor");
        }

        constructor.setAccessible(true);
        return constructor.newInstance(0L, name, defaultValue, min, max);
    }

    public Object createStringProperty(String name, String defaultValue) throws Exception {
        Class<?> stringPropertyClass = getCachedClass("myau.d");
        Constructor<?> constructor = null;

        for (Constructor<?> c : stringPropertyClass.getDeclaredConstructors()) {
            Class<?>[] params = c.getParameterTypes();
            if (params.length == 2 && params[0] == String.class && params[1] == String.class) {
                constructor = c;
                break;
            }

            if (params.length == 3) {
                if (params[0] == String.class && params[1] == String.class) {
                    constructor = c;
                    break;
                }
                if (params[0] == long.class && params[1] == String.class && params[2] == String.class) {
                    constructor = c;
                    break;
                }
            }
        }

        if (constructor == null) {
            throw new NoSuchMethodException("Could not find StringProperty constructor");
        }

        constructor.setAccessible(true);
        Class<?>[] params = constructor.getParameterTypes();
        if (params.length == 2) {
            return constructor.newInstance(name, defaultValue);
        } else if (params.length == 3) {
            return params[0] == long.class
                    ? constructor.newInstance(0L, name, defaultValue)
                    : constructor.newInstance(name, defaultValue, 0L);
        } else {
            throw new NoSuchMethodException("Unexpected StringProperty constructor signature");
        }
    }

    public Object createBooleanProperty(String name, boolean defaultValue) throws Exception {
        Class<?> booleanPropertyClass = getCachedClass("myau.l");
        Constructor<?> constructor = booleanPropertyClass.getDeclaredConstructor(String.class, Boolean.class,
                long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(name, defaultValue, 0L);
    }

    public Object createIntegerProperty(String name, int defaultValue, int min, int max) throws Exception {
        Class<?> intPropertyClass = getCachedClass("myau.c");
        Constructor<?> targetCtor = null;
        int[] intParamIdx = null;

        for (Constructor<?> ctor : intPropertyClass.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length >= 4 && params[0] == String.class) {
                List<Integer> intIndices = new ArrayList<>();
                for (int i = 0; i < params.length; i++) {
                    if (params[i] == Integer.class || params[i] == int.class) {
                        intIndices.add(i);
                    }
                }
                if (intIndices.size() >= 3) {
                    targetCtor = ctor;
                    intParamIdx = new int[] { intIndices.get(0), intIndices.get(1), intIndices.get(2) };
                    break;
                }
            }
        }

        if (targetCtor == null) {
            throw new NoSuchMethodException("No suitable IntProperty constructor found in myau.c");
        }

        targetCtor.setAccessible(true);
        Class<?>[] params = targetCtor.getParameterTypes();
        Object[] args = new Object[params.length];
        args[0] = name;
        args[intParamIdx[0]] = defaultValue;
        args[intParamIdx[1]] = min;
        args[intParamIdx[2]] = max;

        for (int i = 1; i < params.length; i++) {
            if (args[i] == null) {
                Class<?> t = params[i];
                if (t == boolean.class) {
                    args[i] = false;
                } else if (t == int.class) {
                    args[i] = 0;
                } else if (t == long.class) {
                    args[i] = 0L;
                } else {
                    args[i] = null;
                }
            }
        }

        return targetCtor.newInstance(args);
    }

    public Object createEnumProperty(String name, int defaultValue, String[] values) throws Exception {
        Class<?> enumPropertyClass = getCachedClass("myau.n");
        Constructor<?> constructor = enumPropertyClass.getDeclaredConstructor(String.class, long.class, Integer.class,
                String[].class);
        constructor.setAccessible(true);
        return constructor.newInstance(name, 0L, defaultValue, values);
    }

    public Object findModule(String name) {
        Object cached = this.moduleCache.get(name);
        if (cached != null) {
            return cached;
        }

        for (Object module : this.modulesMap.values()) {
            try {
                String moduleName = (String) this.moduleGetNameMethod.invoke(module);
                if (moduleName.equals(name)) {
                    this.moduleCache.put(name, module);
                    return module;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public Object findProperty(Object module, String propertyName) {
        String key = module.hashCode() + "." + propertyName;
        Object cached = this.propertyCache.get(key);
        if (cached != null) {
            return cached;
        }

        try {
            for (Field f : module.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object prop = f.get(module);
                if (prop != null) {
                    try {
                        String name = (String) this.propertyGetNameMethod.invoke(prop);
                        if (name.equals(propertyName)) {
                            this.propertyCache.put(key, prop);
                            return prop;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Object getPropertyValue(Object property) throws Exception {
        return this.propertyGetValueMethod.invoke(property);
    }

    public String getPropertyName(Object property) throws Exception {
        return (String) this.propertyGetNameMethod.invoke(property);
    }

    public Object getModuleManager() {
        return this.moduleManager;
    }

    public LinkedHashMap<Class<?>, Object> getModulesMap() {
        return this.modulesMap;
    }

    public String getClientName() throws Exception {
        Class<?> myauClass = getCachedClass("myau.X");
        Field clientNameField = getCachedField(myauClass, "R");
        return (String) clientNameField.get(null);
    }

    public String getClientVersion() {
        try {
            Class<?> myauClass = getCachedClass("myau.X");
            InputStreamReader reader = new InputStreamReader(myauClass.getResourceAsStream("/mcmod.info"),
                    StandardCharsets.UTF_8);
            JsonArray arr = new JsonParser().parse(reader).getAsJsonArray();
            JsonObject modInfo = arr.get(0).getAsJsonObject();
            String version = modInfo.get("version").getAsString();
            reader.close();
            return version;
        } catch (Exception e) {
            MyauLogger.error("HOOK_FAIL", e);
            return "Unknown";
        }
    }

    public int getModuleCount() {
        return this.modulesMap != null ? this.modulesMap.size() : 0;
    }

    public int getCommandCount() {
        try {
            if (this.commandManager == null) {
                cacheCommandManager();
            }
            Field commandsField = getCachedField(this.commandManager.getClass(), "Z");
            ArrayList<?> commands = (ArrayList<?>) commandsField.get(this.commandManager);
            return commands.size();
        } catch (Exception e) {
            MyauLogger.error("HOOK_FAIL", e);
            return 0;
        }
    }

    public String getUsername() {
        if (this.cachedUsername != null) {
            return this.cachedUsername;
        }

        try {
            Class<?> myauClass = getCachedClass("myau.X");
            Field[] fields = myauClass.getDeclaredFields();
            for (Field f : fields) {
                if (f.getType() == String.class) {
                    f.setAccessible(true);
                    Object val = f.get(null);
                    if (val != null && val.toString().length() > 0 && val.toString().length() <= 16) {
                        this.cachedUsername = val.toString();
                        return this.cachedUsername;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        this.cachedUsername = "Unknown";
        return this.cachedUsername;
    }

    public int getConfigCount() {
        try {
            File configDir = new File(mc.mcDataDir, "config/myau");
            if (configDir.exists() && configDir.isDirectory()) {
                String[] files = configDir.list((dir, name) -> name.endsWith(".json"));
                return files != null ? files.length : 0;
            } else {
                return 0;
            }
        } catch (Exception e) {
            MyauLogger.error("HOOK_FAIL", e);
            return 0;
        }
    }

    public Method getPropertyGetValueMethod() {
        return this.propertyGetValueMethod;
    }

    public void clearCaches() {
        this.moduleCache.clear();
        this.propertyCache.clear();
        this.fieldCache.clear();
    }

    private byte[] generateModuleClass(String className) {
        if (this.cachedClassWriter == null) {
            this.cachedClassWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        }

        ClassWriter cw = this.cachedClassWriter;
        String internalName = className.replace('.', '/');
        cw.visit(52, 1, internalName, null, "myau/mD", null);

        MethodVisitor mv = cw.visitMethod(1, "<init>", "(Ljava/lang/String;ZIJ)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(25, 0);
        mv.visitVarInsn(25, 1);
        mv.visitVarInsn(21, 2);
        mv.visitVarInsn(21, 3);
        mv.visitVarInsn(22, 4);
        mv.visitMethodInsn(183, "myau/mD", "<init>", "(Ljava/lang/String;ZIJ)V", false);
        mv.visitInsn(177);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(1, "h", "(SIC)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(25, 0);
        mv.visitVarInsn(21, 1);
        mv.visitVarInsn(21, 2);
        mv.visitVarInsn(21, 3);
        mv.visitMethodInsn(183, "myau/mD", "h", "(SIC)V", false);
        mv.visitVarInsn(25, 0);
        mv.visitMethodInsn(184, "coffee/axle/myauinjectiontemplate/hooks/ClientHook", "triggerOnEnable",
                "(Ljava/lang/Object;)V", false);
        mv.visitInsn(177);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(1, "S", "(J)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(25, 0);
        mv.visitVarInsn(22, 1);
        mv.visitMethodInsn(183, "myau/mD", "S", "(J)V", false);
        mv.visitVarInsn(25, 0);
        mv.visitMethodInsn(184, "coffee/axle/myauinjectiontemplate/hooks/ClientHook", "triggerOnDisable",
                "(Ljava/lang/Object;)V", false);
        mv.visitInsn(177);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        byte[] bytes = cw.toByteArray();
        this.cachedClassWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        return bytes;
    }

    private byte[] generateCommandClass(String className, String handlerId) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        String internalName = className.replace('.', '/');
        String superClassName = this.commandBaseClass.getName().replace('.', '/');

        cw.visit(52, 1, internalName, null, superClassName, null);
        cw.visitField(18, "handlerId", "Ljava/lang/String;", null, null);

        MethodVisitor mv = cw.visitMethod(1, "<init>", "(Ljava/util/ArrayList;Ljava/lang/String;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(25, 0);
        mv.visitVarInsn(25, 1);
        mv.visitMethodInsn(183, superClassName, "<init>", "(Ljava/util/ArrayList;)V", false);
        mv.visitVarInsn(25, 0);
        mv.visitVarInsn(25, 2);
        mv.visitFieldInsn(181, internalName, "handlerId", "Ljava/lang/String;");
        mv.visitInsn(177);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(1, "J", "(Ljava/util/ArrayList;J)V", "(Ljava/util/ArrayList<Ljava/lang/String;>;J)V", null);
        mv.visitCode();
        mv.visitVarInsn(25, 0);
        mv.visitFieldInsn(180, internalName, "handlerId", "Ljava/lang/String;");
        mv.visitVarInsn(25, 1);
        mv.visitMethodInsn(184, "coffee/axle/myauinjectiontemplate/hooks/ClientHook", "invokeHandler",
                "(Ljava/lang/String;Ljava/util/ArrayList;)V", false);
        mv.visitInsn(177);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    public void registerEventHandler(String eventClassName, EventHandler handler, byte priority) throws Exception {
        String className = "coffee.axle.myauinjectiontemplate.generated.EventHandler_" + System.currentTimeMillis()
                + "_" + Math.random();
        byte[] classBytes = generateEventHandlerClass(className, eventClassName, priority);
        ClassLoader loader = new FastClassLoader(getClass().getClassLoader(), className, classBytes);
        Class<?> handlerClass = loader.loadClass(className);
        Constructor<?> constructor = handlerClass.getDeclaredConstructor(EventHandler.class);
        constructor.setAccessible(true);
        Object handlerInstance = constructor.newInstance(handler);
        Class<?> eventBusClass = getCachedClass("myau.mE");
        Method registerMethod = eventBusClass.getMethod("c", Object.class);
        registerMethod.invoke(null, handlerInstance);
    }

    private byte[] generateEventHandlerClass(String className, String eventClass, byte priority) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        String internalName = className.replace('.', '/');
        String eventInternalName = eventClass.replace('.', '/');

        cw.visit(52, 1, internalName, null, "java/lang/Object", null);
        cw.visitField(18, "handler", "Lcoffee/axle/myauinjectiontemplate/hooks/ClientHook$EventHandler;", null, null);

        MethodVisitor mv = cw.visitMethod(1, "<init>",
                "(Lcoffee/axle/myauinjectiontemplate/hooks/ClientHook$EventHandler;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(25, 0);
        mv.visitMethodInsn(183, "java/lang/Object", "<init>", "()V", false);
        mv.visitVarInsn(25, 0);
        mv.visitVarInsn(25, 1);
        mv.visitFieldInsn(181, internalName, "handler",
                "Lcoffee/axle/myauinjectiontemplate/hooks/ClientHook$EventHandler;");
        mv.visitInsn(177);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(1, "onEvent", "(L" + eventInternalName + ";)V", null, null);
        AnnotationVisitor av = mv.visitAnnotation("Lmyau/mB;", true);
        av.visit("value", priority);
        av.visitEnd();
        mv.visitCode();
        mv.visitVarInsn(25, 0);
        mv.visitFieldInsn(180, internalName, "handler",
                "Lcoffee/axle/myauinjectiontemplate/hooks/ClientHook$EventHandler;");
        mv.visitVarInsn(25, 1);
        mv.visitMethodInsn(185, "coffee/axle/myauinjectiontemplate/hooks/ClientHook$EventHandler", "handleEvent",
                "(Ljava/lang/Object;)V", true);
        mv.visitInsn(177);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    public int getModuleKeybind(Object module) {
        try {
            Field keyField = findFieldInHierarchy(module.getClass(), "P");
            if (keyField != null) {
                return keyField.getInt(module);
            }
        } catch (Exception e) {
            MyauLogger.error("HOOK_FAIL", e);
        }
        return -1;
    }

    public boolean setModuleKeybind(Object module, int keyCode) {
        try {
            Field keyField = findFieldInHierarchy(module.getClass(), "P");
            if (keyField != null) {
                keyField.setInt(module, keyCode);
                return true;
            }
        } catch (Exception e) {
            MyauLogger.error("HOOK_FAIL", e);
        }
        return false;
    }

    public void registerModuleCallbacks(Object moduleInstance, Runnable onEnable, Runnable onDisable) {
        try {
            Class<?> moduleClass = moduleInstance.getClass();
            Method onEnableMethod = findMethodInHierarchy(moduleClass, "h", short.class, int.class, char.class);
            if (onEnableMethod == null) {
                return;
            }

            Method onDisableMethod = findMethodInHierarchy(moduleClass, "S", long.class);
            if (onDisableMethod == null) {
                return;
            }

            String key = moduleInstance.getClass().getName() + "@" + System.identityHashCode(moduleInstance);
            this.moduleEnableCallbacks.put(key, onEnable);
            this.moduleDisableCallbacks.put(key, onDisable);
        } catch (Exception e) {
            MyauLogger.error("HOOK_FAIL", e);
            e.printStackTrace();
        }
    }

    public static void triggerOnEnable(Object moduleInstance) {
        String key = moduleInstance.getClass().getName() + "@" + System.identityHashCode(moduleInstance);
        Runnable callback = getInstance().moduleEnableCallbacks.get(key);
        if (callback != null) {
            try {
                callback.run();
            } catch (Exception e) {
                System.err.println("[Myau+] Error in onEnable callback: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static void triggerOnDisable(Object moduleInstance) {
        String key = moduleInstance.getClass().getName() + "@" + System.identityHashCode(moduleInstance);
        Runnable callback = getInstance().moduleDisableCallbacks.get(key);
        if (callback != null) {
            try {
                callback.run();
            } catch (Exception e) {
                System.err.println("[Myau+] Error in onDisable callback: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private String getModuleName(Object module) {
        try {
            return (String) this.moduleGetNameMethod.invoke(module);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public interface CommandHandler {
        void handle(ArrayList<String> args);
    }

    public interface EventHandler {
        void handleEvent(Object event);
    }

    private static class FastClassLoader extends ClassLoader {
        private final String targetClassName;
        private final byte[] classBytes;

        FastClassLoader(ClassLoader parent, String className, byte[] bytes) {
            super(parent);
            this.targetClassName = className;
            this.classBytes = bytes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(this.targetClassName)) {
                return defineClass(name, this.classBytes, 0, this.classBytes.length);
            }
            return super.findClass(name);
        }
    }
}
