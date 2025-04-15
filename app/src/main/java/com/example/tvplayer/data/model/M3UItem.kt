package com.example.tvplayer.data.model

data class M3UItem(
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String? = null
)
