package su.kaoyu.telegram;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Spannable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;

import com.amulyakhare.textdrawable.TextDrawable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.*;

public class Xposed implements IXposedHookLoadPackage {

    ThreadLocal<String> mRow = new ThreadLocal<>();

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if (!"org.telegram.messenger".equals(loadPackageParam.packageName)) {
            return;
        }

        Class<?> BuildConfigClass = findClass("org.telegram.messenger.BuildConfig", loadPackageParam.classLoader);
        Field versionCodeField = findField(BuildConfigClass, "VERSION_CODE");
        int versionCode = versionCodeField.getInt(null);

        final Class<?> EmojiClass = findClass("org.telegram.android.Emoji", loadPackageParam.classLoader);


        XC_MethodReplacement replaceEmoji_methodReplacement = new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                XSharedPreferences preferences = new XSharedPreferences(loadPackageParam.packageName, "mainconfig");
                if (preferences.getBoolean("use_android_emoji", true)) {
                    CharSequence cs = ((CharSequence) methodHookParam.args[0]);
                    if (cs == null || cs.length() == 0) {
                        return cs;
                    }
                    Spannable s;
                    if (cs instanceof Spannable) {
                        s = (Spannable) cs;
                    } else {
                        s = Spannable.Factory.getInstance().newSpannable(cs);
                    }
                    return s;
                }
                return XposedBridge.invokeOriginalMethod(methodHookParam.method, methodHookParam.thisObject, methodHookParam.args);
            }
        };

        if (versionCode < 578) {
            findAndHookMethod(EmojiClass,
                    "replaceEmoji", CharSequence.class, Paint.FontMetricsInt.class, int.class,
                    replaceEmoji_methodReplacement);
        } else {
            findAndHookMethod(EmojiClass,
                    "replaceEmoji", CharSequence.class, Paint.FontMetricsInt.class, int.class, boolean.class,
                    replaceEmoji_methodReplacement);
        }

        //表情页面替换
        try {
            final Class<?> EmojiGridAdapter = findClass("org.telegram.ui.Components.EmojiView.EmojiGridAdapter", loadPackageParam.classLoader);

            findAndHookMethod(EmojiGridAdapter, "getView", int.class, View.class, ViewGroup.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(final MethodHookParam methodHookParam) throws Throwable {
                            Object o = XposedBridge.invokeOriginalMethod(methodHookParam.method, methodHookParam.thisObject, methodHookParam.args);

                            XSharedPreferences preferences = new XSharedPreferences(loadPackageParam.packageName, "mainconfig");
                            if (!preferences.getBoolean("use_android_emoji", true)) {
                                return o;
                            }
                            if (o instanceof ImageView) {
                                ImageView imageView = (ImageView) o;
                                String convert = convert((long) imageView.getTag());
                                try {
                                    int drawImgSize = getStaticIntField(EmojiClass, "drawImgSize");
                                    int bigImgSize = getStaticIntField(EmojiClass, "bigImgSize");
                                    imageView.setImageDrawable(TextDrawable.builder()
                                            .beginConfig()
                                            .textColor(Color.BLACK)
                                            .fontSize(preferences.getBoolean("big_emoji_page", true) ? bigImgSize : drawImgSize)
                                            .endConfig()
                                            .buildRect(convert, Color.TRANSPARENT));
                                } catch (Exception ignored) {
                                    imageView.setImageDrawable(TextDrawable.builder()
                                            .beginConfig()
                                            .textColor(Color.BLACK)
                                            .endConfig()
                                            .buildRect(convert, Color.TRANSPARENT));

                                }
                            }
                            return o;
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }

        //设置页面插入选项
        try {
            Class<?> SettingsActivity = findClass("org.telegram.ui.SettingsActivity", loadPackageParam.classLoader);
            final Field sendByEnterRowField = findField(SettingsActivity, "sendByEnterRow");
            final Field rowCountField = findField(SettingsActivity, "rowCount");
            Class<?> ListAdapter = findClass("org.telegram.ui.SettingsActivity.ListAdapter", loadPackageParam.classLoader);

            findAndHookMethod(ListAdapter, "getCount", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    Object SettingsActivityObject = XposedHelpers.getSurroundingThis(methodHookParam.thisObject);
                    int rowCount = rowCountField.getInt(SettingsActivityObject);
                    return rowCount + 3;
                }
            });

            findAndHookMethod(ListAdapter, "isEnabled", int.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    Object SettingsActivityObject = XposedHelpers.getSurroundingThis(methodHookParam.thisObject);
                    int sendByEnterRow = sendByEnterRowField.getInt(SettingsActivityObject);
                    int i = (int) methodHookParam.args[0];
                    if (i == sendByEnterRow + 1 || i == sendByEnterRow + 2) {
                        return true;
                    } else if (i == sendByEnterRow + 3) {
                        XSharedPreferences preferences = new XSharedPreferences(loadPackageParam.packageName, "mainconfig");
                        return preferences.getBoolean("use_android_emoji", true);
                    } else if (i >= sendByEnterRow + 3) {
                        methodHookParam.args[0] = i - 3;
                    }
                    return XposedBridge.invokeOriginalMethod(methodHookParam.method, methodHookParam.thisObject, methodHookParam.args);
                }
            });

            findAndHookMethod(ListAdapter, "getItemViewType", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (mRow.get() != null) {
                        return;
                    }
                    Object SettingsActivityObject = XposedHelpers.getSurroundingThis(param.thisObject);
                    int sendByEnterRow = sendByEnterRowField.getInt(SettingsActivityObject);
                    int i = (int) param.args[0];
                    if (i == sendByEnterRow + 1 || i == sendByEnterRow + 2 || i == sendByEnterRow + 3) {
                        param.args[0] = sendByEnterRow;
                    } else if (i > sendByEnterRow + 3) {
                        param.args[0] = i - 3;
                    }
                }
            });

            findAndHookMethod(ListAdapter, "getView", int.class, View.class, ViewGroup.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object SettingsActivityObject = XposedHelpers.getSurroundingThis(param.thisObject);
                            int sendByEnterRow = sendByEnterRowField.getInt(SettingsActivityObject);
                            int i = (int) param.args[0];
                            if (i == sendByEnterRow) {
                                mRow.set("sendByEnterRow");
                            } else if (i == sendByEnterRow + 1) {
                                param.args[0] = sendByEnterRow;
                                mRow.set("doNotSendTyping");
                            } else if (i == sendByEnterRow + 2) {
                                param.args[0] = sendByEnterRow;
                                mRow.set("useAndroidEmojiRow");
                            } else if (i == sendByEnterRow + 3) {
                                param.args[0] = sendByEnterRow;
                                mRow.set("bigEmojiPageRow");
                            } else {
                                mRow.set("otherRow");
                                if (i > sendByEnterRow + 3) {
                                    param.args[0] = i - 3;
                                }
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            mRow.set(null);
                        }
                    });

            final Class<?> TextCheckCell = findClass("org.telegram.ui.Cells.TextCheckCell", loadPackageParam.classLoader);
            findAndHookMethod(TextCheckCell, "setTextAndCheck", String.class, boolean.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if ("sendByEnterRow".equals(mRow.get())) {
                                param.args[2] = true;
                            } else if ("doNotSendTyping".equals(mRow.get())) {
                                View view = (View) param.thisObject;
                                SharedPreferences preferences = view.getContext().getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                param.args[0] = "Do Not Send Typing";
                                param.args[1] = preferences.getBoolean("do_not_send_typing", false);
                                param.args[2] = true;
                            } else if ("useAndroidEmojiRow".equals(mRow.get())) {
                                View view = (View) param.thisObject;
                                SharedPreferences preferences = view.getContext().getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                param.args[0] = "Use Android Emoji";
                                param.args[1] = preferences.getBoolean("use_android_emoji", true);
                                param.args[2] = true;
                            } else if ("bigEmojiPageRow".equals(mRow.get())) {
                                View view = (View) param.thisObject;
                                SharedPreferences preferences = view.getContext().getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                param.args[0] = "Use Big Emoji Page";
                                param.args[1] = preferences.getBoolean("big_emoji_page", true);
                            }
                        }
                    });

            final Field listViewField = findField(SettingsActivity, "listView");

            final Method setChecked = findMethodExact(TextCheckCell, "setChecked", boolean.class);

            XC_MethodHook settingMethodHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    ListView listView = (ListView) listViewField.get(param.thisObject);
                    final AdapterView.OnItemClickListener onItemClickListener = listView.getOnItemClickListener();
                    final int sendByEnterRow = sendByEnterRowField.getInt(param.thisObject);
                    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            if (position == sendByEnterRow + 1) {
                                SharedPreferences preferences = parent.getContext().getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                boolean send = preferences.getBoolean("do_not_send_typing", false);
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putBoolean("do_not_send_typing", !send);
                                editor.commit();
                                if (TextCheckCell.equals(view.getClass())) {
                                    try {
                                        setChecked.invoke(view, !send);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                return;
                            } else if (position == sendByEnterRow + 2) {
                                SharedPreferences preferences = parent.getContext().getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                boolean use = preferences.getBoolean("use_android_emoji", true);
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putBoolean("use_android_emoji", !use);
                                editor.commit();
                                if (TextCheckCell.equals(view.getClass())) {
                                    try {
                                        setChecked.invoke(view, !use);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                return;
                            } else if (position == sendByEnterRow + 3) {
                                SharedPreferences preferences = parent.getContext().getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                boolean big = preferences.getBoolean("big_emoji_page", true);
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putBoolean("big_emoji_page", !big);
                                editor.commit();
                                if (TextCheckCell.equals(view.getClass())) {
                                    try {
                                        setChecked.invoke(view, !big);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                return;
                            } else if (position > sendByEnterRow + 3) {
                                position -= 3;
                            }
                            assert onItemClickListener != null;
                            onItemClickListener.onItemClick(parent, view, position, id);
                        }
                    });
                }
            };
            if (versionCode < 578) {
                findAndHookMethod(SettingsActivity, "createView", Context.class, LayoutInflater.class,
                        settingMethodHook);
            } else {
                findAndHookMethod(SettingsActivity, "createView", Context.class, settingMethodHook);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //输入状态屏蔽
        try {
            findAndHookMethod("org.telegram.android.MessagesController", loadPackageParam.classLoader,
                    "sendTyping", long.class, int.class, int.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                            XSharedPreferences preferences = new XSharedPreferences(loadPackageParam.packageName, "mainconfig");
                            if (preferences.getBoolean("do_not_send_typing", false)) {
                                return null;
                            } else {
                                return XposedBridge.invokeOriginalMethod(methodHookParam.method,
                                        methodHookParam.thisObject, methodHookParam.args);
                            }
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String convert(long paramLong) {
        String str = "";
        for (int i = 0; ; i++) {
            if (i >= 4) {
                return str;
            }
            int j = (int) (0xFFFF & paramLong >> 16 * (3 - i));
            if (j != 0) {
                str = str + (char) j;
            }
        }
    }
}
