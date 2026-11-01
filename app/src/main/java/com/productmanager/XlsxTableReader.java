package com.productmanager;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * xlsx 的纯解析层：只负责从 zip 里把表格和图片位置读出来。
 *
 * 刻意不引用任何 Android API —— 这样这段最容易出错的逻辑
 * 可以在普通 JVM 上跑单元测试，而不必依赖真机或模拟器。
 * 真正把图片落盘的动作交给 {@link XlsxImporter}。
 *
 * 解析路径：
 *   xl/sharedStrings.xml                  → 共享字符串池
 *   xl/worksheets/sheetN.xml              → 单元格数据
 *   xl/worksheets/_rels/sheetN.xml.rels   → 找到 drawing 部件
 *   xl/drawings/drawingN.xml              → 图片锚点（row/col，0 起始）
 *   xl/drawings/_rels/drawingN.xml.rels   → rId → media 文件
 */
final class XlsxTableReader {

    private static final String TAG_ROW = "row";
    private static final String TAG_COL = "col";
    private static final String TAG_FROM = "from";
    private static final String TAG_TO = "to";
    private static final String TAG_BLIP = "blip";

    private XlsxTableReader() {
    }

    /** 图片锚点：某张图贴在哪个单元格 */
    static final class Anchor {
        final int row;      // 0 起始
        final int col;      // 0 起始
        final String relId;

        Anchor(int row, int col, String relId) {
            this.row = row;
            this.col = col;
            this.relId = relId;
        }
    }

    /** 从 zip 里读出来的原始表格 */
    static final class Table {
        /** Excel 行号（1 起始）→ 列下标（0 起始）→ 值 */
        final TreeMap<Integer, Map<Integer, String>> rows = new TreeMap<>();
        final List<Anchor> anchors = new ArrayList<>();
        /** drawing 内部的 rId → media 在 zip 里的路径 */
        final Map<String, String> relTargets = new HashMap<>();
        /** 是否为 Excel 365 的「置于单元格内」图片（存法完全不同，读不到） */
        boolean hasInCellImages;
        /** 表头识别出来的图片列下标，没有就是 -1 */
        int photoColumn = -1;
        /** 表头所在 Excel 行号，没找到就是 -1 */
        int headerRow = -1;

        /** 按行号顺序取出每一行，转成稠密的字符串列表（缺失的格子补空串） */
        List<List<String>> asGrid() {
            List<List<String>> grid = new ArrayList<>(rows.size());
            for (Map<Integer, String> row : rows.values()) {
                grid.add(toRowList(row));
            }
            return grid;
        }

        /** 行号顺序，与 {@link #asGrid()} 一一对应 */
        List<Integer> rowNumbers() {
            return new ArrayList<>(rows.keySet());
        }
    }

    // ================= 主流程 =================

    static Table readTable(ZipFile zip) throws IOException {
        Table table = new Table();

        List<String> sharedStrings = readSharedStrings(zip);
        String sheetPath = findFirstSheet(zip);

        readSheet(zip, sheetPath, sharedStrings, table);

        List<Integer> rowIndexes = table.rowNumbers();
        List<List<String>> grid = table.asGrid();
        int headerPos = CsvImporter.findHeaderRow(grid);
        if (headerPos >= 0) {
            table.headerRow = rowIndexes.get(headerPos);
            table.photoColumn = findPhotoColumn(grid.get(headerPos));
        }

        String drawingPath = findDrawingPath(zip, sheetPath);
        if (drawingPath != null) {
            table.relTargets.putAll(readRels(zip, drawingPath));
            table.anchors.addAll(readAnchors(zip, drawingPath));
        }

        table.hasInCellImages = hasRichData(zip) && table.anchors.isEmpty();
        return table;
    }

    // ================= 部件定位 =================

