package net.theluckycoder.sharpide.adapter

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.extensions.toDp

class SymbolsAdapter(
    private val onClickListener: (text: String) -> Unit
) : RecyclerView.Adapter<SymbolsAdapter.SymbolViewHolder>() {

    companion object {
        val SYMBOLS = charArrayOf(
            '{', '}', '(', ')', ';', '=', ',', '\"', '\'', '|', '&',
            '[', ']', '+', '-', '*', '/', '<', '>', '?', '!'
        )
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SymbolViewHolder {
        val context = parent.context

        val view = TextView(context).apply {
            layoutParams =
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            isClickable = true
            isFocusable = true

            setPadding(context.toDp(11), context.toDp(8), context.toDp(11), context.toDp(8))

            val outValue = TypedValue()
            context.theme.resolveAttribute(R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }

        val holder = SymbolViewHolder(view)

        view.setOnClickListener {
            val pos = holder.adapterPosition

            if (pos != -1)
                onClickListener(view.text.toString())
        }

        return holder
    }

    override fun getItemId(position: Int) = SYMBOLS[position].toLong()

    override fun getItemCount() = SYMBOLS.size

    override fun onBindViewHolder(holder: SymbolViewHolder, position: Int) {
        val symbol = SYMBOLS[position]

        holder.textView.text = symbol.toString()
    }

    class SymbolViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = itemView as TextView
    }
}
