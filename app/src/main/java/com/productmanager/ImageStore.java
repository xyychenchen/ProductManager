package com.productmanager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 产品图片的统一存储与清理入口
 *
 * 设计目标：
 * 1. 所有图片只经过这一条管道写入，保证尺寸/质量一致，避免原图直接落盘
 * 2. 提供删除能力，删除产品、替换图片时能回收空间
 * 3. 只允许删除本应用图片目录下的文件，防止误删外部路径
 */
public final class ImageStore {

    private static final String TAG = "ImageStore";
    private static final String DIR_NAME = "product_images";

    /** 展示图长边上限（px）。列表与详情共用这一张，兼顾清晰度与体积 */
    private static final int MAX_EDGE = 1080;
    /** JPEG 质量 */
    private static final int QUALITY = 85;

    /** 同一毫秒内批量导入多张图时的序号，避免文件名相撞互相覆盖 */
    private static int sequence;

    private ImageStore() {
    }

    private static File dir(Context context) {
        File dir = new File(context.getFilesDir(), DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "无法创建图片目录: " + dir);
        }
        return dir;
    }

    /**
     * 读取任意来源的图片，降采样压缩后存入应用私有目录。
     *
     * 必须在后台线程调用（含 IO 与解码）。
     *
     * @return 新文件的绝对路径；失败返回 null
     */
    public static String importImage(Context context, Uri source) {
        if (source == null) {
            return null;
        }

        int sampleSize = calculateSampleSize(context, source);
        Bitmap bitmap = decodeSampled(context, source, sampleSize);
        if (bitmap == null) {
            return null;
        }

        File target = new File(dir(context), nextFileName());
        try (FileOutputStream fos = new FileOutputStream(target)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, fos);
            return target.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "写入图片失败", e);
            deleteQuietly(target.getAbsolutePath());
            return null;
        } finally {
            bitmap.recycle();
        }
    }

    /**
     * 从已有的本地文件导入（用于从 xlsx 里解包出来的图片，没有 Uri 可用）。
     *
     * 必须在后台线程调用。源文件不会被删除，由调用方负责清理。
     *
     * @return 新文件的绝对路径；解不出图像（例如 EMF/WMF 矢量图）时返回 null
     */
    public static String importFromFile(Context context, File source) {
        if (source == null || !source.isFile()) {
            return null;
        }
        String path = source.getAbsolutePath();

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        // 宽高为 -1 说明 BitmapFactory 不认识这个格式（xlsx 里可能混进 emf/wmf）
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "无法识别的图片格式: " + source.getName());
            return null;
        }

        int sampleSize = 1;
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        while (longest / sampleSize > MAX_EDGE) {
            sampleSize *= 2;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize;
        Bitmap bitmap = BitmapFactory.decodeFile(path, opts);
        if (bitmap == null) {
            return null;
        }

        File target = new File(dir(context), nextFileName());
        try (FileOutputStream fos = new FileOutputStream(target)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, fos);
            return target.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "写入图片失败", e);
            deleteQuietly(target.getAbsolutePath());
            return null;
        } finally {
            bitmap.recycle();
        }
    }

    private static synchronized String nextFileName() {
        sequence = (sequence + 1) % 100000;
        return "p_" + System.currentTimeMillis() + "_" + sequence + ".jpg";
    }

    /**
     * 把相机已经写好的临时文件压缩成标准展示图，并删除临时原图。
     *
     * 必须在后台线程调用。
     */
    public static String importCameraTempFile(Context context, String tempPath) {
        if (tempPath == null) {
            return null;
        }
        File temp = new File(tempPath);
        if (!temp.exists()) {
            return null;
        }
        try {
            String stored = importImage(context, Uri.fromFile(temp));
            return stored;
        } finally {
            // 原图体积大，压缩完成后立刻回收；即使失败也清理临时文件
            deleteQuietly(tempPath);
        }
    }

    private static int calculateSampleSize(Context context, Uri source) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            BitmapFactory.decodeStream(in, null, bounds);
        } catch (Exception e) {
            Log.e(TAG, "读取图片尺寸失败", e);
            return 1;
        }
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (longest / sample > MAX_EDGE) {
            sample *= 2;
        }
        return sample;
    }

    private static Bitmap decodeSampled(Context context, Uri source, int sampleSize) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize;
        // 不加 inPreferredConfig：降采样后单张约 1080px，ARGB_8888 峰值 ~4.6MB 可接受，
        // 换来照片色彩不过度失真（产品图常需要看清标签文字）
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            return BitmapFactory.decodeStream(in, null, opts);
        } catch (Exception e) {
            Log.e(TAG, "解码图片失败", e);
            return null;
        }
    }

    /**
     * 删除一张由本应用管理的图片。传外部路径会被忽略，避免误删相册文件。
     */
    public static void deleteManaged(Context context, String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        File file = new File(path);
        try {
            String managedRoot = dir(context).getCanonicalPath();
            String target = file.getCanonicalPath();
            if (!target.startsWith(managedRoot + File.separator)) {
                return;
            }
            deleteQuietly(target);
        } catch (IOException e) {
            Log.w(TAG, "路径校验失败，跳过删除: " + path, e);
        }
    }

    /**
     * 直接删除文件（用于临时文件清理），失败不抛出。
     */
    public static void deleteQuietly(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        File file = new File(path);
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "删除文件失败: " + path);
        }
    }
}
