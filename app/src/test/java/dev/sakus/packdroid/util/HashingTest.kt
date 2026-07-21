package dev.sakus.packdroid.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream

class HashingTest {
    @Test
    fun knownHashesAreStable() {
        val result = Hashing.stream(ByteArrayInputStream("PackDroid".toByteArray()))
        assertEquals("6c8371e4f964f537a439a2def2d2d7153a863472", result.sha1)
        assertEquals(
            "f9cc8a8e07913443f81bb52971e994f61b3e108a89cfeef2163d9fa72142fc05" +
                "c65dbeed5aeb785027aa057e197d6f919cc503152d2fac6a8e95b1fca49bb7b7",
            result.sha512
        )
    }

    @Test
    fun unsafeFilenameIsSanitized() {
        val result = safeFileName("../bad:name.jar")
        assertFalse(result.contains(".."))
        assertFalse(result.contains(":"))
        assertEquals("__bad_name.jar", result)
    }
}
