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
import android.util.TypedValue;
import android.view.ViewTreeObserver;

import net.theluckycoder.scriptcraft.R;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

public class CodeEditText extends AppCompatEditText {

    private static final Pattern PATTERN_NUMBERS = Pattern.compile("\\b(\\d*[.]?\\d+)\\b");
    private static final Pattern PATTERN_CLASSES = Pattern.compile(
            "^[\t ]*(Object|Function|Boolean|Symbol|Error|EvalError|InternalError|" +
                    "RangeError|ReferenceError|SyntaxError|TypeError|URIError|" +
                    "Number|Math|Date|String|RegExp|Map|Set|WeakMap|WeakSet|" +
                    "Array|ArrayBuffer|DataView|JSON|Promise|Generator|GeneratorFunction" +
                    "Reflect|Proxy|Intl)\\b",
            Pattern.MULTILINE);
    private static final Pattern PATTERN_KEYWORDS = Pattern.compile(
            "\\b(break|case|catch|class|const|continue|debugger|default|delete|do|yield|" +
                    "else|export|extends|finally|for|function|if|import|in|instanceof|" +
                    "new|return|super|switch|this|throw|try|typeof|var|void|while|with|" +
                    "null|true|false)\\b");
    private static final Pattern PATTERN_COMMENTS = Pattern.compile("/\\*(?:.|[\\n\\r])*?\\*/|//.*");
    private Context context;
    public static final transient Paint paint = new Paint();
    private Layout layout;
    private final Handler updateHandler = new Handler();
    private OnTextChangedListener onTextChangedListener;
    private final int updateDelay = 500;
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
        paint.setTextSize(Integer.parseInt(getDefaultSharedPreferences(context).getString("font_size", "16")));
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                layout = getLayout();
            }
        });
        //TODO: Font
        /*String typeface = PreferenceManager.getDefaultSharedPreferences(context).getString("font_type", "normal");
        if (typeface.matches("normal"))
            paint.setTypeface(Typeface.DEFAULT);
        else if (typeface.matches("bold"))
            paint.setTypeface(Typeface.DEFAULT_BOLD);
        else if (typeface.matches("serif"))
            paint.setTypeface(Typeface.SERIF);
        else if (typeface.equals("sans-serif"))
            paint.setTypeface(Typeface.SANS_SERIF);
        else if (typeface.equals("monospace"))
            paint.setTypeface(Typeface.MONOSPACE);*/
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

    private Editable highlight(Editable e) {
        try {
            // don't use e.clearSpans() because it will
            // remove too much
            clearSpans(e);

            if (e.length() == 0)
                return e;

            for (Matcher m = PATTERN_NUMBERS.matcher(e); m.find(); )
                e.setSpan(new ForegroundColorSpan(colorNumber), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            for (Matcher m = PATTERN_CLASSES.matcher(e); m.find(); )
                e.setSpan(new ForegroundColorSpan(colorBuiltin), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            for (Matcher m = PATTERN_KEYWORDS.matcher(e); m.find(); )
                e.setSpan(new ForegroundColorSpan(colorKeyword), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            for(Matcher m = Pattern.compile("\\$\\w+").matcher(e); m.find(); ) {
                e.setSpan(new ForegroundColorSpan(colorKeyword), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            for(Matcher m = Pattern.compile("\"(.*?)\"|'(.*?)'").matcher(e); m.find(); ) {
                ForegroundColorSpan spans[] = e.getSpans(m.start(), m.end(), ForegroundColorSpan.class);
                for(ForegroundColorSpan span : spans)
                    e.removeSpan(span);
                e.setSpan(new ForegroundColorSpan(colorString), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            for (Matcher m = PATTERN_COMMENTS.matcher(e); m.find(); ) {
                ForegroundColorSpan spans[] = e.getSpans(m.start(), m.end(), ForegroundColorSpan.class);
                for(ForegroundColorSpan span : spans)
                    e.removeSpan(span);
                e.setSpan(new ForegroundColorSpan(colorComment), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (IllegalStateException ise) {
            ise.printStackTrace();
            // raised by Matcher.start()/.end() when
            // no successful match has been made what
            // shouldn't ever happen because of find()
        }

        return e;
    }

    private CharSequence autoIndent(CharSequence source, Spanned dest, int dstart, int dend) {
        String indent = "";
        int iStart = dstart - 1;

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
            char charAtCursor = dest.charAt(dstart);
            int iEnd;

            for (iEnd = ++iStart;
                 iEnd < dend;
                 ++iEnd) {
                char c = dest.charAt(iEnd);

                // auto expand comments
                if (charAtCursor != '\n' &&
                        c == '/' &&
                        iEnd + 1 < dend &&
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
}
