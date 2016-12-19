package net.theluckycoder.scriptcraft.utils;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.text.style.ReplacementSpan;

public class CustomTabWidthSpan extends ReplacementSpan {

    private int tabWidth = 0;

    public CustomTabWidthSpan(int tabWidth){
        this.tabWidth=tabWidth;
    }

    @Override
    public int getSize(@NonNull Paint p1, CharSequence p2, int p3, int p4, Paint.FontMetricsInt p5) {
        return tabWidth;
    }

    @Override
    public void draw(@NonNull Canvas p1, CharSequence p2, int p3, int p4, float p5, int p6, int p7, int p8, @NonNull Paint p9) {

    }
}
