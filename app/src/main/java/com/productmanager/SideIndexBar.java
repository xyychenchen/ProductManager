package com.productmanager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 字母索引侧边栏
 * 类似通讯录的字母快速索引功能
 * 只显示已有产品的首字母
 * 如果有收藏产品，顶部显示星星
 */
public class SideIndexBar extends View {

    // 字母间距（dp）- 舒适的间距，有空间感
    private static final float LETTER_SPACING_DP = 20f;

    // 字母文字大小（sp）
    private static final float LETTER_TEXT_SIZE_SP = 14f;

    // 星星大小（dp）
    private static final float STAR_SIZE_DP = 16f;

    // 当前显示的元素列表（可能包含★和字母）
    private List<String> currentItems = new ArrayList<>();

    // 是否有收藏产品（用于绘制星星）
    private boolean hasFavorites = false;

    private Paint paint;
    private Paint selectedPaint;
    private Paint starPaint;
    private int selectedPosition = -1;
    private OnLetterSelectedListener listener;

    // 实际使用的间距（像素）
    private float letterSpacing;
    private float starSize;

    public interface OnLetterSelectedListener {
        void onLetterSelected(String letter);
        void onLetterReleased();
        void onStarSelected();
    }

    public void setOnLetterSelectedListener(OnLetterSelectedListener listener) {
        this.listener = listener;
    }

    public SideIndexBar(Context context) {
        super(context);
        init(context);
    }

    public SideIndexBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SideIndexBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // 将 dp 转换为像素
        float density = context.getResources().getDisplayMetrics().density;
        letterSpacing = LETTER_SPACING_DP * density;
        starSize = STAR_SIZE_DP * density;

        // 将 sp 转换为像素
        float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        float textSize = LETTER_TEXT_SIZE_SP * scaledDensity;

        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(textSize);
        paint.setColor(0xFF666666);
        paint.setTextAlign(Paint.Align.CENTER);

        selectedPaint = new Paint();
        selectedPaint.setAntiAlias(true);
        selectedPaint.setTextSize(textSize * 1.15f);
        selectedPaint.setColor(0xFF2196F3);
        selectedPaint.setFakeBoldText(true);
        selectedPaint.setTextAlign(Paint.Align.CENTER);

        // 填充星星画笔（金色）
        starPaint = new Paint();
        starPaint.setAntiAlias(true);
        starPaint.setColor(0xFFFFD700);
        starPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * 设置要显示的元素列表（从adapter获取，已包含★和字母）
     */
    public void setLetters(List<String> items) {
        currentItems.clear();

        if (items == null || items.isEmpty()) {
            setVisibility(GONE);
            return;
        }

        currentItems.addAll(items);
        setVisibility(VISIBLE);
        invalidate();
    }

    /**
     * 设置是否有收藏产品
     */
    public void setHasFavorites(boolean hasFavorites) {
        this.hasFavorites = hasFavorites;
        invalidate();
    }

    /**
     * 绘制星星形状
     */
    private void drawStar(Canvas canvas, float centerX, float centerY, float size, boolean selected) {
        Path path = new Path();
        float outerRadius = size / 2;
        float innerRadius = outerRadius * 0.4f;

        // 五角星有5个外顶点和5个内顶点
        for (int i = 0; i < 10; i++) {
            float radius = (i % 2 == 0) ? outerRadius : innerRadius;
            double angle = Math.toRadians(i * 36 - 90); // 从顶部开始
            float x = centerX + (float) (radius * Math.cos(angle));
            float y = centerY + (float) (radius * Math.sin(angle));

            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();

        // 选中时使用蓝色
        if (selected) {
            Paint selectedStarPaint = new Paint(starPaint);
            selectedStarPaint.setColor(0xFF2196F3);
            canvas.drawPath(path, selectedStarPaint);
        } else {
            canvas.drawPath(path, starPaint);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (getHeight() == 0 || getWidth() == 0 || currentItems.isEmpty()) {
            return;
        }

        float centerX = getWidth() / 2f;
        int itemCount = currentItems.size();

        // 计算所有元素需要的总高度
        float totalHeight = (itemCount - 1) * letterSpacing;

        // 计算起始Y位置（使元素整体居中）
        float startY;
        if (itemCount == 1) {
            startY = getHeight() / 2f;
        } else {
            startY = (getHeight() - totalHeight) / 2f;
        }

        // 获取文字高度用于垂直居中
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textOffset = (fm.descent - fm.ascent) / 2 - fm.descent;

        // 绘制每个元素
        for (int i = 0; i < itemCount; i++) {
            float y = startY + i * letterSpacing;

            // 确保Y坐标在view范围内
            if (y < letterSpacing / 2) {
                y = letterSpacing / 2;
            } else if (y > getHeight() - letterSpacing / 2) {
                y = getHeight() - letterSpacing / 2;
            }

            String item = currentItems.get(i);
            boolean isSelected = (i == selectedPosition);

            if (item.equals("★")) {
                // 绘制星星
                drawStar(canvas, centerX, y, starSize, isSelected);
            } else {
                // 绘制字母
                if (isSelected) {
                    canvas.drawText(item, centerX, y + textOffset, selectedPaint);
                } else {
                    canvas.drawText(item, centerX, y + textOffset, paint);
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (currentItems.isEmpty()) {
            return false;
        }

        int itemCount = currentItems.size();
        float totalHeight = (itemCount - 1) * letterSpacing;
        float startY;
        if (itemCount == 1) {
            startY = getHeight() / 2f;
        } else {
            startY = (getHeight() - totalHeight) / 2f;
        }

        float y = event.getY();

        // 计算触摸位置对应的元素索引
        int position = -1;
        for (int i = 0; i < itemCount; i++) {
            float itemY = startY + i * letterSpacing;
            float halfSpacing = letterSpacing / 2;
            if (y >= itemY - halfSpacing && y < itemY + halfSpacing) {
                position = i;
                break;
            }
        }

        // 如果没有精确匹配，找最近的元素
        if (position == -1) {
            if (y < startY) {
                position = 0;
            } else {
                position = itemCount - 1;
            }
        }

        // 确保位置有效
        if (position < 0) position = 0;
        if (position >= itemCount) position = itemCount - 1;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                selectedPosition = position;
                invalidate();
                if (listener != null) {
                    String item = currentItems.get(position);
                    if (item.equals("★")) {
                        listener.onStarSelected();
                    } else {
                        listener.onLetterSelected(item);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                selectedPosition = -1;
                invalidate();
                if (listener != null) {
                    listener.onLetterReleased();
                }
                break;
        }
        return true;
    }

    /**
     * 高亮显示指定元素
     */
    public void highlightLetter(String letter) {
        for (int i = 0; i < currentItems.size(); i++) {
            if (currentItems.get(i).equals(letter)) {
                selectedPosition = i;
                invalidate();
                return;
            }
        }
    }

    /**
     * 清除高亮
     */
    public void clearHighlight() {
        selectedPosition = -1;
        invalidate();
    }
}
