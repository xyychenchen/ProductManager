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
    
    @Insert
    long insert(Product product);
    
    @Update
    void update(Product product);
    
    @Delete
    void delete(Product product);
    
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    List<Product> getAllProducts();
    
    @Query("SELECT * FROM products WHERE id = :id")
    Product getProductById(int id);
    
    @Query("SELECT * FROM products WHERE " +
           "name LIKE '%' || :keyword || '%' OR " +
           "specification LIKE '%' || :keyword || '%' OR " +
           "size LIKE '%' || :keyword || '%' OR " +
           "material LIKE '%' || :keyword || '%' OR " +
           "remark LIKE '%' || :keyword || '%' " +
           "ORDER BY name COLLATE NOCASE ASC")
    List<Product> searchProducts(String keyword);
    
    @Query("SELECT * FROM products WHERE name LIKE :letter || '%' ORDER BY name COLLATE NOCASE ASC")
    List<Product> getProductsByFirstLetter(String letter);
    
    @Query("SELECT DISTINCT UPPER(SUBSTR(name, 1, 1)) as letter FROM products WHERE name IS NOT NULL AND name != '' ORDER BY letter")
    List<String> getUsedLetters();
    
    @Query("DELETE FROM products WHERE id = :id")
    void deleteById(int id);
}
