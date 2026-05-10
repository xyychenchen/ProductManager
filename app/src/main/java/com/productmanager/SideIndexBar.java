package com.productmanager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 字母索引侧边栏
 * 类似通讯录的字母快速索引功能
 * 只显示已有产品的首字母
 * 
 * 设计理念（参考 FossifyOrg/Contacts）：
 * - 固定的字母间距，不管有多少字母
 * - 字母整体垂直居中
 * - 间距舒适，有空间感
 */
public class SideIndexBar extends View {
    
    // 默认全部字母（用于无产品时）
    private static final String[] ALL_LETTERS = {
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#"
    };
    
    // 字母间距（dp）- 舒适的间距，有空间感
    private static final float LETTER_SPACING_DP = 20f;
    
    // 字母文字大小（sp）
    private static final float LETTER_TEXT_SIZE_SP = 14f;
    
    // 当前显示的字母列表（动态）
    private List<String> currentLetters = new ArrayList<>();
    
    private Paint paint;
    private Paint selectedPaint;
    private int selectedPosition = -1;
    private OnLetterSelectedListener listener;
    
    // 实际使用的间距（像素）
    private float letterSpacing;
    
    public interface OnLetterSelectedListener {
        void onLetterSelected(String letter);
        void onLetterReleased();
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
        // 默认显示所有字母
        for (String letter : ALL_LETTERS) {
            currentLetters.add(letter);
        }
        
        // 将 dp 转换为像素
        float density = context.getResources().getDisplayMetrics().density;
        letterSpacing = LETTER_SPACING_DP * density;
        
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
    }
    
    /**
     * 设置要显示的字母列表
     * 只显示有产品的首字母
     * @param letters 字母列表（如 ["A", "B", "H"]）
     */
    public void setLetters(List<String> letters) {
        currentLetters.clear();
        
        if (letters == null || letters.isEmpty()) {
            // 如果没有产品，隐藏索引栏
            setVisibility(GONE);
            return;
        }
        
        // 按字母顺序排序
        java.util.Collections.sort(letters, (a, b) -> {
            if (a.equals("#")) return 1;
            if (b.equals("#")) return -1;
            return a.compareTo(b);
        });
        
        currentLetters.addAll(letters);
        setVisibility(VISIBLE);
        invalidate();
    }
    
    /**
     * 获取当前显示的字母列表
     */
    public List<String> getCurrentLetters() {
        return new ArrayList<>(currentLetters);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (getHeight() == 0 || getWidth() == 0 || currentLetters.isEmpty()) {
            return;
        }
        
        float centerX = getWidth() / 2f;
        int letterCount = currentLetters.size();
        
        // 计算所有字母需要的总高度
        float totalHeight = (letterCount - 1) * letterSpacing;
        
        // 计算起始Y位置（使字母整体居中）
        float startY;
        if (letterCount == 1) {
            // 只有一个字母时，放在正中间
            startY = getHeight() / 2f;
        } else {
            startY = (getHeight() - totalHeight) / 2f;
        }
        
        // 获取文字高度用于垂直居中
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textOffset = (fm.descent - fm.ascent) / 2 - fm.descent;
        
        // 绘制每个字母
        for (int i = 0; i < letterCount; i++) {
            float y = startY + i * letterSpacing;
            
            // 确保Y坐标在view范围内
            if (y < letterSpacing / 2) {
                y = letterSpacing / 2;
            } else if (y > getHeight() - letterSpacing / 2) {
                y = getHeight() - letterSpacing / 2;
            }
            
            if (i == selectedPosition) {
                canvas.drawText(currentLetters.get(i), centerX, y + textOffset, selectedPaint);
            } else {
                canvas.drawText(currentLetters.get(i), centerX, y + textOffset, paint);
            }
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (currentLetters.isEmpty()) {
            return false;
        }
        
        int letterCount = currentLetters.size();
        float totalHeight = (letterCount - 1) * letterSpacing;
        float startY;
        if (letterCount == 1) {
            startY = getHeight() / 2f;
        } else {
            startY = (getHeight() - totalHeight) / 2f;
        }
        
        float y = event.getY();
        
        // 计算触摸位置对应的字母索引
        int position = -1;
        for (int i = 0; i < letterCount; i++) {
            float letterY = startY + i * letterSpacing;
            // 检查触摸点是否在这个字母的范围内
            float halfSpacing = letterSpacing / 2;
            if (y >= letterY - halfSpacing && y < letterY + halfSpacing) {
                position = i;
                break;
            }
        }
        
        // 如果没有精确匹配，找最近的字母
        if (position == -1) {
            if (y < startY) {
                position = 0;
            } else {
                position = letterCount - 1;
            }
        }
        
        // 确保位置有效
        if (position < 0) position = 0;
        if (position >= letterCount) position = letterCount - 1;
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                selectedPosition = position;
                invalidate();
                if (listener != null) {
                    listener.onLetterSelected(currentLetters.get(position));
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
     * 高亮显示指定字母
     */
    public void highlightLetter(String letter) {
        for (int i = 0; i < currentLetters.size(); i++) {
            if (currentLetters.get(i).equals(letter)) {
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
