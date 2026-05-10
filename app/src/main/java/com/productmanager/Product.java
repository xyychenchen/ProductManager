package com.productmanager;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 产品数据模型
 * 用于存储产品信息：名称、规格、尺寸、材质、价格、照片、话术
 */
@Entity(tableName = "products")
public class Product {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String name;           // 产品名称（用于记忆的名字）
    private String specification;  // 规格
    private String size;           // 尺寸
    private String material;       // 材质
    private double price;          // 价格（美金）
    private String photoPath;      // 产品照片路径
    private String script;         // 产品话术（不显示在主页列表）
    private long createTime;       // 创建时间
    private long updateTime;       // 更新时间

    // 构造函数
    public Product() {
        this.createTime = System.currentTimeMillis();
        this.updateTime = System.currentTimeMillis();
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.updateTime = System.currentTimeMillis();
    }

    public String getSpecification() {
        return specification;
    }

    public void setSpecification(String specification) {
        this.specification = specification;
        this.updateTime = System.currentTimeMillis();
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
        this.updateTime = System.currentTimeMillis();
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
        this.updateTime = System.currentTimeMillis();
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
        this.updateTime = System.currentTimeMillis();
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
        this.updateTime = System.currentTimeMillis();
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
        this.updateTime = System.currentTimeMillis();
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    /**
     * 获取首字母（用于字母索引）
     */
    public String getFirstLetter() {
        if (name == null || name.isEmpty()) {
            return "#";
        }
        char firstChar = name.charAt(0);
        if (Character.isLetter(firstChar)) {
            return String.valueOf(Character.toUpperCase(firstChar));
        }
        return "#";
    }

    /**
     * 格式化价格显示
     */
    public String getFormattedPrice() {
        return String.format("$%.2f", price);
    }
}
