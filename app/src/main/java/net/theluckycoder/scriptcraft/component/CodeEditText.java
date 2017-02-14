package net.theluckycoder.scriptcraft.component;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.AppCompatEditText;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.ViewTreeObserver;

import net.theluckycoder.scriptcraft.R;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

public class CodeEditText extends AppCompatEditText {

    private final Pattern PATTERN_NUMBERS = Pattern.compile("\\b(\\d*[.]?\\d+)\\b");
    private final Pattern PATTERN_CLASSES = Pattern.compile(
            "^[\t ]*(Object|Function|Boolean|Symbol|Error|EvalError|InternalError|" +
                    "RangeError|ReferenceError|SyntaxError|TypeError|URIError|" +
                    "Number|Math|Date|String|RegExp|Map|Set|WeakMap|WeakSet|" +
                    "Array|ArrayBuffer|DataView|JSON|Promise|Generator|GeneratorFunction" +
                    "Reflect|Proxy|Intl)\\b",
            Pattern.MULTILINE);
    private final Pattern PATTERN_KEYWORDS = Pattern.compile(
            "\\b(break|case|catch|class|const|continue|debugger|default|delete|do|yield|" +
                    "else|export|extends|finally|for|function|if|import|in|instanceof|" +
                    "new|return|super|switch|this|throw|try|typeof|var|void|while|with|" +
                    "null|true|false)\\b");
    private final Pattern PATTERN_COMMENTS = Pattern.compile("/\\*(?:.|[\\n\\r])*?\\*/|//.*");
    private Context context;
    private final transient Paint paint = new Paint();
    private Layout layout;
    private final Handler updateHandler = new Handler();
    private OnTextChangedListener onTextChangedListener;
    private final int updateDelay = 1000;
    private boolean modified = true;
    private int colorNumber, colorKeyword, colorBuiltin, colorComment, colorString;
    private final Runnable updateRunnable =
            new Runnable() {
                @Override
                public void run() {
                    Editable e = getText();

                    if (onTextChangedListener != null)
                        onTextChangedListener.onTextChanged(
                                e.toString());

                    highlightWithoutChange(e);
                }
            };
    private BackPressedListener mOnImeBack;

    public CodeEditText(Context context) {
        super(context);

        init(context);
    }

