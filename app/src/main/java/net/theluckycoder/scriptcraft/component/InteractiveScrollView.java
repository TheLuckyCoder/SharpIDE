package net.theluckycoder.scriptcraft.component;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

import net.theluckycoder.scriptcraft.listener.OnBottomReachedListener;
import net.theluckycoder.scriptcraft.listener.OnScrollListener;

public class InteractiveScrollView extends ScrollView {

    private OnBottomReachedListener onBottomReachedListener;
    private OnScrollListener onScrollListener;

    public InteractiveScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public InteractiveScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public InteractiveScrollView(Context context) {
        super(context);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldL, int oldT) {
        if (onScrollListener == null || onBottomReachedListener == null)
            return;

        onScrollListener.onScrolled();

        if (t > oldT)
            onScrollListener.onScrolledDown();
        else
            onScrollListener.onScrolledUp();

        View view = getChildAt(getChildCount() - 1);
        int diff = (view.getBottom() - (getHeight() + getScrollY()));

        if (diff <= 20 && onBottomReachedListener != null) {
            onBottomReachedListener.onBottomReached();
        }

        super.onScrollChanged(l, t, oldL, oldT);
    }

    public void setOnBottomReachedListener(
            OnBottomReachedListener onBottomReachedListener) {
        this.onBottomReachedListener = onBottomReachedListener;
    }

    public void setOnScrollListener(OnScrollListener onScrollListener) {
        this.onScrollListener = onScrollListener;
    }

}