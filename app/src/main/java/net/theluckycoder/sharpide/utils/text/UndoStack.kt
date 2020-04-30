package net.theluckycoder.sharpide.utils.text

import java.util.ArrayList

class UndoStack {

    companion object {
        const val MAX_SIZE = 1048576
    }

    private var currentTextSize = 0
    private val stack = ArrayList<TextChange>()

    fun pop(): TextChange? {
        val size = stack.size
        if (size <= 0) {
            return null
        }
        val item = stack[size - 1]
        stack.removeAt(size - 1)
        currentTextSize -= item.newText.length + item.oldText.length
        return item
    }

    fun push(item: TextChange) {
        val oldText = item.oldText
        val newText = item.newText
        val delta = newText.length + oldText.length

        if (delta >= MAX_SIZE) {
            clear()
            return
        }

        if (stack.size > 0) {
            val previous = stack[stack.size - 1]
            val toCharArray = previous.newText.toCharArray()
            val length = toCharArray.size
            var i = 0

            if (oldText.isEmpty() && newText.length == 1 && previous.oldText.isEmpty()) {
                when {
                    previous.start + previous.newText.length != item.start -> stack.add(item)
                    newText[0].isWhitespace() -> {
                        var allWhitespace = true

                        while (i < length) {
                            if (!toCharArray[i].isWhitespace()) {
                                allWhitespace = false
                                break
                            }
                            i++
                        }

                        if (allWhitespace) {
                            previous.newText += newText
                        } else {
                            stack.add(item)
                        }
                    }
                    newText[0].isLetterOrDigit() -> {
                        var allLettersDigits = true

                        while (i < length) {
                            if (!toCharArray[i].isLetterOrDigit()) {
                                allLettersDigits = false
                                break
                            }
                            i++
                        }

                        if (allLettersDigits) {
                            previous.newText += newText
                        } else {
                            stack.add(item)
                        }
                    }
                    else -> {
                        stack.add(item)
                    }
                }
            } else if (oldText.length != 1 || newText.isNotEmpty() || previous.newText.isNotEmpty()) {
                stack.add(item)
            } else if (previous.start - 1 != item.start) {
                stack.add(item)
            } else if (oldText[0].isWhitespace()) {
                var allWhitespace = true

                while (i < length) {
                    if (!toCharArray[i].isWhitespace()) {
                        allWhitespace = false
                        break
                    }
                    i++
                }

                if (allWhitespace) {
                    previous.oldText = oldText + previous.oldText
                    previous.start -= oldText.length
                } else {
                    stack.add(item)
                }
            } else if (oldText[0].isLetterOrDigit()) {
                var allLettersDigits = true

                while (i < length) {
                    if (!toCharArray[i].isLetterOrDigit()) {
                        allLettersDigits = false
                        break
                    }
                    i++
                }

                if (allLettersDigits) {
                    previous.oldText = oldText + previous.oldText
                    previous.start -= oldText.length
                } else {
                    stack.add(item)
                }
            } else {
                stack.add(item)
            }
        } else
            stack.add(item)

        currentTextSize += delta
        while (currentTextSize > MAX_SIZE) {
            if (!removeLast())
                return
        }
    }

    private fun removeLast(): Boolean {
        if (stack.size <= 0)
            return false

        val (newText, oldText) = stack[0]
        stack.removeAt(0)
        currentTextSize -= newText.length + oldText.length
        return true
    }

    fun clear() {
        stack.clear()
        currentTextSize = 0
    }
}
