package com.llamatik.sdk.assistant

data class ConversationMessage(
    val text: String,
    val author: Author,
    val imagePng: ByteArray? = null,
    val imageFileName: String? = null,
    val imageRgba: ByteArray? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
) {
    val isFromMe: Boolean get() = author == Author.ME
    val hasImage: Boolean get() = imagePng != null || imageRgba != null

    enum class Author { ME, BOT }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as ConversationMessage
        if (text != other.text) return false
        if (author != other.author) return false
        if (imagePng != null && other.imagePng != null && !imagePng.contentEquals(other.imagePng)) return false
        if (imageRgba != null && other.imageRgba != null && !imageRgba.contentEquals(other.imageRgba)) return false
        if (imageWidth != other.imageWidth) return false
        if (imageHeight != other.imageHeight) return false
        if (imageFileName != other.imageFileName) return false
        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + author.hashCode()
        result = 31 * result + (imagePng?.contentHashCode() ?: 0)
        result = 31 * result + (imageFileName?.hashCode() ?: 0)
        result = 31 * result + (imageRgba?.contentHashCode() ?: 0)
        result = 31 * result + (imageWidth ?: 0)
        result = 31 * result + (imageHeight ?: 0)
        return result
    }
}
