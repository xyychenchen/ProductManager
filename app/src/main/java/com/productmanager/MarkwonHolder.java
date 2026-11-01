package com.productmanager;

import android.content.Context;

import io.noties.markwon.Markwon;
import io.noties.markwon.core.CorePlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.glide.GlideImagesPlugin;

/**
 * Markwon 单例
 *
 * 构建 Markwon 需要装配 5 个插件，属于一次性成本。放在 Application 级别复用，
 * 避免每次打开话术页都重新构建。
 */
public final class MarkwonHolder {

    private static volatile Markwon instance;

    private MarkwonHolder() {
    }

    public static Markwon get(Context context) {
        Markwon local = instance;
        if (local == null) {
            synchronized (MarkwonHolder.class) {
                local = instance;
                if (local == null) {
                    Context appContext = context.getApplicationContext();
                    local = Markwon.builder(appContext)
                            .usePlugin(CorePlugin.create())
                            .usePlugin(StrikethroughPlugin.create())
                            .usePlugin(TablePlugin.create(appContext))
                            .usePlugin(TaskListPlugin.create(appContext))
                            .usePlugin(HtmlPlugin.create())
                            .usePlugin(GlideImagesPlugin.create(appContext))
                            .build();
                    instance = local;
                }
            }
        }
        return local;
    }
}
