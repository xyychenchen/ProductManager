package com.productmanager;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Excel（.xlsx）导入入口，支持把单元格里的产品图片一起导进来。
 *
 * 为什么 CSV 做不到：CSV 是纯文本格式，规范里只有字符，没有任何位置能塞二进制图片。
 * 想带图只能用 xlsx —— 它本身就是个 zip 包，图片躺在 xl/media/ 下，
 * 再靠 xl/drawings/*.xml 里的锚点确定每张图属于哪一行。
 *
 * 没有引入 Apache POI：它会带来 10MB+ 的体积和一串反射调用，
 * 而我们只需要读表格和图片，用 JDK 自带的 zip + SAX 就够了。
 *
 * 表格解析在 {@link XlsxTableReader} 里（纯 Java，可单测），
 * 这里只负责把解析出的图片解码、压缩、落到应用私有目录。
 */
public final class XlsxImporter {

    private static final String TAG = "XlsxImporter";

    /** xlsx 带图片，体积比 CSV 大得多，上限放到 40MB */
    private static final long MAX_FILE_BYTES = 40L * 1024 * 1024;
    /** 单张图片解压上限 */
    private static final long MAX_IMAGE_BYTES = 30L * 1024 * 1024;
    /** 单次最多处理多少张图，防止极端表格拖垮导入 */
    private static final int MAX_IMAGES = 500;

    private XlsxImporter() {
    }

    /** 解析结果 */
    public static final class Result {
        public final List<Product> products = new ArrayList<>();
        /** 数据行总数 */
        public int totalRows;
        /** 跳过的行及原因 */
        public final List<String> skipped = new ArrayList<>();
        /** 非致命提示：图片读不出、格式不支持之类 */
        public final List<String> warnings = new ArrayList<>();
        /** 成功落地的图片数量 */
        public int imageCount;

        public int importedCount() {
            return products.size();
        }

        public int skippedCount() {
            return skipped.size();
        }
    }

    /**
     * 解析 xlsx 并把其中的图片压缩后落到应用私有目录。
     *
     * 必须在后台线程调用（磁盘 IO + 图片解码）。
     */
    public static Result parse(Context context, InputStream in) throws IOException {
        File temp = File.createTempFile("xlsx_import_", ".xlsx", context.getCacheDir());
        try {
            copyToFile(in, temp);
            try (ZipFile zip = new ZipFile(temp)) {
                XlsxTableReader.Table table = XlsxTableReader.readTable(zip);
                return build(context, zip, table);
            }
        } finally {
            ImageStore.deleteQuietly(temp.getAbsolutePath());
        }
    }

    /**
     * 判断选中的文件是不是 xlsx。
     *
     * 看的是 zip 的魔数 PK\x03\x04，而不是扩展名 —— 部分文件管理器会把 .xlsx
     * 改名，或者 ContentResolver 拿到的显示名压根没有后缀。
     * 调用方需要传支持 mark 的流（例如 BufferedInputStream）。
     */
    public static boolean looksLikeXlsx(InputStream in) {
        try {
            if (!in.markSupported()) {
                return false;
            }
            in.mark(8);
            byte[] head = new byte[4];
            int read = in.read(head);
            in.reset();
            return read == 4
                    && head[0] == 'P' && head[1] == 'K'
                    && head[2] == 0x03 && head[3] == 0x04;
        } catch (IOException e) {
            return false;
        }
    }

    // ================= 组装 =================

