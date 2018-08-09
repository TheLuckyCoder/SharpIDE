package net.theluckycoder.sharpide.utils.text

import java.util.ArrayList

class UndoStack {

    companion object {
        const val MAX_SIZE = 1048576
    }

    private var mCurrentSize = 0
    private val mStack = ArrayList<TextChange>()

    fun pop(): TextChange? {
        val size = mStack.size
        if (size <= 0) {
            return null
        }
        val item = mStack[size - 1]
        mStack.removeAt(size - 1)
        mCurrentSize -= item.newText.length + item.oldText.length
        return item
    }

    fun push(item: TextChange) {
        var i = 0
        val delta = item.newText.length + item.oldText.length

        if (delta < MAX_SIZE) {
            if (mStack.size > 0) {
                val previous = mStack[mStack.size - 1]
                var allWhitespace: Boolean
                val toCharArray: CharArray
                val length: Int
                var allLettersDigits: Boolean
                if (item.oldText.isEmpty() && item.newText.length == 1 && previous.oldText.isEmpty()) {
                    if (previous.start + previous.newText.length != item.start) {
                        mStack.add(item)
                    } else if (Character.isWhitespace(item.newText[0])) {
                        allWhitespace = true
                        toCharArray = previous.newText.toCharArray()
                        length = toCharArray.size
                        while (i < length) {
                            if (!Character.isWhitespace(toCharArray[i])) {
                                allWhitespace = false
                            }
                            i++
                        }
                        if (allWhitespace) {
                            previous.newText += item.newText
                        } else {
                            mStack.add(item)
                        }
                    } else if (Character.isLetterOrDigit(item.newText[0])) {
                        allLettersDigits = true
                        toCharArray = previous.newText.toCharArray()
                        length = toCharArray.size
                        while (i < length) {
                            if (!Character.isLetterOrDigit(toCharArray[i])) {
                                allLettersDigits = false
                            }
                            i++
                        }
                        if (allLettersDigits) {
                            previous.newText += item.newText
                        } else {
                            mStack.add(item)
                        }
                    } else {
                        mStack.add(item)
                    }
                } else if (item.oldText.length != 1 || item.newText.isNotEmpty() || previous.newText.isNotEmpty()) {
                    mStack.add(item)
                } else if (previous.start - 1 != item.start) {
                    mStack.add(item)
                } else if (Character.isWhitespace(item.oldText[0])) {
                    allWhitespace = true
                    toCharArray = previous.oldText.toCharArray()
                    length = toCharArray.size
                    while (i < length) {
                        if (!Character.isWhitespace(toCharArray[i])) {
                            allWhitespace = false
                        }
                        i++
                    }
                    if (allWhitespace) {
                        previous.oldText = item.oldText + previous.oldText
                        previous.start -= item.oldText.length
                    } else {
                        mStack.add(item)
                    }
                } else if (Character.isLetterOrDigit(item.oldText[0])) {
                    allLettersDigits = true
                    toCharArray = previous.oldText.toCharArray()
                    length = toCharArray.size
                    while (i < length) {
                        if (!Character.isLetterOrDigit(toCharArray[i])) {
                            allLettersDigits = false
                        }
                        i++
                    }
                    if (allLettersDigits) {
                        previous.oldText = item.oldText + previous.oldText
                        previous.start -= item.oldText.length
                    } else {
                        mStack.add(item)
                    }
                } else {
                    mStack.add(item)
                }
            } else {
                mStack.add(item)
            }
            mCurrentSize += delta
            while (mCurrentSize > MAX_SIZE) {
                if (!removeLast()) {
                    return
                }
            }
            return
        }
        removeAll()
    }

    fun removeAll() {
        mStack.removeAll(mStack)
        mCurrentSize = 0
    }

    private fun removeLast(): Boolean {
        if (mStack.size <= 0) {
            return false
        }
        val (newText, oldText) = mStack[0]
        mStack.removeAt(0)
        mCurrentSize -= newText.length + oldText.length
        return true
    }

    fun clear() {
        mStack.clear()
    }
}