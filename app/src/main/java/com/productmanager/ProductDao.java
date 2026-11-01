package com.productmanager;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 产品数据访问对象
 * 提供数据库CRUD操作
 */
@Dao
public interface ProductDao {

    // 列表页需要的字段（不含 script 大文本）
    String LIST_COLUMNS = "id, name, specification, size, material, remark, price, photoPath, isFavorite";

    @Insert
    long insert(Product product);

    @Update
    void update(Product product);

    @Delete
    void delete(Product product);

    /**
     * 完整实体查询：仅在需要全部字段（导出、编辑回填）时使用
     */
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    List<Product> getAllProducts();

    @Query("SELECT * FROM products WHERE id = :id")
    Product getProductById(int id);

    /**
     * 列表查询：只取展示所需字段，避免把话术等大文本读进内存
     */
    @Query("SELECT " + LIST_COLUMNS + " FROM products ORDER BY name COLLATE NOCASE ASC")
    List<ProductListItem> getAllItems();

    /**
     * 搜索：名称/规格/尺寸/材质/备注/话术
     *
     * 调用方需先用 {@link #toLikePattern(String)} 生成 pattern。
     */
    @Query("SELECT " + LIST_COLUMNS + " FROM products WHERE "
            + "name LIKE :pattern ESCAPE '\\' OR "
            + "specification LIKE :pattern ESCAPE '\\' OR "
            + "size LIKE :pattern ESCAPE '\\' OR "
            + "material LIKE :pattern ESCAPE '\\' OR "
            + "remark LIKE :pattern ESCAPE '\\' OR "
            + "script LIKE :pattern ESCAPE '\\'")
    List<ProductListItem> searchItems(String pattern);

    @Query("UPDATE products SET isFavorite = :isFavorite WHERE id = :id")
    void updateFavorite(int id, boolean isFavorite);

    /**
     * 把用户输入转成 LIKE 模式，转义通配符。
     *
     * 不做转义时，用户输入一个 "%" 就会匹配全部数据。
     */
    static String toLikePattern(String keyword) {
        String escaped = keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
