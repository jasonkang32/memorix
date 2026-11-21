package com.mebonsoft.memorix.core.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryCatalogTest {
    @Test
    fun defaultCountry_isKorea() {
        assertEquals("대한민국", CountryCatalog.defaultCountryName)
        assertEquals("KR", CountryCatalog.all.first().code)
    }

    @Test
    fun all_listsMajorCountriesFirstWithoutDuplicates() {
        val codes = CountryCatalog.all.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        val firstOther = CountryCatalog.all.indexOfFirst { !it.isMajor }
        assertTrue(CountryCatalog.all.take(firstOther).all { it.isMajor })
        assertTrue(CountryCatalog.all.drop(firstOther).none { it.isMajor })
        assertTrue(CountryCatalog.all.size > 200)
    }

    @Test
    fun search_matchesKoreanEnglishAndCode() {
        assertEquals("JP", CountryCatalog.search("일본").first().code)
        assertEquals("JP", CountryCatalog.search("japan").first().code)
        assertEquals("JP", CountryCatalog.search(" jp ").first().code)
        assertEquals(CountryCatalog.all, CountryCatalog.search(""))
        assertTrue(CountryCatalog.search("존재하지않는나라").isEmpty())
    }
}
