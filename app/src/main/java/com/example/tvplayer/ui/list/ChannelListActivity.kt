package com.example.tvplayer.ui.list

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvplayer.R
import com.example.tvplayer.data.model.M3UItem
import com.example.tvplayer.data.parser.M3UParser
import com.example.tvplayer.ui.main.ChannelAdapter
import com.example.tvplayer.ui.player.PlayerActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
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

    private lateinit var recyclerView: RecyclerView
    private lateinit var edtSearch: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ChannelAdapter
    private lateinit var adView: AdView    // Banner AdView declaration

    private var channels: List<M3UItem> = emptyList()

    companion object {
        private const val PREFS_NAME      = "tvplayer_prefs"
        private const val KEY_LIST_URL    = "list_url"
        private const val TAG             = "ChannelListActivity"
        private const val CONNECT_TIMEOUT = 10_000
        private const val READ_TIMEOUT    = 10_000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializa o SDK do AdMob
        MobileAds.initialize(this)

        setContentView(R.layout.activity_channel_list)

        // Busca o AdView e carrega o anúncio
        adView = findViewById(R.id.adViewChannelList)
        adView.loadAd(AdRequest.Builder().build())

        // Configura a Toolbar e seta o "Up" (seta de voltar)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Inicializa RecyclerView e demais views
        recyclerView = findViewById<RecyclerView>(R.id.recyclerViewChannels).apply {
            layoutManager = LinearLayoutManager(this@ChannelListActivity)
        }
        edtSearch   = findViewById(R.id.edtSearch)
        progressBar = findViewById(R.id.progressBarLoading)

        // Filtra em tempo real ao digitar
        edtSearch.addTextChangedListener(afterTextChanged = { editable ->
            val q = editable?.toString()?.trim()?.lowercase() ?: ""
            adapter.updateData(channels.filter { it.name.lowercase().contains(q) })
        })

        // Lê URL salva dos SharedPreferences
        val savedUrl = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LIST_URL, null)

        if (!savedUrl.isNullOrBlank()) {
            loadFromSource(savedUrl)
        } else {
            Toast.makeText(this, "Nenhuma URL configurada.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // Captura clique na seta "Up" da Toolbar
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadFromSource(source: String) {
        progressBar.visibility = ProgressBar.VISIBLE

        lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    openStreamReader(source).use { reader ->
                        M3UParser.parseStream(reader)
                    }
                }

                // Atualiza UI com os canais
                progressBar.visibility = ProgressBar.GONE
                channels = list
                adapter = ChannelAdapter(channels) { item ->
                    startActivity(
                        Intent(this@ChannelListActivity, PlayerActivity::class.java)
                            .putExtra("channel_url", item.url)
                    )
                }
                recyclerView.adapter = adapter

            } catch (toe: SocketTimeoutException) {
                progressBar.visibility = ProgressBar.GONE
                Toast.makeText(
                    this@ChannelListActivity,
                    "Timeout ao baixar lista. Verifique a conexão/URL.",
                    Toast.LENGTH_LONG
                ).show()
                Log.e(TAG, "Timeout ao baixar lista", toe)

            } catch (e: Exception) {
                progressBar.visibility = ProgressBar.GONE
                Toast.makeText(
                    this@ChannelListActivity,
                    "Erro ao carregar lista: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                Log.e(TAG, "Erro ao carregar lista", e)
            }
        }
    }

    @Throws(IOException::class)
    private fun openStreamReader(source: String): BufferedReader {
        // Se for URI local (SAF)
        if (source.startsWith("content:")) {
            val uri = Uri.parse(source)
            return contentResolver
                .openInputStream(uri)
                ?.bufferedReader()
                ?: throw IOException("Não foi possível abrir URI local.")
        }

        // Função auxiliar para conexão HTTP/HTTPS
        fun connect(urlString: String): BufferedReader {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT
                readTimeout    = READ_TIMEOUT
            }
            return conn.inputStream.bufferedReader()
        }

        return try {
            // Tentativa HTTPS ou HTTP conforme URL
            connect(source)
        } catch (ioe: IOException) {
            // Se falha de TLS em HTTPS, tenta rebaixar para HTTP
            if (ioe.message
                    ?.contains("Unable to parse TLS packet header", ignoreCase = true) == true
                && source.startsWith("https://")
            ) {
                Log.w(TAG, "Falha no handshake TLS, tentando HTTP em vez de HTTPS")
                connect(source.replaceFirst("https://", "http://"))
            } else {
                throw ioe
            }
        }
    }

    override fun onDestroy() {
        adView.destroy()   // Importante destruir o AdView
        super.onDestroy()
    }
}