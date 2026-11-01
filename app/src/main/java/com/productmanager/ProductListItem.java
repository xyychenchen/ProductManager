package com.productmanager;

import androidx.room.ColumnInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 列表页专用的轻量投影
 *
 * 列表只需要展示若干短字段，不需要 script（话术）这类大文本。
 * 用投影查询可以显著减少首屏的数据量与内存占用。
 */
public class ProductListItem {

    @ColumnInfo(name = "id")
    public int id;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "specification")
    public String specification;

    @ColumnInfo(name = "size")
    public String size;

    @ColumnInfo(name = "material")
    public String material;

    @ColumnInfo(name = "remark")
    public String remark;

    @ColumnInfo(name = "price")
    public double price;

    @ColumnInfo(name = "photoPath")
    public String photoPath;

    @ColumnInfo(name = "isFavorite")
    public boolean isFavorite;

    /**
     * 索引字母：A-Z 归一到大写字母，其余（中文、数字、符号、emoji）统一归到 "#"
     */
    public String getFirstLetter() {
        if (name == null || name.isEmpty()) {
            return "#";
        }
        int codePoint = name.codePointAt(0);
        if (codePoint >= 'a' && codePoint <= 'z') {
            return String.valueOf((char) (codePoint - ('a' - 'A')));
        }
        if (codePoint >= 'A' && codePoint <= 'Z') {
            return String.valueOf((char) codePoint);
        }
        return "#";
    }

    public String getFormattedPrice() {
        return String.format(Locale.US, "$%.2f", price);
    }

    /**
     * 展示顺序：A-Z 在前，"#" 分组统一放到最后。
     *
     * 排序只在这里做一次，避免 SQL 的 COLLATE NOCASE（仅对 ASCII 生效）与
     * Java 的 toLowerCase（Unicode）两套口径互相打架。
     */
    private static int letterRank(String letter) {
        if (letter == null || letter.isEmpty() || "#".equals(letter)) {
            return Integer.MAX_VALUE;
        }
        return letter.charAt(0);
    }

    public static final Comparator<ProductListItem> DISPLAY_ORDER = (a, b) -> {
        int byLetter = Integer.compare(letterRank(a.getFirstLetter()), letterRank(b.getFirstLetter()));
        if (byLetter != 0) {
            return byLetter;
        }
        String nameA = a.name == null ? "" : a.name;
        String nameB = b.name == null ? "" : b.name;
        return String.CASE_INSENSITIVE_ORDER.compare(nameA, nameB);
    };

    /**
     * 返回排好序的副本，不修改入参（入参可能来自数据库缓存或 ViewModel）
     */
    public static List<ProductListItem> sortedCopy(List<ProductListItem> source) {
        List<ProductListItem> copy = new ArrayList<>(source);
        copy.sort(DISPLAY_ORDER);
        return copy;
    }
}
