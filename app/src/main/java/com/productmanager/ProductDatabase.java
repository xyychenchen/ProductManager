package com.productmanager;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * 产品数据库
 * 使用Room持久化库管理SQLite数据库
 */
@Database(entities = {Product.class}, version = 2, exportSchema = false)
public abstract class ProductDatabase extends RoomDatabase {

    private static volatile ProductDatabase INSTANCE;

    public abstract ProductDao productDao();

    /**
     * 数据库迁移：从版本1到版本2
     * 添加 material 和 script 字段
     */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 添加 material 列（材质），默认为空
            database.execSQL("ALTER TABLE products ADD COLUMN material TEXT");
            // 添加 script 列（话术），默认为空
            database.execSQL("ALTER TABLE products ADD COLUMN script TEXT");
        }
    };

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
                    )
                    .addMigrations(MIGRATION_1_2)
                    // 如果迁移失败，允许重建数据库（会丢失数据，但不会崩溃）
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
