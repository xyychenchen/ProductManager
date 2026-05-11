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
 *
 * 数据库版本历史：
 * - 版本1: 基础字段 (name, specification, size, price, photoPath, createTime, updateTime)
 * - 版本2: 添加 material 和 script 字段
 * - 版本3: 添加 remark（备注）字段
 */
@Database(entities = {Product.class}, version = 3, exportSchema = false)
public abstract class ProductDatabase extends RoomDatabase {

    private static volatile ProductDatabase INSTANCE;

    public abstract ProductDao productDao();

    /**
     * 数据库迁移：从版本1到版本2
     * 添加 material（材质）和 script（话术）字段
     *
     * 这是正确的迁移方式，会保留所有现有数据
     */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // ALTER TABLE ADD COLUMN 会自动给现有行的这个列设置为 NULL
            database.execSQL("ALTER TABLE products ADD COLUMN material TEXT");
            database.execSQL("ALTER TABLE products ADD COLUMN script TEXT");
        }
    };

    /**
     * 数据库迁移：从版本2到版本3
     * 添加 remark（备注）字段
     */
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE products ADD COLUMN remark TEXT");
        }
    };

    /**
     * 获取数据库单例
     *
     * 重要：只使用 addMigrations，不使用 fallbackToDestructiveMigration
     * 这样可以确保数据永远不会因为迁移问题而丢失
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // 不使用 fallbackToDestructiveMigration
                    // 这样如果迁移失败会抛出异常，而不是删除数据
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
