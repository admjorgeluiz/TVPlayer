package com.example.tvplayer.data.parser

import com.example.tvplayer.data.model.M3UItem
import java.io.BufferedReader
import java.io.StringReader

object M3UParser {
    /**
     * Faz o parse do conteúdo de um arquivo M3U usando um BufferedReader.
     * Este método lê o arquivo linha a linha e, sempre que encontra uma linha
     * com informações do canal (começando com "#EXTINF:"), espera que a próxima linha
     * contenha a URL do canal.
     */
    fun parseStream(reader: BufferedReader): List<M3UItem> {
        val channels = mutableListOf<M3UItem>()
        var currentName: String? = null
        reader.forEachLine { line ->
            when {
                line.startsWith("#EXTINF:") -> {
                    // Exemplo de linha: "#EXTINF:-1,Channel Name"
                    val commaIndex = line.indexOf(",")
                    if (commaIndex != -1 && commaIndex < line.length - 1) {
                        currentName = line.substring(commaIndex + 1).trim()
                    }
                }
                line.isNotBlank() && !line.startsWith("#") -> {
                    // Linha de URL
                    val localName = currentName
                    if (localName != null) {
                        channels.add(M3UItem(name = localName, url = line.trim()))
                        currentName = null
                    }
                }
            }
        }
        return channels
    }

    fun parse(content: String): List<M3UItem> {
        val reader = StringReader(content).buffered()
        return parseStream(reader)
    }
}
