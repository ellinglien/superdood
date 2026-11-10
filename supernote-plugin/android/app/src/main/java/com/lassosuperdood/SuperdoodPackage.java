package com.lassosuperdood;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;
import java.util.Collections;
import java.util.List;

/** The plugin's one native module ({@link SuperdoodModule}); PluginHost finds it through PluginConfig's reactPackages. */
public class SuperdoodPackage implements ReactPackage {
    @Override public List<NativeModule> createNativeModules(ReactApplicationContext context) { return Collections.singletonList(new SuperdoodModule(context)); }
    @Override public List<ViewManager> createViewManagers(ReactApplicationContext context) { return Collections.emptyList(); }
}
