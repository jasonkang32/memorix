package com.mebonsoft.memorix.core.locale

import java.util.Locale

/**
 * 위치 입력의 국가 목록.
 *
 * DB에는 기존 데이터·사진 위치 자동 입력(Geocoder countryName)과 맞추기 위해 한국어 국가명을 그대로 저장한다.
 */
data class CountryOption(
    val code: String,
    val koreanName: String,
    val englishName: String,
    val isMajor: Boolean,
)

object CountryCatalog {
    const val DEFAULT_COUNTRY_CODE = "KR"

    private val majorCodes = listOf(
        "KR", "US", "JP", "CN", "TW", "HK", "MO", "VN", "TH", "SG", "MY", "ID", "PH", "KH", "LA", "MM",
        "IN", "MN", "KZ", "UZ", "AU", "NZ", "CA", "MX", "BR", "AR", "CL", "PE", "CO",
        "GB", "FR", "DE", "IT", "ES", "PT", "NL", "BE", "CH", "AT", "IE", "SE", "NO", "DK", "FI",
        "PL", "CZ", "HU", "GR", "TR", "RU", "UA", "AE", "SA", "QA", "IL", "EG", "ZA",
    )

    /** 주요 국가(지정 순서) 다음에 나머지 국가(가나다순). */
    val all: List<CountryOption> by lazy {
        val major = majorCodes.map { option(it, isMajor = true) }
        val others = Locale.getISOCountries()
            .filterNot { it in majorCodes }
            .map { option(it, isMajor = false) }
            .filter { it.koreanName.isNotBlank() && it.koreanName != it.code }
            .sortedBy { it.koreanName }
        major + others
    }

    val defaultCountryName: String get() = option(DEFAULT_COUNTRY_CODE, isMajor = true).koreanName

    /** 한국어명·영어명·국가 코드로 찾는다. 빈 검색어면 전체 목록. */
    fun search(query: String): List<CountryOption> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return all
        return all.filter {
            it.koreanName.lowercase(Locale.ROOT).contains(normalized) ||
                it.englishName.lowercase(Locale.ROOT).contains(normalized) ||
                it.code.lowercase(Locale.ROOT) == normalized
        }
    }

    private fun option(code: String, isMajor: Boolean): CountryOption {
        val locale = Locale("", code)
        return CountryOption(
            code = code,
            koreanName = locale.getDisplayCountry(Locale.KOREAN),
            englishName = locale.getDisplayCountry(Locale.ENGLISH),
            isMajor = isMajor,
        )
    }
}
