package com.productmanager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CSV 导入解析器
 *
 * 只解析，不碰数据库。调用方拿到 {@link Result} 后自行决定怎么写库。
 *
 * 表头识别（findHeaderRow / mapColumns / matchField）是包内共用的，
 * XlsxImporter 直接复用同一套别名规则，保证两种格式认列的方式完全一致。
 *
 * 处理了这几个实际会遇到的问题：
 * 1. Excel 另存的 CSV 大多是 GBK 编码，直接按 UTF-8 读会全是乱码
 * 2. 字段里可能含逗号、换行、双引号（话术经常是这样）
 * 3. 表头写法五花八门，"产品名称""名称""name" 都得认
 * 4. 价格可能带 $ 符号和千分位逗号
 */
public final class CsvImporter {

    /** 单次导入的体积上限，超过就拒绝，避免把内存撑爆 */
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    /** 表头最多往下找几行：有些表格前面带标题行 */
    private static final int HEADER_SEARCH_ROWS = 5;

    private CsvImporter() {
    }

    /** 解析结果 */
    public static final class Result {
        public final List<Product> products = new ArrayList<>();
        /** 数据行总数（不含表头） */
        public int totalRows;
        /** 跳过的行及原因，格式：第 3 行：缺少产品名称 */
        public final List<String> skipped = new ArrayList<>();

        public int importedCount() {
            return products.size();
        }

        public int skippedCount() {
            return skipped.size();
        }
    }

    public static Result parse(InputStream in) throws IOException {
        byte[] data = readAll(in);
        if (data.length == 0) {
            throw new IOException("文件是空的");
        }
        if (data.length > MAX_BYTES) {
            throw new IOException("文件超过 8MB，请拆分后再导入");
        }
        String text = decode(data);
        return parseText(text);
    }

    // ================= 编码 =================