    static String findFirstSheet(ZipFile zip) {
        String first = null;
        try {
            Map<String, String> workbookRels = readRels(zip, "xl/workbook.xml");
            final String[] relId = new String[1];
            parseXml(zip, "xl/workbook.xml", new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) {
                    if (relId[0] == null && "sheet".equals(local(qName))) {
                        relId[0] = attr(attributes, "id");
                    }
                }
            });
            if (relId[0] != null) {
                first = workbookRels.get(relId[0]);
            }
        } catch (Exception ignored) {
            // workbook.xml 读不出来就走下面的兜底路径
        }
        if (first != null && zip.getEntry(first) != null) {
            return first;
        }
        if (zip.getEntry("xl/worksheets/sheet1.xml") != null) {
            return "xl/worksheets/sheet1.xml";
        }
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.startsWith("xl/worksheets/") && name.endsWith(".xml")
                    && !name.contains("_rels")) {
                return name;
            }
        }
        throw new IllegalStateException("这个文件里没有工作表，可能不是 xlsx");
    }

    /** sheet → drawing 部件 */
    private static String findDrawingPath(ZipFile zip, String sheetPath) {
        String relsPath = relsPathFor(sheetPath);
        if (zip.getEntry(relsPath) == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : readRelsWithType(zip, relsPath).entrySet()) {
            if (entry.getKey().contains("/drawing")) {
                String target = entry.getValue();
                return zip.getEntry(target) != null ? target : null;
            }
        }
        return null;
    }

    private static boolean hasRichData(ZipFile zip) {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.startsWith("xl/richData/")) {
                return true;
            }
        }
        return false;
    }

    /** xl/worksheets/sheet1.xml → xl/worksheets/_rels/sheet1.xml.rels */
    static String relsPathFor(String partPath) {
        int slash = partPath.lastIndexOf('/');
        String dir = slash >= 0 ? partPath.substring(0, slash + 1) : "";
        String file = slash >= 0 ? partPath.substring(slash + 1) : partPath;
        return dir + "_rels/" + file + ".rels";
    }

    /**
     * 解析 .rels，返回 rId → 目标在 zip 内的路径。
     *
     * Target 可能是 "/xl/media/image1.png"（绝对）或 "../media/image1.png"（相对），
     * 两种都要归一化。
     */
    static Map<String, String> readRels(ZipFile zip, String partPath) {
        Map<String, String> result = new HashMap<>();
        String relsPath = relsPathFor(partPath);
        if (zip.getEntry(relsPath) == null) {
            return result;
        }
        result.putAll(readRelsWithType(zip, relsPath));
        return result;
    }

    /** key 同时收录 rId 和 Type，value 是解析后的 zip 内路径 */
    private static Map<String, String> readRelsWithType(ZipFile zip, String relsPath) {
        final Map<String, String> byId = new HashMap<>();
        final Map<String, String> byType = new HashMap<>();
        try {
            parseXml(zip, relsPath, new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) {
                    if (!"Relationship".equals(local(qName))) {
                        return;
                    }
                    String id = attr(attributes, "Id");
                    String target = attr(attributes, "Target");
                    String type = attr(attributes, "Type");
                    if (target == null) {
                        return;
                    }
                    String resolved = resolveTarget(relsPath, target);
                    if (id != null) {
                        byId.put(id, resolved);
                    }
                    if (type != null) {
                        byType.put(type, resolved);
                    }
                }
            });
        } catch (Exception ignored) {
            // 关系文件坏了，等同于这张表没有图片
        }
        Map<String, String> merged = new HashMap<>(byId);
        merged.putAll(byType);
        return merged;
    }

    /** 把 rels 里的相对 Target 解析成 zip 内路径 */
    static String resolveTarget(String relsPath, String target) {
        if (target.startsWith("/")) {
            return target.substring(1);
        }
        int idx = relsPath.lastIndexOf("/_rels/");
        String baseDir = idx >= 0 ? relsPath.substring(0, idx + 1) : "";
        String combined = baseDir + target;

        String[] parts = combined.split("/");
        List<String> stack = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
            } else {
                stack.add(part);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stack.size(); i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(stack.get(i));
        }
        return sb.toString();
    }

    // ================= 各部件解析 =================

    static List<String> readSharedStrings(ZipFile zip) {
        final List<String> result = new ArrayList<>();
        if (zip.getEntry("xl/sharedStrings.xml") == null) {
            return result;
        }
        try {
            parseXml(zip, "xl/sharedStrings.xml", new DefaultHandler() {
                private final StringBuilder text = new StringBuilder();
                private boolean inItem;
                private boolean inText;

                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) {
                    String name = local(qName);
                    if ("si".equals(name)) {
                        inItem = true;
                        text.setLength(0);
                    } else if ("t".equals(name) && inItem) {
                        inText = true;
                    }
                }

                @Override
                public void characters(char[] ch, int start, int length) {
                    if (inText) {
                        text.append(ch, start, length);
                    }
                }

                @Override
                public void endElement(String uri, String localName, String qName) {
                    String name = local(qName);
                    if ("t".equals(name)) {
                        inText = false;
                    } else if ("si".equals(name)) {
                        result.add(text.toString());
                        inItem = false;
                    }
                }
            });
        } catch (Exception ignored) {
            // 共享字符串读失败时，单元格会退化成空值，不至于整表报废
        }
        return result;
    }

    static void readSheet(ZipFile zip, String sheetPath,
                          final List<String> sharedStrings, final Table table) {
        try {
            parseXml(zip, sheetPath, new DefaultHandler() {
                private int currentRow = 0;
                private int currentCol = -1;
                private int lastCol = -1;
                private String cellType;
                private final StringBuilder valueBuf = new StringBuilder();
                private final StringBuilder textBuf = new StringBuilder();
                private boolean inValue;
                private boolean inText;

                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) {
                    String name = local(qName);
                    if (TAG_ROW.equals(name)) {
                        String r = attr(attributes, "r");
                        currentRow = r != null ? parseInt(r, currentRow + 1) : currentRow + 1;
                        lastCol = -1;
                    } else if ("c".equals(name)) {
                        String ref = attr(attributes, "r");
                        int col = ref != null ? columnOf(ref) : lastCol + 1;
                        if (col < 0) {
                            col = lastCol + 1;
                        }
                        currentCol = col;
                        lastCol = col;
                        cellType = attr(attributes, "t");
                        valueBuf.setLength(0);
                        textBuf.setLength(0);
                    } else if ("v".equals(name)) {
                        inValue = true;
                        valueBuf.setLength(0);
                    } else if ("t".equals(name)) {
                        // inlineStr 的 <is><t>；富文本会有多个 <t>，累加即可
                        inText = true;
                    }
                }

                @Override
                public void characters(char[] ch, int start, int length) {
                    if (inValue) {
                        valueBuf.append(ch, start, length);
                    }
                    if (inText) {
                        textBuf.append(ch, start, length);
                    }
                }

                @Override
                public void endElement(String uri, String localName, String qName) {
                    String name = local(qName);
                    if ("v".equals(name)) {
                        inValue = false;
                    } else if ("t".equals(name)) {
                        inText = false;
                    } else if ("c".equals(name)) {
                        storeCell();
                    }
                }

                private void storeCell() {
                    if (currentRow <= 0 || currentCol < 0) {
                        return;
                    }
                    String value;
                    if ("s".equals(cellType)) {
                        int idx = parseInt(valueBuf.toString().trim(), -1);
                        value = (idx >= 0 && idx < sharedStrings.size()) ? sharedStrings.get(idx) : "";
                    } else if ("inlineStr".equals(cellType)) {
                        value = textBuf.toString();
                    } else if ("b".equals(cellType)) {
                        value = "1".equals(valueBuf.toString().trim()) ? "TRUE" : "FALSE";
                    } else {
                        value = valueBuf.toString();
                    }
                    if (value.isEmpty()) {
                        return;
                    }
                    Map<Integer, String> row = table.rows.get(currentRow);
                    if (row == null) {
                        row = new HashMap<>();
                        table.rows.put(currentRow, row);
                    }
                    row.put(currentCol, value);
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("工作表内容读不出来: " + e.getMessage());
        }
    }

    static List<Anchor> readAnchors(ZipFile zip, String drawingPath) {
        final List<Anchor> result = new ArrayList<>();
        try {
            parseXml(zip, drawingPath, new DefaultHandler() {
                private int anchorRow = -1;
                private int anchorCol = -1;
                private boolean inFrom;
                private final StringBuilder buf = new StringBuilder();
                private String pendingTag;

                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) {
                    String name = local(qName);
                    if ("oneCellAnchor".equals(name) || "twoCellAnchor".equals(name)) {
                        anchorRow = -1;
                        anchorCol = -1;
                        inFrom = false;
                    } else if ("absoluteAnchor".equals(name)) {
                        // 没有 from，是浮在页面上而不属于任何单元格的图
                        anchorRow = -1;
                    } else if (TAG_FROM.equals(name)) {
                        inFrom = true;
                    } else if (TAG_TO.equals(name)) {
                        inFrom = false;
                    } else if (inFrom && (TAG_ROW.equals(name) || TAG_COL.equals(name))) {
                        pendingTag = name;
                        buf.setLength(0);
                    } else if (TAG_BLIP.equals(name)) {
                        // blip 在 from 之后出现，此刻行号已经就绪
                        String relId = attr(attributes, "embed");
                        if (relId != null && anchorRow >= 0) {
                            result.add(new Anchor(anchorRow, Math.max(anchorCol, 0), relId));
                        }
                    }
                }

                @Override
                public void characters(char[] ch, int start, int length) {
                    if (pendingTag != null) {
                        buf.append(ch, start, length);
                    }
                }

                @Override
                public void endElement(String uri, String localName, String qName) {
                    String name = local(qName);
                    if (pendingTag != null && pendingTag.equals(name)) {
                        int value = parseInt(buf.toString().trim(), 0);
                        if (TAG_ROW.equals(name)) {
                            anchorRow = value;
                        } else {
                            anchorCol = value;
                        }
                        pendingTag = null;
                    }
                    if (TAG_FROM.equals(name)) {
                        inFrom = false;
                    }
                }
            });
        } catch (Exception ignored) {
            // 图片锚点读不出来时，表格文字部分仍然可用
        }
        return result;
    }

    // ================= 小工具 =================

    /** 表头里找图片列 */
    static int findPhotoColumn(List<String> header) {
        for (int i = 0; i < header.size(); i++) {
            String key = CsvImporter.normalizeHeader(header.get(i))
                    .replaceAll("[（(].*?[)）]", "")
                    .trim();
            if (key.equals("图片") || key.equals("产品图片") || key.equals("照片")
                    || key.equals("image") || key.equals("photo") || key.equals("picture")
                    || key.equals("img")) {
                return i;
            }
        }
        return -1;
    }

    /** "H2" → 7 */
    static int columnOf(String cellRef) {
        int col = 0;
        for (int i = 0; i < cellRef.length(); i++) {
            char c = cellRef.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                col = col * 26 + (c - 'A' + 1);
            } else if (c >= 'a' && c <= 'z') {
                col = col * 26 + (c - 'a' + 1);
            } else {
                break;
            }
        }
        return col - 1;
    }

    private static List<String> toRowList(Map<Integer, String> cells) {
        int max = -1;
        for (Integer key : cells.keySet()) {
            if (key > max) {
                max = key;
            }
        }
        List<String> row = new ArrayList<>(Collections.nCopies(max + 1, ""));
        for (Map.Entry<Integer, String> entry : cells.entrySet()) {
            row.set(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return row;
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 去命名空间前缀：xdr:row → row，a:blip → blip */
    private static String local(String qName) {
        int idx = qName.indexOf(':');
        return idx >= 0 ? qName.substring(idx + 1) : qName;
    }

    /** 按本地名取属性，兼容 r:embed / embed 两种写法 */
    private static String attr(Attributes attributes, String wanted) {
        for (int i = 0; i < attributes.getLength(); i++) {
            if (wanted.equals(local(attributes.getQName(i)))) {
                return attributes.getValue(i);
            }
        }
        return null;
    }

    private static void parseXml(ZipFile zip, String entryPath, DefaultHandler handler)
            throws IOException, SAXException, ParserConfigurationException {
        ZipEntry entry = zip.getEntry(entryPath);
        if (entry == null) {
            throw new IOException("找不到部件: " + entryPath);
        }
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {
            // 部分平台的解析器不支持该特性
        }
        SAXParser parser = factory.newSAXParser();
        try (InputStream in = zip.getInputStream(entry)) {
            parser.parse(new InputSource(in), handler);
        }
    }
}
