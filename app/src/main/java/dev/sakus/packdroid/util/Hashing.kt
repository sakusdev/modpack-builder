package dev.sakus.packdroid.util

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

data class HashPair(
    val sha1: String,
    val sha512: String
)

object Hashing {
    fun file(file: File): HashPair = file.inputStream().use(::stream)

    fun stream(input: InputStream): HashPair {
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha512 = MessageDigest.getInstance("SHA-512")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            sha1.update(buffer, 0, read)
            sha512.update(buffer, 0, read)
        }
        return HashPair(sha1.digest().hex(), sha512.digest().hex())
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}

fun safeFileName(value: String): String {
    val cleaned = value
        .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")
        .replace("..", "_")
        .trim()
    return cleaned.ifBlank { "file.jar" }.take(180)
}
