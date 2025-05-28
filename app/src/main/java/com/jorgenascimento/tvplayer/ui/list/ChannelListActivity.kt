package com.jorgenascimento.tvplayer.ui.list

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jorgenascimento.tvplayer.R
import com.jorgenascimento.tvplayer.data.model.M3UItem
import com.jorgenascimento.tvplayer.data.parser.M3UParser
import com.jorgenascimento.tvplayer.databinding.ActivityChannelListBinding // SR_CORRECTION: Import do ViewBinding
import com.jorgenascimento.tvplayer.ui.main.ChannelAdapter // SR_NOTE: Adapter está no pacote .ui.main
import com.jorgenascimento.tvplayer.ui.player.PlayerActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class ChannelListActivity : AppCompatActivity() {

    // SR_CORRECTION: Usando ViewBinding
    private lateinit var binding: ActivityChannelListBinding
    private lateinit var channelAdapter: ChannelAdapter // SR_CORRECTION: Renomeado de 'adapter'

    // SR_CORRECTION: Lista original para busca, o ListAdapter gerencia a lista exibida
    private var fullChannelList: List<M3UItem> = emptyList()

    companion object {
        private const val PREFS_NAME = "tvplayer_prefs"
        private const val KEY_LIST_URL = "list_url" // SR_NOTE: Esta Activity lê apenas a URL. O nome da lista não é usado aqui.
        private const val TAG = "ChannelListActivity"
        private const val CONNECT_TIMEOUT = 15_000 // SR_SUGGESTION: Aumentado
        private const val READ_TIMEOUT = 15_000    // SR_SUGGESTION: Aumentado
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // SR_CORRECTION: Inflate usando ViewBinding
        binding = ActivityChannelListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MobileAds.initialize(this) {}
        binding.adViewChannelList.loadAd(AdRequest.Builder().build())

        setupToolbar()
        setupRecyclerView()
        setupSearch()

        loadChannels()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        // SR_SUGGESTION: O título da toolbar pode ser definido via XML ou aqui
        // supportActionBar?.title = getString(R.string.channel_list_title) // Se tiver uma string para isso
    }

    private fun setupRecyclerView() {
        // SR_CORRECTION: Instanciação do ChannelAdapter (agora ListAdapter)
        channelAdapter = ChannelAdapter { m3uItem ->
            // O clique no item está correto, item.url deve ser acessível
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("channel_url", m3uItem.url)
            }
            startActivity(intent)
        }
        binding.recyclerViewChannels.apply {
            layoutManager = LinearLayoutManager(this@ChannelListActivity)
            adapter = channelAdapter // Atribui o adapter aqui
        }
    }

    private fun setupSearch() {
        binding.edtSearch.addTextChangedListener { editable ->
            val query = editable?.toString()?.trim()?.lowercase() ?: ""
            if (query.isEmpty()) {
                channelAdapter.submitList(fullChannelList)
            } else {
                val filteredList = fullChannelList.filter {
                    it.name.lowercase().contains(query) ||
                            (it.groupTitle?.lowercase()?.contains(query) == true)
                }
                channelAdapter.submitList(filteredList)
            }
        }
    }

    private fun loadChannels() {
        val savedUrl = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LIST_URL, null)

        if (!savedUrl.isNullOrBlank()) {
            loadFromSource(savedUrl)
        } else {
            Toast.makeText(this, getString(R.string.nenhuma_lista_selecionada), Toast.LENGTH_LONG).show()
            // SR_SUGGESTION: Limpar a lista no adapter se não houver URL
            channelAdapter.submitList(emptyList())
            fullChannelList = emptyList()
            finish() // Continua finalizando se não houver URL
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadFromSource(sourceUrlOrUri: String) {
        binding.progressBarLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val parsedList = withContext(Dispatchers.IO) {
                    openStreamReader(sourceUrlOrUri).use { reader ->
                        M3UParser.parseStream(reader)
                    }
                }
                fullChannelList = parsedList // Atualiza a lista completa
                channelAdapter.submitList(fullChannelList) // Submete a lista ao adapter

                if (fullChannelList.isEmpty()) {
                    Toast.makeText(this@ChannelListActivity, "Lista carregada, mas está vazia ou formato inválido.", Toast.LENGTH_LONG).show()
                }

            } catch (toe: SocketTimeoutException) {
                Log.e(TAG, "Timeout ao baixar lista de: $sourceUrlOrUri", toe)
                Toast.makeText(this@ChannelListActivity, getString(R.string.toast_timeout_baixar_lista), Toast.LENGTH_LONG).show()
                channelAdapter.submitList(emptyList()) // Limpa em caso de erro
                fullChannelList = emptyList()
            } catch (e: IOException) {
                Log.e(TAG, "IOException ao carregar lista de: $sourceUrlOrUri", e)
                Toast.makeText(this@ChannelListActivity, getString(R.string.toast_erro_generico_carregar_lista), Toast.LENGTH_LONG).show()
                channelAdapter.submitList(emptyList())
                fullChannelList = emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Erro geral ao carregar lista de: $sourceUrlOrUri", e)
                Toast.makeText(this@ChannelListActivity, getString(R.string.toast_erro_carregar_lista, e.localizedMessage ?: "Desconhecido"), Toast.LENGTH_LONG).show()
                channelAdapter.submitList(emptyList())
                fullChannelList = emptyList()
            } finally {
                binding.progressBarLoading.visibility = View.GONE
            }
        }
    }

    @Throws(IOException::class, SocketTimeoutException::class)
    private fun openStreamReader(source: String): BufferedReader {
        if (source.startsWith("content:")) {
            val uri = Uri.parse(source)
            return contentResolver.openInputStream(uri)?.bufferedReader()
                ?: throw IOException(getString(R.string.toast_erro_abrir_uri_local, uri.toString())) // Usando string resource
        }

        // Lógica de conexão HTTP/HTTPS (similar à da MainActivity)
        val url = URL(source)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = READ_TIMEOUT
        connection.instanceFollowRedirects = true

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            return connection.inputStream.bufferedReader()
        } else {
            val errorStreamMessage = connection.errorStream?.bufferedReader()?.readText() ?: "Sem detalhes adicionais."
            connection.disconnect()
            throw IOException("Erro de conexão HTTP: $responseCode. $errorStreamMessage")
        }
        // SR_NOTE: A lógica de fallback HTTP->HTTPS foi removida para consistência com a MainActivity revisada.
        // Se for estritamente necessário, pode ser readicionada aqui, mas geralmente é melhor
        // que os servidores HTTPS funcionem corretamente.
    }

    override fun onDestroy() {
        binding.adViewChannelList.destroy() // SR_CORRECTION: Usar binding
        super.onDestroy()
    }
}
