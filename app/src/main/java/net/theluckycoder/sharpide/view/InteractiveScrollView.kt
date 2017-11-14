package net.theluckycoder.sharpide.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView
import net.theluckycoder.sharpide.listener.OnBottomReachedListener
import net.theluckycoder.sharpide.listener.OnScrollListener


class InteractiveScrollView : ScrollView {

    private var onBottomReachedListener: OnBottomReachedListener? = null
    private var onScrollListener: OnScrollListener? = null

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onScrollChanged(l: Int, t: Int, oldL: Int, oldT: Int) {
        if (onScrollListener == null || onBottomReachedListener == null) return

        onScrollListener?.onScrolled()

        if (t > oldT)
            onScrollListener?.onScrolledDown()
        else
            onScrollListener?.onScrolledUp()

        val view = getChildAt(childCount - 1)
        val diff = view.bottom - (height + scrollY)

        if (diff <= 20)
            onBottomReachedListener?.onBottomReached()

        super.onScrollChanged(l, t, oldL, oldT)
    }

    fun setOnBottomReachedListener(onBottomReachedListener: OnBottomReachedListener?) {
        this.onBottomReachedListener = onBottomReachedListener
    }

}
