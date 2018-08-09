package net.theluckycoder.sharpide.utils.text

data class TextChange(
    var newText: String = "",
    var oldText: String = "",
    var start: Int = 0
)
