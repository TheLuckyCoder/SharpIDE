package net.theluckycoder.scriptcraft.utils;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.text.style.ReplacementSpan;

public class CustomTabWidthSpan extends ReplacementSpan {

    private int tabWidth = 10;

    public CustomTabWidthSpan(int tabWidth){
        this.tabWidth=tabWidth;
    }


    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        return tabWidth;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {

    }


}
