package com.tcltv.sahne.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TmdbArtworkTest {

    @Test
    fun punctuationAndCaseDontMatterForAMatch() {
        assertEquals(TmdbArtwork.normalize("Dexter: Resurrection"), TmdbArtwork.normalize("dexter - resurrection"))
        assertEquals(TmdbArtwork.normalize("Çocuklar Duymasın"), TmdbArtwork.normalize("çocuklar duymasın!"))
    }

    @Test
    fun differentWordsStayDifferent() {
        assertNotEquals(TmdbArtwork.normalize("Lofi hip hop radio"), TmdbArtwork.normalize("Lofi"))
    }
}