    /**
     * 优先 UTF-8（带不带 BOM 都行），不合法再回退 GBK。
     *
     * 用严格解码器判断，而不是 new String(bytes, UTF_8)——
     * 后者遇到非法字节会静默替换成"�"，永远不抛异常，没法用来探测。
     */
    private static String decode(byte[] data) {
        int offset = 0;
        if (data.length >= 3
                && (data[0] & 0xFF) == 0xEF
                && (data[1] & 0xFF) == 0xBB
                && (data[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data, offset, data.length - offset))
                    .toString();
        } catch (CharacterCodingException ignored) {
            // 不是合法 UTF-8，继续往下试
        }
        for (String name : new String[]{"GBK", "GB18030"}) {
            try {
                return Charset.forName(name).newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(data, offset, data.length - offset))
                        .toString();
            } catch (Exception ignored) {
                // 换下一个编码
            }
        }
        return new String(data, offset, data.length - offset, StandardCharsets.UTF_8);
    }

    // ================= 解析 =================

    private static Result parseText(String text) {
        List<List<String>> rows = splitRows(text);
        Result result = new Result();
        if (rows.isEmpty()) {
            return result;
        }

        // 在前几行里找一个像表头的行
        int headerIndex = findHeaderRow(rows);
        if (headerIndex < 0) {
            result.skipped.add("没有找到表头。第一行请写：产品名称,规格,尺寸,材质,备注,价格,话术");
            return result;
        }

        Map<String, Integer> columns = mapColumns(rows.get(headerIndex));
        Integer nameCol = columns.get("name");
        if (nameCol == null) {
            result.skipped.add("表头里没有「产品名称」这一列");
            return result;
        }

        for (int i = headerIndex + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            result.totalRows++;
            int lineNo = i + 1;

            String name = cell(row, nameCol);
            if (name.isEmpty()) {
                result.skipped.add("第 " + lineNo + " 行：产品名称为空");
                continue;
            }

            Product product = new Product();
            product.setName(name);
            product.setSpecification(cell(row, columns.get("specification")));
            product.setSize(cell(row, columns.get("size")));
            product.setMaterial(cell(row, columns.get("material")));
            product.setRemark(cell(row, columns.get("remark")));
            product.setScript(cell(row, columns.get("script")));

            String rawPrice = cell(row, columns.get("price"));
            if (!rawPrice.isEmpty()) {
                Double price = parsePrice(rawPrice);
                if (price == null) {
                    result.skipped.add("第 " + lineNo + " 行：价格「" + rawPrice + "」无法识别，已按 0 处理");
                } else {
                    product.setPrice(price);
                }
            }

            // 图片无法从表格还原，留空；createTime/updateTime 由构造函数填当前时间
            product.setPhotoPath(null);
            product.setFavorite(false);
            result.products.add(product);
        }

        return result;
    }

    /**
     * CSV 状态机：处理引号包裹、双重引号转义、字段内换行
     */
    private static List<List<String>> splitRows(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (c != '\r') {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 在前几行里找表头：要求同时出现「名称」类列，且至少再认出一列
     */
    static int findHeaderRow(List<List<String>> rows) {
        int limit = Math.min(HEADER_SEARCH_ROWS, rows.size());
        for (int i = 0; i < limit; i++) {
            Map<String, Integer> map = mapColumns(rows.get(i));
            if (map.containsKey("name") && map.size() >= 2) {
                return i;
            }
        }
        // 兜底：哪怕只认出名称列也接受
        for (int i = 0; i < limit; i++) {
            if (mapColumns(rows.get(i)).containsKey("name")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 表头 → 字段下标。别名覆盖常见写法，含英文。
     */
    static Map<String, Integer> mapColumns(List<String> header) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String key = normalizeHeader(header.get(i));
            if (key.isEmpty()) {
                continue;
            }
            String field = matchField(key);
            if (field != null && !map.containsKey(field)) {
                map.put(field, i);
            }
        }
        return map;
    }

    static String matchField(String key) {
        // 去掉括号里的单位说明，比如「价格(美元)」→「价格」
        String k = key.replaceAll("[（(].*?[)）]", "").trim();

        if (k.equals("产品名称") || k.equals("产品名") || k.equals("名称") || k.equals("名字")
                || k.equals("商品名称") || k.equals("品名")
                || k.equals("name") || k.equals("product") || k.equals("productname")
                || k.equals("title")) {
            return "name";
        }
        if (k.equals("规格") || k.equals("型号") || k.equals("spec") || k.equals("specification")) {
            return "specification";
        }
        if (k.equals("尺寸") || k.equals("大小") || k.equals("size")) {
            return "size";
        }
        if (k.equals("材质") || k.equals("材料") || k.equals("material")) {
            return "material";
        }
        if (k.equals("备注") || k.equals("说明") || k.equals("remark") || k.equals("note")
                || k.equals("memo")) {
            return "remark";
        }
        if (k.equals("价格") || k.equals("单价") || k.equals("售价") || k.equals("价格美元")
                || k.equals("price") || k.equals("usd")) {
            return "price";
        }
        if (k.equals("话术") || k.equals("话术内容") || k.equals("口播") || k.equals("脚本")
                || k.equals("script") || k.equals("salespitch")) {
            return "script";
        }
        return null;
    }

    static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .replace("\uFEFF", "")
                .replace(" ", "")
                .replace("　", "")
                .replace("_", "")
                .toLowerCase(Locale.ROOT);
    }

    static boolean isBlankRow(List<String> row) {
        for (String cell : row) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    static String cell(List<String> row, Integer index) {
        if (index == null || index < 0 || index >= row.size()) {
            return "";
        }
        String value = row.get(index);
        return value == null ? "" : value.trim();
    }

    /**
     * 价格容错：$1,234.56 / 1234.56 元 / 1 234.56 都能读
     * 完全读不出数字时返回 null，由调用方决定按 0 处理
     */
    static Double parsePrice(String raw) {
        String cleaned = raw.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".")) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if (out.size() > MAX_BYTES) {
                throw new IOException("文件超过 8MB，请拆分后再导入");
            }
        }
        return out.toByteArray();
    }

    /**
     * 生成导入模板。带 BOM，Excel 双击打开不会乱码。
     */
    public static byte[] buildTemplate() {
        StringBuilder sb = new StringBuilder();
        sb.append("产品名称,规格,尺寸,材质,备注,价格,话术\n");
        sb.append("示例保温杯,500ml,直径7cm高20cm,304不锈钢,现货,12.99,开场白：这款杯子保温12小时\n");
        sb.append("示例帆布袋,38x42cm,,棉麻,,5.50,\n");
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[body.length + 3];
        out[0] = (byte) 0xEF;
        out[1] = (byte) 0xBB;
        out[2] = (byte) 0xBF;
        System.arraycopy(body, 0, out, 3, body.length);
        return out;
    }
}
