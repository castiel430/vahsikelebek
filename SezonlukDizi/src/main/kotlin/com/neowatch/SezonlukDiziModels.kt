package com.neowatch

import com.fasterxml.jackson.annotation.JsonProperty


data class Kaynak(
    @JsonProperty("status") val status: String = "",
    @JsonProperty("data") val data: List<Veri> = emptyList(),
)

data class Veri(
    @JsonProperty("baslik") val baslik: String,
    @JsonProperty("id") val id: Int,
    @JsonProperty("kalite") val kalite: Int? = null,
)

data class AspData(
    val alternatif: String,
    val embed: String
)

data class SearchApiResponse(
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("results") val results: SearchApiResults? = null
)

data class SearchApiResults(
    @JsonProperty("diziler") val diziler: SearchApiCategory? = null
)

data class SearchApiCategory(
    @JsonProperty("results") val results: List<SearchApiItem>? = null
)

data class SearchApiItem(
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("image") val image: String? = null
)
