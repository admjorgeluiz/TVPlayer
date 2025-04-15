package com.example.tvplayer.ui.list

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvplayer.R
import com.example.tvplayer.data.model.M3UItem
import com.example.tvplayer.data.parser.M3UParser
import com.example.tvplayer.ui.main.ChannelAdapter
import com.example.tvplayer.ui.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ChannelListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var edtSearch: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ChannelAdapter
    private var channels: List<M3UItem> = emptyList()
    private var filteredChannels: List<M3UItem> = emptyList()

    // SharedPreferences key para a URL da lista (mesmo nome usado na tela de configuração)
    private val PREFS_NAME = "tvplayer_prefs"
    private val KEY_LIST_URL = "list_url"

    // Tag para logging
    private val TAG = "ChannelListActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel_list)

        recyclerView = findViewById(R.id.recyclerViewChannels)
        recyclerView.layoutManager = LinearLayoutManager(this)
        edtSearch = findViewById(R.id.edtSearch)
        progressBar = findViewById(R.id.progressBarLoading)

        // Configura o campo de busca para filtrar os canais conforme o usuário digita
        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterChannels(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Carrega a URL salva dos SharedPreferences
        val savedUrl = loadListUrlFromPrefs()
        Log.d(TAG, "URL carregada dos prefs: $savedUrl")
        if (!savedUrl.isNullOrBlank()) {
            fetchM3UFromSource(savedUrl)
        } else {
            Toast.makeText(
                this,
                "Nenhuma URL de lista encontrada. Configure a lista na tela de configuração.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    /**
     * Faz o download ou leitura do conteúdo M3U a partir da fonte informada.
     *
     * Se a string (após trim) iniciar com "content:", a fonte é tratada como URI local
     * usando o ContentResolver; caso contrário, é tratada como URL remota.
     */
    private fun fetchM3UFromSource(source: String) {
        progressBar.visibility = ProgressBar.VISIBLE
        lifecycleScope.launch {
            try {
                val src = source.trim() // Remove espaços em branco
                Log.d(TAG, "Processando fonte: $src")
                val m3uContent = withContext(Dispatchers.IO) {
                    if (src.startsWith("content:")) {
                        // Trata URI local (ex.: selecionada via SAF)
                        val uri = Uri.parse(src)
                        Log.d(TAG, "Fonte é uma URI local: $uri")
                        contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                            ?: throw Exception("Não foi possível ler o arquivo selecionado pelo SAF.")
                    } else {
                        // Trata como URL remota
                        Log.d(TAG, "Fonte é uma URL remota.")
                        val url = URL(src)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        try {
                            connection.inputStream.bufferedReader().readText()
                        } finally {
                            connection.disconnect()
                        }
                    }
                }
                onM3UContentReceived(m3uContent)
            } catch (e: Exception) {
                e.printStackTrace()
                progressBar.visibility = ProgressBar.GONE
                runOnUiThread {
                    Toast.makeText(
                        this@ChannelListActivity,
                        "Erro ao carregar a lista.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                Log.e(TAG, "Erro em fetchM3UFromSource", e)
            }
        }
    }

    /**
     * Processa o conteúdo M3U recebido, atualiza a lista interna e o RecyclerView.
     */
    private fun onM3UContentReceived(m3uContent: String) {
        progressBar.visibility = ProgressBar.GONE
        channels = M3UParser.parse(m3uContent)
        filteredChannels = channels
        updateRecyclerView(filteredChannels)
    }

    /**
     * Atualiza o RecyclerView com a lista de canais e define a ação de clique para iniciar o PlayerActivity.
     */
    private fun updateRecyclerView(channelsList: List<M3UItem>) {
        adapter = ChannelAdapter(channelsList) { selectedChannel ->
            // Ao clicar em um canal, inicia a PlayerActivity passando a URL do canal
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("channel_url", selectedChannel.url)
            }
            startActivity(intent)
        }
        recyclerView.adapter = adapter
    }

    /**
     * Filtra os canais de acordo com o termo de busca e atualiza o adapter.
     */
    private fun filterChannels(query: String) {
        val lowerQuery = query.lowercase()
        filteredChannels = channels.filter { it.name.lowercase().contains(lowerQuery) }
        adapter.updateData(filteredChannels)
    }

    /**
     * Carrega a URL da lista dos SharedPreferences.
     */
    private fun loadListUrlFromPrefs(): String? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LIST_URL, null)
    }
}