    private static Result build(Context context, ZipFile zip, XlsxTableReader.Table table) {
        Result result = new Result();

        if (table.headerRow < 0) {
            result.skipped.add("没有找到表头。第一行请写：产品名称,规格,尺寸,材质,备注,价格,话术"
                    + "（要带图片就再加一列「图片」）");
            return result;
        }
        if (table.hasInCellImages) {
            result.warnings.add("检测到「置于单元格内」的图片，这种存法读不出来。"
                    + "请在 Excel 里改用「插入 → 图片 → 放在单元格上」再试。");
        }

        List<Integer> rowIndexes = table.rowNumbers();
        List<List<String>> grid = table.asGrid();
        int headerPos = rowIndexes.indexOf(table.headerRow);
        if (headerPos < 0) {
            return result;
        }

        Map<String, Integer> columns = CsvImporter.mapColumns(grid.get(headerPos));
        Integer nameCol = columns.get("name");
        if (nameCol == null) {
            result.skipped.add("表头里没有「产品名称」这一列");
            return result;
        }

        Map<Integer, String> photoByRow = bindImages(table);
        File tempDir = new File(context.getCacheDir(), "xlsx_media");

        for (int i = headerPos + 1; i < grid.size(); i++) {
            List<String> row = grid.get(i);
            int excelRow = rowIndexes.get(i);
            if (CsvImporter.isBlankRow(row)) {
                continue;
            }
            result.totalRows++;

            String name = CsvImporter.cell(row, nameCol);
            if (name.isEmpty()) {
                result.skipped.add("第 " + excelRow + " 行：产品名称为空");
                continue;
            }

            Product product = new Product();
            product.setName(name);
            product.setSpecification(CsvImporter.cell(row, columns.get("specification")));
            product.setSize(CsvImporter.cell(row, columns.get("size")));
            product.setMaterial(CsvImporter.cell(row, columns.get("material")));
            product.setRemark(CsvImporter.cell(row, columns.get("remark")));
            product.setScript(CsvImporter.cell(row, columns.get("script")));

            String rawPrice = CsvImporter.cell(row, columns.get("price"));
            if (!rawPrice.isEmpty()) {
                Double price = CsvImporter.parsePrice(rawPrice);
                if (price == null) {
                    result.skipped.add("第 " + excelRow + " 行：价格「" + rawPrice
                            + "」无法识别，已按 0 处理");
                } else {
                    product.setPrice(price);
                }
            }

            String mediaPath = photoByRow.get(excelRow);
            if (mediaPath != null && result.imageCount < MAX_IMAGES) {
                String stored = extractAndStore(context, zip, mediaPath, tempDir);
                if (stored != null) {
                    product.setPhotoPath(stored);
                    result.imageCount++;
                } else {
                    result.warnings.add("第 " + excelRow + " 行：图片格式无法识别，已跳过");
                }
            }

            product.setFavorite(false);
            result.products.add(product);
        }

        ImageStore.deleteQuietly(tempDir.getAbsolutePath());
        return result;
    }

    /** 把锚点按行归拢：每行只取一张，「图片」列优先 */
    private static Map<Integer, String> bindImages(XlsxTableReader.Table table) {
        Map<Integer, String> preferred = new HashMap<>();
        Map<Integer, String> fallback = new HashMap<>();
        for (XlsxTableReader.Anchor anchor : table.anchors) {
            String target = table.relTargets.get(anchor.relId);
            if (target == null) {
                continue;
            }
            int excelRow = anchor.row + 1;
            if (table.photoColumn >= 0 && anchor.col == table.photoColumn) {
                if (!preferred.containsKey(excelRow)) {
                    preferred.put(excelRow, target);
                }
            } else if (!fallback.containsKey(excelRow)) {
                fallback.put(excelRow, target);
            }
        }
        for (Map.Entry<Integer, String> entry : fallback.entrySet()) {
            if (!preferred.containsKey(entry.getKey())) {
                preferred.put(entry.getKey(), entry.getValue());
            }
        }
        return preferred;
    }

    /**
     * 把 zip 里的一张图解到临时文件，交给 ImageStore 统一压缩后入库。
     * 临时文件用完即删，避免在 cache 里堆积。
     */
    private static String extractAndStore(Context context, ZipFile zip,
                                          String entryPath, File tempDir) {
        ZipEntry entry = zip.getEntry(entryPath);
        if (entry == null || entry.isDirectory()) {
            return null;
        }
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            Log.w(TAG, "无法创建临时目录: " + tempDir);
            return null;
        }
        int dot = entryPath.lastIndexOf('.');
        String suffix = dot >= 0 ? entryPath.substring(dot) : "";
        File temp = new File(tempDir, "img_" + Math.abs(entryPath.hashCode()) + suffix);
        try (InputStream in = zip.getInputStream(entry)) {
            copyToFileLimited(in, temp, MAX_IMAGE_BYTES);
            return ImageStore.importFromFile(context, temp);
        } catch (IOException e) {
            Log.w(TAG, "解出图片失败: " + entryPath, e);
            return null;
        } finally {
            ImageStore.deleteQuietly(temp.getAbsolutePath());
        }
    }

    // ================= IO =================

    private static void copyToFile(InputStream in, File target) throws IOException {
        try (FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[16384];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_FILE_BYTES) {
                    throw new IOException("文件超过 40MB，请拆分后再导入");
                }
                out.write(buffer, 0, read);
            }
        }
    }

    private static void copyToFileLimited(InputStream in, File target, long limit) throws IOException {
        try (FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[16384];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new IOException("单张图片超过体积上限");
                }
                out.write(buffer, 0, read);
            }
        }
    }
}
