package cz.tal0052.edisonrozvrh.data.repository

interface RoundcubeCookieProvider {
    fun cookiesFor(url: String): List<String>
}
