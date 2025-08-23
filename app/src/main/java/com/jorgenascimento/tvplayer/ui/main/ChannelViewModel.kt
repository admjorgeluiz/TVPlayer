package com.jorgenascimento.tvplayer.ui.main

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.jorgenascimento.tvplayer.R
import com.jorgenascimento.tvplayer.data.model.M3UItem
import com.jorgenascimento.tvplayer.data.parser.M3UParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

// Estados para a UI
sealed class ListLoadingState {
    object Loading : ListLoadingState()
    data class Success(val channels: List<M3UItem>, val listName: String, val sourceIdentifier: String) : ListLoadingState()
    data class Error(val message: String) : ListLoadingState()
    object Idle : ListLoadingState() // Estado inicial ou após erro
}

class ChannelViewModel(application: Application) : AndroidViewModel(application) {

    private val _channelListState = MutableLiveData<ListLoadingState>(ListLoadingState.Idle)
    val channelListState: LiveData<ListLoadingState> = _channelListState

    // Mantém a lista completa aqui
    var fullChannelList: List<M3UItem> = emptyList()
        private set // Apenas o ViewModel pode modificar diretamente

    companion object {
        private const val TAG = "ChannelViewModel"
        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 15_000
    }

    fun loadM3UFromSourceIfNotLoaded(sourceUrlOrUri: String, listName: String, context: Context) {
        if (fullChannelList.isNotEmpty() && _channelListState.value is ListLoadingState.Success) {
            Log.d(TAG, "loadM3UFromSourceIfNotLoaded: Lista já carregada, mas permitindo recarga.")
        }

        _channelListState.value = ListLoadingState.Loading
        viewModelScope.launch {
            try {
                val parsedList: List<M3UItem> = withContext(Dispatchers.IO) {
                    openStreamReader(sourceUrlOrUri, context).use { reader ->
                        M3UParser.parseStream(reader)
                    }
                }
                fullChannelList = parsedList // Armazena a lista completa no ViewModel
                _channelListState.value = ListLoadingState.Success(parsedList, listName, sourceUrlOrUri)
                Log.d(TAG, "Lista M3U carregada com sucesso: ${parsedList.size} canais.")

            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "Timeout ao carregar M3U de: $sourceUrlOrUri", e)
                _channelListState.value = ListLoadingState.Error(context.getString(R.string.toast_timeout_baixar_lista))
            } catch (e: IOException) {
                Log.e(TAG, "IOException ao carregar M3U de: $sourceUrlOrUri", e)
                _channelListState.value = ListLoadingState.Error(context.getString(R.string.toast_erro_generico_carregar_lista))
            } catch (e: Exception) {
                Log.e(TAG, "Erro geral ao carregar M3U de: $sourceUrlOrUri", e)
                _channelListState.value = ListLoadingState.Error(context.getString(R.string.toast_erro_carregar_lista, e.localizedMessage ?: "Desconhecido"))
            }
        }
    }

    @SuppressLint("UseKtx")
    @Throws(IOException::class, SocketTimeoutException::class)
    private fun openStreamReader(source: String, context: Context): BufferedReader {
        return if (source.startsWith("content:")) {
            val uri = Uri.parse(source)
            context.contentResolver.openInputStream(uri)?.bufferedReader()
                ?: throw IOException(context.getString(R.string.toast_erro_abrir_uri_local, uri.toString()))
        } else {
            val url = URL(source)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader()
            } else {
                val errorStreamMessage = connection.errorStream?.bufferedReader()?.readText() ?: "Sem detalhes adicionais."
                connection.disconnect()
                throw IOException("Erro de conexão HTTP: $responseCode. $errorStreamMessage")
            }
        }
    }
}
