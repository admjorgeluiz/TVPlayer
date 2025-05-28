package com.jorgenascimento.tvplayer.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// SR_SUGGESTION: Transforme M3UItem em uma data class para DiffUtil funcionar melhor.
// Adicione @Parcelize e implemente Parcelable se precisar passar M3UItem entre componentes via Intent.
@Parcelize
data class M3UItem(
    val name: String,
    val logo: String?, // URL do logo, pode ser nulo
    val url: String,  // URL do stream
    val groupTitle: String?, // Título do grupo, pode ser nulo
    // Adicione um ID único se possível, para DiffUtil.areItemsTheSame
    // val id: String = url // Usar a URL como ID pode ser uma opção se for única
) : Parcelable