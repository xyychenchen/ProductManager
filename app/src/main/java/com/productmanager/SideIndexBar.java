package com.productmanager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 字母索引侧边栏
 * 类似通讯录的字母快速索引功能
 */
public class SideIndexBar extends View {
    
    private static final String[] LETTERS = {
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#"
    };
    
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
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (getHeight() == 0 || getWidth() == 0) {
            return;
        }
        
        float itemHeight = getHeight() / (float) LETTERS.length;
        float centerX = getWidth() / 2f;
        
        for (int i = 0; i < LETTERS.length; i++) {
            float y = itemHeight * (i + 0.8f);
            
            if (i == selectedPosition) {
                canvas.drawText(LETTERS[i], centerX, y, selectedPaint);
            } else {
                canvas.drawText(LETTERS[i], centerX, y, paint);
            }
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float itemHeight = getHeight() / (float) LETTERS.length;
        float y = event.getY();
        int position = (int) (y / itemHeight);
        
        if (position < 0) {
            position = 0;
        } else if (position >= LETTERS.length) {
            position = LETTERS.length - 1;
        }
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                selectedPosition = position;
                invalidate();
                if (listener != null) {
                    listener.onLetterSelected(LETTERS[position]);
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
        for (int i = 0; i < LETTERS.length; i++) {
            if (LETTERS[i].equals(letter)) {
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
