package com.example.tvplayer.ui.main

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvplayer.R
import com.example.tvplayer.data.model.M3UItem
import com.example.tvplayer.data.parser.M3UParser
import com.example.tvplayer.ui.player.PlayerActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
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

class MainActivity : AppCompatActivity() {

    private lateinit var tvCurrentListInfo: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var edtSearch: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var fabLoadM3U: FloatingActionButton
    private lateinit var adapter: ChannelAdapter
    private lateinit var adView: AdView

    private var channels: List<M3UItem> = emptyList()

    companion object {
        private const val PREFS_NAME      = "tvplayer_prefs"
        private const val KEY_LIST_URL    = "list_url"
        private const val KEY_LIST_NAME   = "list_name"
        private const val TAG             = "MainActivity"
        private const val CONNECT_TIMEOUT = 10_000
        private const val READ_TIMEOUT    = 10_000
    }

    // File picker para M3U local
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                contentResolver.openInputStream(it)?.bufferedReader().use { reader ->
                    val content = reader?.readText() ?: ""
                    onM3UContentReceived(content, uri.toString())
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Erro ao ler o arquivo.", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Erro ao ler M3U local", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializa o SDK de Ads
        MobileAds.initialize(this)

        setContentView(R.layout.activity_main)

        // Banner no rodapé
        adView = findViewById(R.id.adViewMain)
        adView.loadAd(AdRequest.Builder().build())

        tvCurrentListInfo = findViewById(R.id.tvCurrentListUrl)
        recyclerView = findViewById<RecyclerView>(R.id.recyclerViewChannels)
        recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)

        edtSearch         = findViewById(R.id.edtSearch)
        progressBar       = findViewById(R.id.progressBarLoading)
        fabLoadM3U        = findViewById(R.id.fabLoadM3U)

        // Busca dinâmica
        edtSearch.addTextChangedListener(afterTextChanged = {
            val q = it.toString().trim().lowercase()
            adapter.updateData(channels.filter { c -> c.name.lowercase().contains(q) })
        })

        // FAB: opções de carregar
        fabLoadM3U.setOnClickListener { showLoadOptionsDialog() }

        // Exibe lista salva, se existir
        val prefs    = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(KEY_LIST_URL, null)
        val name     = prefs.getString(KEY_LIST_NAME, null)
        if (!savedUrl.isNullOrBlank() && !name.isNullOrBlank()) {
            tvCurrentListInfo.text = "Lista: $name\nURL: $savedUrl"
            fetchM3UFromUrl(savedUrl, name)
        } else {
            tvCurrentListInfo.text = "Nenhuma lista selecionada"
        }
    }

    private fun showLoadOptionsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Carregar Lista M3U")
            .setMessage("Escolha uma opção para carregar a lista:")
            .setPositiveButton("Via URL") { dialog, _ ->
                dialog.dismiss()
                showUrlInputDialog()
            }
            .setNegativeButton("Arquivo Local") { dialog, _ ->
                dialog.dismiss()
                filePickerLauncher.launch("text/*")
            }
            .create()
            .show()
    }

    private fun showUrlInputDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input_url, null)
        val edtName = view.findViewById<EditText>(R.id.edtListName)
        val edtUrl  = view.findViewById<EditText>(R.id.edtUrlM3U)
        AlertDialog.Builder(this)
            .setTitle("Carregar Lista M3U")
            .setView(view)
            .setPositiveButton("Carregar") { dialog, _ ->
                val url  = edtUrl.text.toString().trim()
                val name = edtName.text.toString().trim()
                if (url.isNotEmpty() && name.isNotEmpty()) {
                    fetchM3UFromUrl(url, name)
                } else {
                    Toast.makeText(this, "Insira nome e URL válidos.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun fetchM3UFromUrl(urlString: String, listName: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    URL(urlString).openConnection().run {
                        connectTimeout = CONNECT_TIMEOUT
                        readTimeout    = READ_TIMEOUT
                        inputStream.bufferedReader().readText()
                    }
                }
                onM3UContentReceived(content, urlString)
                // salva prefs
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LIST_URL, urlString)
                    .putString(KEY_LIST_NAME, listName)
                    .apply()
                tvCurrentListInfo.text = "Lista: $listName\nURL: $urlString"

            } catch (e: SocketTimeoutException) {
                Toast.makeText(
                    this@MainActivity,
                    "Timeout ao baixar lista.",
                    Toast.LENGTH_LONG
                ).show()
                Log.e(TAG, "Timeout", e)
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Erro ao carregar lista: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                Log.e(TAG, "Erro geral", e)
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun onM3UContentReceived(m3uContent: String, sourceId: String) {
        progressBar.visibility = View.GONE
        channels = M3UParser.parseStream(m3uContent.reader().buffered())
        adapter = ChannelAdapter(channels) { item ->
            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .putExtra("channel_url", item.url)
            )
        }
        recyclerView.adapter = adapter
    }
}
