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
 */
public class SideIndexBar extends View {
    
    // 默认全部字母（用于无产品时）
    private static final String[] ALL_LETTERS = {
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#"
    };
    
    // 当前显示的字母列表（动态）
    private List<String> currentLetters = new ArrayList<>();
    
    private Paint paint;
    private Paint selectedPaint;
    private int selectedPosition = -1;
    private OnLetterSelectedListener listener;
    
    public interface OnLetterSelectedListener {
        void onLetterSelected(String letter);
        void onLetterReleased();
    }
    
    public void setOnLetterSelectedListener(OnLetterSelectedListener listener) {
        this.listener = listener;
    }
    
    public SideIndexBar(Context context) {
        super(context);
        init();
    }
    
    public SideIndexBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public SideIndexBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        // 默认显示所有字母
        for (String letter : ALL_LETTERS) {
            currentLetters.add(letter);
        }
        
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(36f);
        paint.setColor(0xFF666666);
        paint.setTextAlign(Paint.Align.CENTER);
        
        selectedPaint = new Paint();
        selectedPaint.setAntiAlias(true);
        selectedPaint.setTextSize(40f);
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
        
        float itemHeight = getHeight() / (float) currentLetters.size();
        float centerX = getWidth() / 2f;
        
        for (int i = 0; i < currentLetters.size(); i++) {
            float y = itemHeight * (i + 0.8f);
            
            if (i == selectedPosition) {
                canvas.drawText(currentLetters.get(i), centerX, y, selectedPaint);
            } else {
                canvas.drawText(currentLetters.get(i), centerX, y, paint);
            }
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (currentLetters.isEmpty()) {
            return false;
        }
        
        float itemHeight = getHeight() / (float) currentLetters.size();
        float y = event.getY();
        int position = (int) (y / itemHeight);
        
        if (position < 0) {
            position = 0;
        } else if (position >= currentLetters.size()) {
            position = currentLetters.size() - 1;
        }
        
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
