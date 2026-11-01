package com.productmanager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * 字母索引侧边栏
 *
 * 类似通讯录的字母快速索引：只显示已有产品的首字母，收藏分组显示★。
 *
 * 相比旧实现的改进：
 * - 星形 Path 与选中画笔预先构建，拖动时不再产生垃圾对象
 * - 间距按可用高度自适应，大字号/小屏不会互相重叠
 * - 只有索引变化时才回调，避免拖动时反复触发列表滚动
 */
public class SideIndexBar extends View {

    private static final float MAX_SPACING_DP = 20f;
    private static final float TEXT_SIZE_SP = 14f;
    private static final float STAR_SIZE_DP = 16f;
    private static final String STAR = "★";

    private final List<String> items = new ArrayList<>();

    private Paint normalPaint;
    private Paint selectedPaint;
    private Paint starPaint;
    private Paint starSelectedPaint;

    private final Path starPath = new Path();
    private float starSizePx;
    private float maxSpacingPx;

    private int selectedPosition = -1;
    /** 上一次真正通知出去的元素，用于去重 */
    private String lastDispatched;
    private OnLetterSelectedListener listener;

    public interface OnLetterSelectedListener {
        void onLetterSelected(String letter);

        void onLetterReleased();

        void onStarSelected();
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
        float density = context.getResources().getDisplayMetrics().density;
        maxSpacingPx = MAX_SPACING_DP * density;
        starSizePx = STAR_SIZE_DP * density;

        float textSize = TEXT_SIZE_SP * context.getResources().getDisplayMetrics().scaledDensity;

        normalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        normalPaint.setTextSize(textSize);
        normalPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        normalPaint.setTextAlign(Paint.Align.CENTER);

        selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedPaint.setTextSize(textSize * 1.15f);
        selectedPaint.setColor(ContextCompat.getColor(context, R.color.primary));
        selectedPaint.setFakeBoldText(true);
        selectedPaint.setTextAlign(Paint.Align.CENTER);

        starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        starPaint.setStyle(Paint.Style.FILL);
        starPaint.setColor(0xFFFFD700);

        starSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        starSelectedPaint.setStyle(Paint.Style.FILL);
        starSelectedPaint.setColor(ContextCompat.getColor(context, R.color.primary));

        buildStarPath();
    }

    public void setOnLetterSelectedListener(OnLetterSelectedListener listener) {
        this.listener = listener;
    }

    /**
     * 设置要显示的元素（★ + 字母），由 Adapter 提供
     */
    public void setItems(List<String> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        selectedPosition = -1;
        lastDispatched = null;

        if (items.isEmpty()) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        invalidate();
    }

    /**
     * 高亮指定元素（滚动时同步）
     */
    public void highlightLetter(String letter) {
        int index = items.indexOf(letter);
        if (index >= 0 && index != selectedPosition) {
            selectedPosition = index;
            invalidate();
        }
    }

    public void clearHighlight() {
        if (selectedPosition != -1) {
            selectedPosition = -1;
            invalidate();
        }
    }

    /**
     * 只构建一次：拖动时不需要再 new Path
     */
    private void buildStarPath() {
        starPath.reset();
        float outer = starSizePx / 2f;
        float inner = outer * 0.4f;
        for (int i = 0; i < 10; i++) {
            float radius = (i % 2 == 0) ? outer : inner;
            double angle = Math.toRadians(i * 36 - 90);
            float x = (float) (radius * Math.cos(angle));
            float y = (float) (radius * Math.sin(angle));
            if (i == 0) {
                starPath.moveTo(x, y);
            } else {
                starPath.lineTo(x, y);
            }
        }
        starPath.close();
    }

    /**
     * 元素间距：优先 20dp，空间不够时按可用高度压缩，保证不重叠
     */
    private float spacing() {
        int count = items.size();
        if (count <= 1) {
            return 0f;
        }
        float available = getHeight() - getPaddingTop() - getPaddingBottom();
        return Math.max(1f, Math.min(maxSpacingPx, available / count));
    }

    private float startY() {
        int count = items.size();
        if (count <= 1) {
            return getHeight() / 2f;
        }
        float totalHeight = spacing() * (count - 1);
        return (getHeight() - totalHeight) / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int count = items.size();
        if (count == 0 || getHeight() == 0 || getWidth() == 0) {
            return;
        }

        float centerX = getWidth() / 2f;
        float step = spacing();
        float startY = startY();

        Paint.FontMetrics fm = normalPaint.getFontMetrics();
        float textOffset = (fm.descent - fm.ascent) / 2f - fm.descent;

        for (int i = 0; i < count; i++) {
            float y = startY + i * step;
            String item = items.get(i);
            boolean selected = (i == selectedPosition);

            if (STAR.equals(item)) {
                canvas.save();
                canvas.translate(centerX, y);
                canvas.drawPath(starPath, selected ? starSelectedPaint : starPaint);
                canvas.restore();
            } else {
                canvas.drawText(item, centerX, y + textOffset, selected ? selectedPaint : normalPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (items.isEmpty()) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // 拖动索引时不要让父容器（RecyclerView）抢走事件
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                handleTouch(event.getY());
                return true;

            case MotionEvent.ACTION_MOVE:
                handleTouch(event.getY());
                return true;

            case MotionEvent.ACTION_UP:
                performClick();
                clearHighlight();
                lastDispatched = null;
                if (listener != null) {
                    listener.onLetterReleased();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                clearHighlight();
                lastDispatched = null;
                if (listener != null) {
                    listener.onLetterReleased();
                }
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    private void handleTouch(float y) {
        int count = items.size();
        int position;
        if (count == 1) {
            position = 0;
        } else {
            float step = spacing();
            position = Math.round((y - startY()) / step);
            position = Math.max(0, Math.min(count - 1, position));
        }

        if (position != selectedPosition) {
            selectedPosition = position;
            invalidate();
        }

        String item = items.get(position);
        if (item.equals(lastDispatched)) {
            return;
        }
        lastDispatched = item;

        if (listener == null) {
            return;
        }
        if (STAR.equals(item)) {
            listener.onStarSelected();
        } else {
            listener.onLetterSelected(item);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
