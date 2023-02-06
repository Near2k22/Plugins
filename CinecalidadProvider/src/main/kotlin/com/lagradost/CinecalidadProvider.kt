package com.lagradost

import com.lagradost.cloudstream3.*
// import com.lagradost.cloudstream3.extractors.Cinestart
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class CinecalidadProvider : MainAPI() {
    override var mainUrl = "https://cinecalidad.ms"
    override var name = "Calidad"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    override val vpnStatus = VPNStatus.MightBeNeeded //Due to evoload sometimes not loading

    override val mainPage = mainPageOf(
        Pair("$mainUrl/serie/", "Series"),
        Pair("$mainUrl/", "Peliculas"),
        Pair("$mainUrl/4k/", "4K ULTRA HD"),
    )

    override suspend fun getMainPage(
        page: Int,
        request : MainPageRequest
    ): HomePageResponse {
        val url = request.data + page

        val soup = app.get(url).document
        val home = soup.select(".relative.group").map {
            val title = it.selectFirst(".sr-only")!!.text()
            val link = it.selectFirst("a")!!.attr("href")
            TvSeriesSearchResponse(
                title,
                link,
                this.name,
                if (link.contains("/pelicula/")) TvType.Movie else TvType.TvSeries,
                it.selectFirst("img")!!.attr("data-src")?.replace("-200x300", ""),
                null,
                null,
            )
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query}"
        val document = app.get(url).document

        return document.select(".relative.group").map {
            val title = it.selectFirst(".sr-only")!!.text()
            val href = it.selectFirst("a")!!.attr("href")
            val image = it.selectFirst("img")!!.attr("data-src")?.replace("-200x300", "")
            val isMovie = href.contains("/pelicula/")

            if (isMovie) {
                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Movie,
                    image,
                    null
                )
            } else {
                TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.TvSeries,
                    image,
                    null,
                    null
                )
            }
        }
    }


    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url, timeout = 120).document

        val title = soup.selectFirst(".mb-2.text-lg")!!.text()
        val description = soup.selectFirst("div.textwidget.max-w-none p")?.text()?.trim()
        val poster: String? = soup.selectFirst(".gap-4 img")?.attr("data-src")?.replace("-400x600", "")
        val episodes = soup.select("ul.episodios li.mark-1").map { li ->
            val href = li.selectFirst("a")!!.attr("href")
            val epThumb = li.selectFirst("img")!!.attr("data-src")
            val name = li.selectFirst(".episodiotitle a")!!.text()
            val seasonid =
                li.selectFirst(".numerando")!!.text().replace(Regex("(S|EP)"), "").let { str ->
                    str.split("-").mapNotNull { subStr -> subStr.toIntOrNull() }
                }
            val isValid = seasonid.size == 2
            val episode = if (isValid) seasonid.getOrNull(1) else null
            val season = if (isValid) seasonid.getOrNull(0) else null
            Episode(
                href,
                name,
                season,
                episode,
                if (epThumb.contains("svg")) null else epThumb
            )
        }
        return when (val tvType =
            if (url.contains("/pelicula/")) TvType.Movie else TvType.TvSeries) {
            TvType.TvSeries -> {
                TvSeriesLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes,
                    poster,
                    null,
                    description,
                )
            }
            TvType.Movie -> {
                MovieLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    url,
                    poster,
                    null,
                    description,

                )
            }
            else -> null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val datam = app.get(data)
        val doc = datam.document
        val datatext = datam.text

        doc.select(".py-1").apmap {
            val url = it.attr("data-src")
            val urldecode = base64Decode(url)
            if (urldecode.startsWith("https://cinecalidad.ms")) {
                val linkdentro = app.get(urldecode, timeout = 120).document
                val iframe = fixUrl(linkdentro.select("iframe").attr("src"))
                            loadExtractor(iframe, mainUrl, subtitleCallback, callback)



            }
        }
        return true
    }
}
