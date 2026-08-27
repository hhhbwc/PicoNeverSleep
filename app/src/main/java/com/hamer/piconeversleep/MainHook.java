package com.hamer.piconeversleep;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Constructor;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Implementation of a "Never Sleep" quick setting button for Pico VR.
 * https://github.com/hhhbwc/pico4-sleep-mode/blob/main/mod_vsleep/src/com/picoxr/vsleep/VSleepHook.java
 */
public final class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "PicoNeverSleep";
    private static final String SETTINGS_PACKAGE = "com.picovr.settings";
    private static final int NEVER_SLEEP_TILE = 9002;
    private static final String TILE_ADDED_KEY = "pico_neversleep_quick_added";
    private static final String TILE_INDEX_KEY = "pico_neversleep_quick_index";
    private static final String SETTING_NEVER_SLEEP = "pvr_never_sleep_enabled";
    private static final String PROP_NEVER_SLEEP_VOLATILE = "pvr.factorytest.never.sleep";
    private static final String MODULE_PACKAGE = "com.hamer.piconeversleep";
    
    private static volatile Object sButton;
    private static final ThreadLocal<Object> MAPPED_BIND_ITEM = new ThreadLocal<>();

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if ("android".equals(lp.packageName)) {
            XposedBridge.log(TAG + ": Hooking android");
            hookSystemReady(lp);
        }

        if (!SETTINGS_PACKAGE.equals(lp.packageName)) return;
        
        XposedBridge.log(TAG + ": Hooking " + lp.packageName);

        try {
            final Class<?> adapterClass = XposedHelpers.findClass("com.picovr.quicksettings.ButtonListAdapter", lp.classLoader);

            // Hook getItemViewType to return 1 for our custom tile type
            XposedHelpers.findAndHookMethod(adapterClass, "getItemViewType", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        List<?> data = (List<?>) XposedHelpers.getObjectField(p.thisObject, "a");
                        int position = (Integer) p.args[0];
                        if (position >= 0 && position < data.size()
                                && buttonType(data.get(position)) == NEVER_SLEEP_TILE) {
                            p.setResult(1);
                        }
                    } catch (Throwable t) {
                        // Silently fail to avoid crashing the settings app
                    }
                }
            });

            // Hook onBindViewHolder to configure our custom tile
            XposedHelpers.findAndHookMethod(adapterClass, "onBindViewHolder", 
                "androidx.recyclerview.widget.RecyclerView$ViewHolder", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    MAPPED_BIND_ITEM.remove();
                    try {
                        List<?> data = (List<?>) XposedHelpers.getObjectField(p.thisObject, "a");
                        int position = (Integer) p.args[1];
                        if (position >= 0 && position < data.size()) {
                            Object item = data.get(position);
                            if (buttonType(item) == NEVER_SLEEP_TILE) {
                                MAPPED_BIND_ITEM.set(item);
                                XposedHelpers.callMethod(item, "m", 1);
                            }
                        }
                    } catch (Throwable t) {
                        MAPPED_BIND_ITEM.remove();
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    int position = (Integer) p.args[1];
                    try {
                        configureButton(p.thisObject, p.args[0], position);
                    } finally {
                        Object mapped = MAPPED_BIND_ITEM.get();
                        MAPPED_BIND_ITEM.remove();
                        if (mapped != null) {
                            try { XposedHelpers.callMethod(mapped, "m", NEVER_SLEEP_TILE); } catch (Throwable ignored) {}
                        }
                    }
                }
            });

            // Hook the list update method to ensure no duplicates
            XposedHelpers.findAndHookMethod(adapterClass, "b", ArrayList.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    ArrayList<?> list = (ArrayList<?>) XposedHelpers.getObjectField(p.thisObject, "a");
                    if (list == null) list = (ArrayList<?>) p.args[0];
                    removeDuplicates(list);
                    repositionTile(list);
                }
            });

            // Hook QuickSettingUtils to inject our tile into the loaded list
            final Class<?> utilsClass = XposedHelpers.findClass("com.picovr.quicksettings.utils.QuickSettingUtils", lp.classLoader);
            final Class<?> loadCallback = XposedHelpers.findClass("com.picovr.quicksettings.utils.QuickSettingUtils$LoadButtonsCallBack", lp.classLoader);
            XposedHelpers.findAndHookMethod(utilsClass, "b", Context.class, loadCallback, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    p.args[1] = quickPanelCallback(p.args[1], loadCallback, lp.classLoader);
                }
            });
            installEditorHooks(lp.classLoader);

            XposedBridge.log(TAG + ": Hooks installed successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to install hooks: " + t);
        }
    }

    private void installEditorHooks(final ClassLoader cl) {
        try {
            final Class<?> fragment = XposedHelpers.findClass("com.picovr.fragments.QuickPanelFragment", cl);
            final Class<?> manager = XposedHelpers.findClass("com.picovr.database.quickpanel.QuickPanelManager", cl);
            final Class<?> callback = XposedHelpers.findClass("com.picovr.listener.ResultCallback", cl);
            final Class<?> added = XposedHelpers.findClass("com.picovr.adapters.QuickPanelAddedAdapter", cl);
            final Class<?> more = XposedHelpers.findClass("com.picovr.adapters.QuickPanelMoreAdapter", cl);
            XposedHelpers.findAndHookMethod(manager, "A", List.class, callback, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) { p.args[0] = saveEditorTile((List<?>) p.args[0]); }
            });
            XposedHelpers.findAndHookMethod(fragment, "I", List.class, List.class, boolean.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) { addEditorTile((List<?>) p.args[0], (List<?>) p.args[1], cl); }
            });
            XC_MethodHook bind = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { bindEditorTile(p.thisObject, p.args[0], (Integer) p.args[1]); }
            };
            XposedHelpers.findAndHookMethod(added, "m", XposedHelpers.findClass("com.picovr.adapters.QuickPanelAddedAdapter$AddedHolder", cl), int.class, bind);
            XposedHelpers.findAndHookMethod(more, "c", XposedHelpers.findClass("com.picovr.adapters.QuickPanelMoreAdapter$MoreHolder", cl), int.class, bind);
            XposedBridge.log(TAG + ": editor hooks installed");
        } catch (Throwable t) { XposedBridge.log(TAG + ": editor hooks unavailable " + t); }
    }

    private static Object currentApplication() throws Exception {
        return Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
    }
    private static int panelType(Object item) { try { return ((Integer) item.getClass().getMethod("f").invoke(item)).intValue(); } catch (Throwable t) { return -1; } }
    private static int panelIndex(Object item) { try { return ((Integer) item.getClass().getMethod("d").invoke(item)).intValue(); } catch (Throwable t) { return 0; } }
    private static boolean panelAdded(Object item) { try { return ((Boolean) item.getClass().getMethod("g").invoke(item)).booleanValue(); } catch (Throwable t) { return false; } }
    private static int panelName(Object item) { try { return ((Integer) item.getClass().getMethod("e").invoke(item)).intValue(); } catch (Throwable t) { return 0; } }
    private static int panelIcon(Object item) { try { return ((Integer) item.getClass().getMethod("b").invoke(item)).intValue(); } catch (Throwable t) { return 0; } }
    private static void addEditorTile(List<?> added, List<?> more, ClassLoader cl) {
        try {
            Object context = currentApplication();
            int state = getGlobalInt((Context) context, TILE_ADDED_KEY, 1);
            List target = state == 1 ? added : more;
            for (Object item : added) if (panelType(item) == NEVER_SLEEP_TILE) return;
            for (Object item : more) if (panelType(item) == NEVER_SLEEP_TILE) return;
            Object template = !added.isEmpty() ? added.get(0) : (more.isEmpty() ? null : more.get(0));
            if (template == null) return;
            Class<?> itemClass = XposedHelpers.findClass("com.picovr.database.quickpanel.QuickPanelItem", cl);
            Constructor<?> c = itemClass.getConstructor(int.class,int.class,int.class,int.class,int.class,String.class);
            int index = clampIndex(getGlobalInt((Context) context, TILE_INDEX_KEY, target.size()), target.size());
            target.add(index, c.newInstance(NEVER_SLEEP_TILE,index,state,panelName(template),panelIcon(template),"neversleep"));
            XposedBridge.log(TAG + ": editor tile added state=" + state + " index=" + index + " addedSize=" + added.size() + " moreSize=" + more.size());
        } catch (Throwable t) { XposedBridge.log(TAG + ": editor tile injection failed " + t); }
    }
    private static List<?> saveEditorTile(List<?> list) {
        try {
            Object context = currentApplication(); ArrayList copy = new ArrayList(list);
            for (Object item : list) if (panelType(item) == NEVER_SLEEP_TILE) {
                putGlobalInt((Context) context, TILE_ADDED_KEY, panelAdded(item) ? 1 : 0);
                int savedIndex = list.indexOf(item);
                putGlobalInt((Context) context, TILE_INDEX_KEY, savedIndex);
                XposedBridge.log(TAG + ": saved editable tile state added=" + (panelAdded(item) ? 1 : 0) + " index=" + savedIndex + " itemIndex=" + panelIndex(item));
                copy.remove(item);
            }
            return copy;
        } catch (Throwable t) { return list; }
    }
    private static void bindEditorTile(Object adapter, Object holder, int position) {
        try {
            java.lang.reflect.Field data = adapter.getClass().getDeclaredField("b");
            data.setAccessible(true);
            List<?> list = (List<?>) data.get(adapter);
            if (position < 0 || position >= list.size() || panelType(list.get(position)) != NEVER_SLEEP_TILE) return;
            boolean isAdded = holder.getClass().getName().contains("Added");
            java.lang.reflect.Field text = holder.getClass().getDeclaredField(isAdded ? "d" : "c");
            text.setAccessible(true);
            Object label = text.get(holder);
            label.getClass().getMethod("setText", CharSequence.class).invoke(label, "Never Sleep");
            java.lang.reflect.Field image = holder.getClass().getDeclaredField(isAdded ? "c" : "b");
            image.setAccessible(true);
            Object imageView = image.get(holder);
            Drawable drawable = getModuleDrawable((Context) currentApplication());
            if (drawable != null) imageView.getClass().getMethod("setImageDrawable", Drawable.class).invoke(imageView, drawable);
        } catch (Throwable ignored) {}
    }

    private void hookSystemReady(XC_LoadPackage.LoadPackageParam lp) {
        try {
            XposedHelpers.findAndHookMethod("com.android.server.SystemServiceManager", lp.classLoader,
                    "startBootPhase", int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int phase = (int) param.args[0];
                            if (phase == 600 || phase == 1000) { // PHASE_BOOT_COMPLETED or PHASE_SYSTEM_SERVICES_READY
                                XposedBridge.log(TAG + ": Boot phase " + phase + " reached, syncing props");
                                final Context context;
                                Object mContext = XposedHelpers.getObjectField(param.thisObject, "mContext");
                                if (mContext instanceof Context) {
                                    context = (Context) mContext;
                                } else {
                                     context = null;
                                }

                                if (context != null) {
                                    syncProps(context);
                                    // Also sync after a short delay to override vendor resets
                                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            XposedBridge.log(TAG + ": Delayed sync after boot");
                                            syncProps(context);
                                        }
                                    }, 10000); // 10 seconds delay
                                }
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to hook startBootPhase: " + t);
        }
    }

    private Object quickPanelCallback(final Object original, Class<?> callbackClass, final ClassLoader cl) {
        return Proxy.newProxyInstance(cl, new Class<?>[]{callbackClass}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("a".equals(method.getName()) && args != null && args.length == 1) {
                            ArrayList<Object> list = (ArrayList<Object>) args[0];
                    Context context = null;
                    try { context = (Context) currentApplication(); } catch (Throwable ignored) {}
                    if (context != null && getGlobalInt(context, TILE_ADDED_KEY, 1) == 1
                            && !hasTile(list, NEVER_SLEEP_TILE)) {
                        try {
                            Class<?> infoClass = XposedHelpers.findClass("com.picovr.quicksettings.button.QuickSettingButtonInfo", cl);
                            Object item = XposedHelpers.newInstance(infoClass);
                            XposedHelpers.callMethod(item, "m", NEVER_SLEEP_TILE);
                            int index = clampIndex(getGlobalInt(context, TILE_INDEX_KEY, 0), list.size());
                            list.add(index, item);
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + ": Tile injection failed: " + t);
                        }
                    }
                }
                return method.invoke(original, args);
            }
        });
    }

    private boolean hasTile(List<?> list, int type) {
        try {
            for (Object item : list) {
                if ((Integer) XposedHelpers.callMethod(item, "f") == type) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void removeDuplicates(ArrayList<?> list) {
        boolean seen = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            try {
                if (buttonType(list.get(i)) == NEVER_SLEEP_TILE) {
                    if (seen) list.remove(i);
                    else seen = true;
                }
            } catch (Throwable ignored) {}
        }
    }

    private void repositionTile(ArrayList list) {
        try {
            Context context = (Context) currentApplication();
            int wanted = clampIndex(getGlobalInt(context, TILE_INDEX_KEY, 0), list.size() - 1);
            for (int i = 0; i < list.size(); i++) {
                if (buttonType(list.get(i)) == NEVER_SLEEP_TILE) {
                    Object item = list.remove(i);
                    list.add(wanted, item);
                    XposedBridge.log(TAG + ": runtime tile positioned=" + wanted);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static int clampIndex(int index, int size) {
        return Math.max(0, Math.min(index, Math.max(0, size)));
    }

    private static int buttonType(Object info) {
        try { return ((Integer) XposedHelpers.callMethod(info, "f")).intValue(); }
        catch (Throwable ignored) { return -1; }
    }

    private void configureButton(Object adapter, Object holder, int position) {
        try {
            List<?> data = (List<?>) XposedHelpers.getObjectField(adapter, "a");
            if (position < 0 || position >= data.size()) return;
            Object item = data.get(position);
            if (MAPPED_BIND_ITEM.get() != item && buttonType(item) != NEVER_SLEEP_TILE) return;
            XposedBridge.log(TAG + ": binding quick tile position=" + position + " type=" + buttonType(item));

            final View button = (View) XposedHelpers.getObjectField(holder, "a");
            final Context context = button.getContext();
            
            View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleNeverSleep(context);
                    refreshTile(context);
                }
            };
            button.setOnClickListener(listener);
            button.setClickable(true);
            View itemView = (View) XposedHelpers.getObjectField(holder, "itemView");
            itemView.setOnClickListener(listener);
            itemView.setClickable(true);
            button.postDelayed(new Runnable() {
                @Override
                public void run() {
                    button.setOnClickListener(listener);
                    itemView.setOnClickListener(listener);
                }
            }, 100L);

            sButton = button;
            XposedBridge.log(TAG + ": quick tile configured position=" + position);
            syncProps(context);
            button.post(new Runnable() {
                @Override
                public void run() {
                    refreshTile(context);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": configureButton failed: " + t);
        }
    }

    private void toggleNeverSleep(Context context) {
        boolean enabled = isNeverSleepEnabled(context);
        String val = enabled ? "0" : "1";
        putGlobalInt(context, SETTING_NEVER_SLEEP, enabled ? 0 : 1);
        setProp(PROP_NEVER_SLEEP_VOLATILE, val);
        XposedBridge.log(TAG + ": Never Sleep toggled to " + (!enabled));
    }

    private void syncProps(Context context) {
        try {
            int enabled = getGlobalInt(context, SETTING_NEVER_SLEEP, 0);
            String val = String.valueOf(enabled);
            String volatileVal = getProp(PROP_NEVER_SLEEP_VOLATILE, "0");
            if (!val.equals(volatileVal)) {
                setProp(PROP_NEVER_SLEEP_VOLATILE, val);
                XposedBridge.log(TAG + ": Synced volatile prop to " + val);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": syncProps failed: " + t);
        }
    }

    private boolean isNeverSleepEnabled(Context context) {
        return getGlobalInt(context, SETTING_NEVER_SLEEP, 0) == 1;
    }

    private void refreshTile(Context context) {
        try {
            Object button = sButton;
            if (button == null) return;

            boolean enabled = isNeverSleepEnabled(context);
            // 'h' likely sets the active/checked state of the button
            XposedHelpers.callMethod(button, "h", enabled);
            XposedHelpers.callMethod(button, "setTipText", getModuleString(context, "never_sleep"));

            ImageView iconView = findImageView((View) button);
            if (iconView != null) {
                Drawable drawable = getModuleDrawable(context);
                if (drawable != null) {
                    iconView.setBackground(null);
                    iconView.setImageDrawable(drawable);
                    
                    ViewGroup.LayoutParams lp = iconView.getLayoutParams();
                    if (lp != null) {
                        float density = context.getResources().getDisplayMetrics().density;
                        lp.width = (int) (55 * density);
                        lp.height = (int) (55 * density);
                        iconView.setLayoutParams(lp);
                    }
                    iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": refreshTile failed: " + t);
        }
    }

    private ImageView findImageView(View view) {
        if (view instanceof ImageView) return (ImageView) view;
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                ImageView found = findImageView(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Drawable getModuleDrawable(Context context) {
        try {
            Context moduleContext = context.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
            int id = moduleContext.getResources().getIdentifier("ic_launcher_foreground", "mipmap", MODULE_PACKAGE);
            if (id == 0) id = moduleContext.getResources().getIdentifier("ic_launcher", "mipmap", MODULE_PACKAGE);
            if (id == 0) id = moduleContext.getResources().getIdentifier("ic_launcher", "drawable", MODULE_PACKAGE);
            if (id != 0) return moduleContext.getResources().getDrawable(id, null);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to load module icon: " + t);
        }
        return null;
    }

    private String getModuleString(Context context, String name) {
        try {
            Context moduleContext = context.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
            int id = moduleContext.getResources().getIdentifier(name, "string", MODULE_PACKAGE);
            if (id != 0) return moduleContext.getString(id);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to load module string: " + t);
        }
        return "Never Sleep";
    }

    private String getProp(String key, String def) {
        try {
            return (String) XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.os.SystemProperties", null), "get", key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private void setProp(String key, String val) {
        try {
            XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.os.SystemProperties", null), "set", key, val);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": setProp failed: " + t);
        }
    }

    private static int getGlobalInt(Context c, String k, int d) {
        try {
            return (Integer) XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.provider.Settings$Global", null),
                "getInt", c.getContentResolver(), k, d);
        } catch (Throwable t) {
            return d;
        }
    }

    private static void putGlobalInt(Context c, String k, int v) {
        try {
            XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.provider.Settings$Global", null),
                "putInt", c.getContentResolver(), k, v);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": setting write failed " + k + ": " + t);
        }
    }
}