    public CodeEditText(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    private static void clearSpans(Editable e) {
        // remove foreground color spans
        {
            ForegroundColorSpan spans[] = e.getSpans(0, e.length(), ForegroundColorSpan.class);

            for (int n = spans.length; n-- > 0; )
                e.removeSpan(spans[n]);
        }

        // remove background color spans
        {
            BackgroundColorSpan spans[] = e.getSpans(0, e.length(), BackgroundColorSpan.class);

            for (int n = spans.length; n-- > 0; )
                e.removeSpan(spans[n]);
        }
    }

    public void setTextHighlighted(CharSequence text) {
        if (text == null)
            text = "";

        cancelUpdate();

        modified = false;
        setText(highlight(new SpannableStringBuilder(text)));
        modified = true;

        if (onTextChangedListener != null)
            onTextChangedListener.onTextChanged(text.toString());
    }

    private void init(Context context) {
        setHorizontallyScrolling(true);

        setFilters(new InputFilter[]{
                new InputFilter() {
                    @Override
                    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dStart, int dEnd) {
                        if (modified &&
                                end - start == 1 &&
                                start < source.length() &&
                                dStart < dest.length()) {
                            char c = source.charAt(start);

                            if (c == '\n')
                                return autoIndent(source, dest, dStart, dEnd);
                        }

                        return source;
                    }
                }});

        addTextChangedListener(
                new TextWatcher() {

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void afterTextChanged(Editable e) {
                        cancelUpdate();

                        if (!modified)
                            return;

                        updateHandler.postDelayed(updateRunnable, updateDelay);
                    }
                });

        setSyntaxColors(context);
        this.context = context;
        Paint bgPaint = new Paint();
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Color.parseColor("#eeeeee"));

        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        paint.setColor(Color.parseColor("#bbbbbb"));
        paint.setTextSize(getPixels(Integer.parseInt(getDefaultSharedPreferences(context).getString("font_size", "15"))));
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                layout = getLayout();
            }
        });
    }

    private void setSyntaxColors(Context context) {
        colorNumber = ContextCompat.getColor(context, R.color.syntax_number);
        colorKeyword = ContextCompat.getColor(context, R.color.syntax_keyword);
        colorBuiltin = ContextCompat.getColor(context, R.color.syntax_class);
        colorComment = ContextCompat.getColor(context, R.color.syntax_comment);
        colorString = ContextCompat.getColor(context, R.color.syntax_string);
    }

    private void cancelUpdate() {
        updateHandler.removeCallbacks(updateRunnable);
    }

    private void highlightWithoutChange(Editable e) {
        modified = false;
        highlight(e);
        modified = true;
    }

    private Editable highlight(Editable editable) {
        try {
            // don't use e.clearSpans() because it will
            // remove too much
            clearSpans(editable);

            if (editable.length() == 0)
                return editable;

            for (Matcher m = PATTERN_NUMBERS.matcher(editable); m.find(); )
                editable.setSpan(new ForegroundColorSpan(colorNumber), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            for (Matcher m = PATTERN_CLASSES.matcher(editable); m.find(); )
                editable.setSpan(new ForegroundColorSpan(colorBuiltin), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            for (Matcher m = PATTERN_KEYWORDS.matcher(editable); m.find(); )
                editable.setSpan(new ForegroundColorSpan(colorKeyword), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            for(Matcher m = Pattern.compile("\\$\\w+").matcher(editable); m.find(); ) {
                editable.setSpan(new ForegroundColorSpan(colorKeyword), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            for(Matcher m = Pattern.compile("\"(.*?)\"|'(.*?)'").matcher(editable); m.find(); ) {
                ForegroundColorSpan spans[] = editable.getSpans(m.start(), m.end(), ForegroundColorSpan.class);
                for(ForegroundColorSpan span : spans)
                    editable.removeSpan(span);
                editable.setSpan(new ForegroundColorSpan(colorString), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            for (Matcher m = PATTERN_COMMENTS.matcher(editable); m.find(); ) {
                ForegroundColorSpan spans[] = editable.getSpans(m.start(), m.end(), ForegroundColorSpan.class);
                for(ForegroundColorSpan span : spans)
                    editable.removeSpan(span);
                editable.setSpan(new ForegroundColorSpan(colorComment), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (IllegalStateException e) {
            Log.e("IllegalStateException", e.getMessage(), e);
            // raised by Matcher.start()/.end() when
            // no successful match has been made what
            // shouldn't ever happen because of find()
        }

        return editable;
    }

    private CharSequence autoIndent(CharSequence source, Spanned dest, int dStart, int dEnd) {
        String indent = "";
        int iStart = dStart - 1;

        // find start of this line
        boolean dataBefore = false;
        int pt = 0;

        for (; iStart > -1; --iStart) {
            char c = dest.charAt(iStart);

            if (c == '\n')
                break;

            if (c != ' ' &&
                    c != '\t') {
                if (!dataBefore) {
                    // indent always after those characters
                    if (c == '{' || c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '^' || c == '=')
                        pt--;

                    dataBefore = true;
                }

                // parenthesis counter
                if (c == '(')
                    --pt;
                else if (c == ')')
                    ++pt;
            }
        }

        // copy indent of this line into the next
        if (iStart > -1) {
            char charAtCursor = dest.charAt(dStart);
            int iEnd;

            for (iEnd = ++iStart;
                 iEnd < dEnd;
                 ++iEnd) {
                char c = dest.charAt(iEnd);

                // auto expand comments
                if (charAtCursor != '\n' &&
                        c == '/' &&
                        iEnd + 1 < dEnd &&
                        dest.charAt(iEnd) == c) {
                    iEnd += 2;
                    break;
                }

                if (c != ' ' &&
                        c != '\t')
                    break;
            }

            indent += dest.subSequence(iStart, iEnd);
        }

        // add new indent
        if (pt < 0)
            indent += "\t";

        // append white space of previous line and new indent
        return source + indent;
    }

    private int getDigitCount() {
        int count = 0;
        int len = getLineCount();
        while (len > 0) {
            count++;
            len /= 10;
        }
        return count;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int padding = (int) getPixels(getDigitCount() * 10 + 10);
        setPadding(padding, 0, 0, 0);

        int scrollY = getScrollY();
        int firstLine = layout.getLineForVertical(scrollY), lastLine;

        try {
            lastLine = layout.getLineForVertical(scrollY + (getHeight() - getExtendedPaddingTop() - getExtendedPaddingBottom()));
        } catch (NullPointerException npe) {
            lastLine = layout.getLineForVertical(scrollY + (getHeight() - getPaddingTop() - getPaddingBottom()));
        }

        //the y position starts at the baseline of the first line
        int positionY = getBaseline() + (layout.getLineBaseline(firstLine) - layout.getLineBaseline(0));
        drawLineNumber(canvas, layout, positionY, firstLine);
        for (int i = firstLine + 1; i <= lastLine; i++) {
            //get the next y position using the difference between the current and last baseline
            positionY += layout.getLineBaseline(i) - layout.getLineBaseline(i - 1);
            drawLineNumber(canvas, layout, positionY, i);
        }

        super.onDraw(canvas);
    }

    private void drawLineNumber(Canvas canvas, Layout layout, int positionY, int line) {
        int positionX = (int) layout.getLineLeft(line);
        canvas.drawText(String.valueOf(line + 1), positionX + getPixels(2), positionY, paint);
    }

    private float getPixels(int dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    public interface OnTextChangedListener {
        void onTextChanged(String text);
    }

    /*** Keyboard checking ***/
    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP)
            if (mOnImeBack != null) mOnImeBack.onImeBack();
        return super.dispatchKeyEvent(event);
    }

    public void setBackPressedListener(BackPressedListener listener) {
        mOnImeBack = listener;
    }

    public interface BackPressedListener {
        void onImeBack();
    }
}
