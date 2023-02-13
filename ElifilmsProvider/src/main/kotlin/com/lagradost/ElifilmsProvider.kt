package com.lagradost

import android.widget.Toast
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class ElifilmsProvider : MainAPI() {
    override var mainUrl = "https://sololatino.net"
    override var name = "SLatino"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    override val vpnStatus = VPNStatus.MightBeNeeded //Due to evoload sometimes not loading
    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl/peliculas/", "Peliculas"),
            Pair("$mainUrl/series/", "Series"),
            Pair("$mainUrl/animes/", "animes"),
        )
        urls.apmap { (url, name) ->
            try {
                val soup = app.get(url).document
                val home = soup.select(".item").map {
                    val title = it.selectFirst("h3")!!.text()
                    val link = it.selectFirst("a")!!.attr("href")
                    TvSeriesSearchResponse(
                        title,
                        link,
                        this.name,
                        if (link.contains("/peliculas/")) TvType.Movie else TvType.TvSeries,
                        it.selectFirst("img")!!.attr("data-srcset"),
                        null,
                        null,
                    )
                }

                items.add(HomePageList(name, home))
            } catch (e: Exception) {
                logError(e)
            }
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query}"
        val document = app.get(url).document

        return document.select(".item").map {
            val title = it.selectFirst("h3")!!.text()
            val href = it.selectFirst("a")!!.attr("href")
            val image = it.selectFirst("img")!!.attr("srcset")
            val isMovie = href.contains("/peliculas/")

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
        }.toList()
    }


    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url, timeout = 120).document

        val title = soup.selectFirst(".data h1")!!.text()
        val description = soup.selectFirst(".wp-content p")?.text()?.trim()
        val poster = soup.selectFirst("div#galeria a")!!.attr("href")
        val episodes = soup.select("ul.episodios li").map { li ->
            val href = (li.select("a")).attr("href")
            val epThumb = li.selectFirst(".imagen img")!!.attr("src")
            val seasonid = li.selectFirst(".numerando")!!.text().let { str ->
                str.split("-").mapNotNull { subStr -> subStr.toIntOrNull() }
            }
            val isValid = seasonid.size == 2
            val episode = if (isValid) seasonid.getOrNull(1) else null
            val season = if (isValid) seasonid.getOrNull(0) else null
            Episode(
                href,
                null,
                season,
                episode,
                fixUrl(epThumb)
            )
        }
        return when (val tvType =
            if (url.contains("/peliculas/")) TvType.Movie else TvType.TvSeries) {
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
        app.get(data).document.select("iframe > src").map { script ->
            fetchUrls(script.data()
                .replace("https://api.mycdn.moe/furl.php?id=","https://www.fembed.com/v/")
                .replace("https://api.mycdn.moe/sblink.php?id=","https://streamsb.net/e/"))
                .apmap { link ->
                    if (link.contains("https://api.mycdn.moe/video/") || link.contains("https://api.mycdn.moe/embed.php?customid") || link.contains("https://embedsito.net/video/")) {
                        val doc = app.get(link).document
                        doc.select("div.ODDIV li").apmap {
                            val linkencoded = it.attr("data-r")
                            val linkdecoded = base64Decode(linkencoded)
                                .replace(Regex("https://owodeuwu.xyz|https://sypl.xyz"),"https://embedsito.com")
                                .replace(Regex(".poster.*"),"")
                            val secondlink = it.attr("onclick").substringAfter("go_to_player('").substringBefore("',")
                            loadExtractor(linkdecoded, link,subtitleCallback, callback)
                            val restwo = app.get("https://api.mycdn.moe/player/?id=$secondlink", allowRedirects = false).document
                            val thirdlink = restwo.selectFirst("body > iframe")?.attr("src")
                                ?.replace(Regex("https://owodeuwu.xyz|https://sypl.xyz"),"https://embedsito.com")
                                ?.replace(Regex(".poster.*"),"")
                            loadExtractor(thirdlink!!, link, subtitleCallback, callback)

                        }
                    }
                    loadExtractor(link, data, subtitleCallback, callback)
                }
        }
        return true
    }
}
