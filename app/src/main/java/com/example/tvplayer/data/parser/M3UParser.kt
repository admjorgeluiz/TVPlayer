package com.example.tvplayer.data.parser

import com.example.tvplayer.data.model.M3UItem

object M3UParser {

    fun parse(content: String): List<M3UItem> {
        val lines = content.lines()
        val items = mutableListOf<M3UItem>()

        var currentName = ""
        var currentLogo: String? = null
        var currentGroup: String? = null

        for (line in lines) {
            val trimmedLine = line.trim()

            // Identifica a linha com metadados
            if (trimmedLine.startsWith("#EXTINF", ignoreCase = true)) {
                // Exemplo: #EXTINF:-1 tvg-logo="http://logo.com/img.png" group-title="Categoria",Nome do Canal
                val nameMatch = Regex(".*,\\s*(.*)").find(trimmedLine)
                val logoMatch = Regex("""tvg-logo="([^"]*)"""").find(trimmedLine)
                val groupMatch = Regex("""group-title="([^"]*)"""").find(trimmedLine)

                currentName = nameMatch?.groupValues?.get(1) ?: "Desconhecido"
                currentLogo = logoMatch?.groupValues?.get(1)
                currentGroup = groupMatch?.groupValues?.get(1)
            }

            // Linha com a URL do canal
            if (trimmedLine.startsWith("http", ignoreCase = true)) {
                items.add(
                    M3UItem(
                        name = currentName,
                        url = trimmedLine,
                        logo = currentLogo,
                        group = currentGroup
                    )
                )
            }
        }

        return items
    }
}
