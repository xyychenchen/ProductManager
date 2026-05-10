package com.productmanager;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * 产品数据库
 * 使用Room持久化库管理SQLite数据库
 */
@Database(entities = {Product.class}, version = 1, exportSchema = false)
public abstract class ProductDatabase extends RoomDatabase {
    
    private static volatile ProductDatabase INSTANCE;
    
    public abstract ProductDao productDao();
    
    /**
     * 获取数据库单例
     */
    public static ProductDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (ProductDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            ProductDatabase.class,
                            "product_database"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}
