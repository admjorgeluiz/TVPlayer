package com.jorgenascimento.tvplayer.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class M3UItem(
    val name: String,
    val logo: String?, // URL do logo, pode ser nulo
    val url: String,  // URL do stream
    val groupTitle: String?, // Título do grupo, pode ser nulo
) : Parcelable