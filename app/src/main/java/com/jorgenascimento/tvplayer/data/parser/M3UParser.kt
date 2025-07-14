package com.jorgenascimento.tvplayer.data.parser

import com.jorgenascimento.tvplayer.data.model.M3UItem
import java.io.BufferedReader
import java.io.StringReader

object M3UParser {

    private fun extractAttribute(extInfLine: String, attributeName: String): String? {
        // Procura por attributeName="valor_aqui"
        val regex = """${Regex.escape(attributeName)}="([^"]*)"""".toRegex()
        val matchResult = regex.find(extInfLine)
        return matchResult?.groups?.get(1)?.value?.trim()?.ifEmpty { null }
    }

    /**
     * Faz o parse do conteúdo de um arquivo M3U usando um BufferedReader.
     */
    fun parseStream(reader: BufferedReader): List<M3UItem> {
        val channels = mutableListOf<M3UItem>()

        var currentName: String? = null
        var currentLogoUrl: String? = null
        var currentGroupTitle: String? = null
        var currentExtInfLine: String? = null // Para guardar a linha #EXTINF completa para atributos

        reader.forEachLine { line ->
            val trimmedLine = line.trim()
            when {
                trimmedLine.startsWith("#EXTM3U") -> {
                    // Cabeçalho M3U, pode ser ignorado para extração de canais ou usado para validação
                }
                trimmedLine.startsWith("#EXTINF:") -> {
                    currentExtInfLine = trimmedLine // Salva a linha para extrair atributos

                    // Tenta extrair o nome de 'tvg-name' primeiro
                    var nameFromAttributes = extractAttribute(trimmedLine, "tvg-name")

                    if (nameFromAttributes.isNullOrBlank()) {
                        // Se 'tvg-name' não existir ou estiver vazio, pega o nome após a última vírgula
                        val commaIndex = trimmedLine.lastIndexOf(',')
                        if (commaIndex != -1 && commaIndex < trimmedLine.length - 1) {
                            nameFromAttributes = trimmedLine.substring(commaIndex + 1).trim()
                        }
                    }
                    currentName = if (nameFromAttributes.isNullOrBlank()) "Unnamed Channel" else nameFromAttributes

                    // Extrai logo e group-title da linha #EXTINF salva
                    currentLogoUrl = extractAttribute(trimmedLine, "tvg-logo")
                    currentGroupTitle = extractAttribute(trimmedLine, "group-title")
                }
                trimmedLine.isNotBlank() && !trimmedLine.startsWith("#") -> {
                    // Esta é uma linha de URL
                    val nameForChannel = currentName
                    val urlForChannel = trimmedLine

                    if (nameForChannel != null) {
                        channels.add(
                            M3UItem(
                                name = nameForChannel,
                                logo = currentLogoUrl, // Passa o logo (pode ser null)
                                url = urlForChannel,
                                groupTitle = currentGroupTitle // Passa o group-title (pode ser null)
                            )
                        )
                    }
                    // Reseta as variáveis para o próximo canal
                    currentName = null
                    currentLogoUrl = null
                    currentGroupTitle = null
                    currentExtInfLine = null
                }
            }
        }
        return channels
    }

    /**
     * Faz o parse do conteúdo de uma string M3U.
     */
    fun parse(content: String): List<M3UItem> {
        val reader = StringReader(content).buffered()
        return parseStream(reader)
    }
}
