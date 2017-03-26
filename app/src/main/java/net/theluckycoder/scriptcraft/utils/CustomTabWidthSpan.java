package net.theluckycoder.scriptcraft.utils;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.text.style.ReplacementSpan;

public final class CustomTabWidthSpan extends ReplacementSpan {

    public CustomTabWidthSpan(){}


    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        return 20;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {

    }

}
