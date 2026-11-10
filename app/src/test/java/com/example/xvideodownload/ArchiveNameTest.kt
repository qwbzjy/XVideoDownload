package com.example.xvideodownload

import com.example.xvideodownload.download.pickArchiveName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveNameTest {

    @Test
    fun `exact name wins`() {
        val names = listOf("2026-11-10.md.txt", "2026-11-10.md", "2026-11-09.md")
        assertEquals("2026-11-10.md", pickArchiveName(names, "2026-11-10.md"))
    }

    @Test
    fun `falls back to mime-suffixed legacy name`() {
        val names = listOf("2026-11-10.md.txt", "2026-11-09.md")
        assertEquals("2026-11-10.md.txt", pickArchiveName(names, "2026-11-10.md"))
    }

    @Test
    fun `prefers original over duplicate`() {
        val names = listOf("2026-11-10.md (1).txt", "2026-11-10.md.txt")
        assertEquals("2026-11-10.md.txt", pickArchiveName(names, "2026-11-10.md"))
    }

    @Test
    fun `prefers a real md file over txt leftovers`() {
        val names = listOf("2026-11-10.md.txt", "2026-11-10.md (1).md")
        assertEquals("2026-11-10.md (1).md", pickArchiveName(names, "2026-11-10.md"))
    }

    @Test
    fun `does not match other dates`() {
        val names = listOf("2026-11-09.md", "2026-11-11.md", "2026-11-01.md")
        assertNull(pickArchiveName(names, "2026-11-10.md"))
    }

    @Test
    fun `empty candidates`() {
        assertNull(pickArchiveName(emptyList(), "2026-11-10.md"))
    }
}
