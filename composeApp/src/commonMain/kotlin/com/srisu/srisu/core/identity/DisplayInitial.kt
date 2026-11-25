package com.srisu.srisu.core.identity

/** An avatar initial must not cut an emoji's UTF-16 surrogate pair in half. */
fun displayInitial(name: String): String {
    val text = name.trim()
    if (text.isEmpty()) return ""
    val length = if (text.length > 1 && text[0].code in 0xD800..0xDBFF && text[1].code in 0xDC00..0xDFFF) 2 else 1
    return text.take(length).uppercase()
}
