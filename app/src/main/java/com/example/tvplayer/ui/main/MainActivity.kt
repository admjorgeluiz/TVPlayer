package com.example.tvplayer.ui.main

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvplayer.R
import com.example.tvplayer.data.model.M3UItem
import com.example.tvplayer.data.parser.M3UParser
import com.example.tvplayer.ui.player.PlayerActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: View
    private lateinit var edtSearch: EditText
    private lateinit var tvCurrentListInfo: TextView
    private lateinit var fabLoadM3U: FloatingActionButton
    private lateinit var adapter: ChannelAdapter

    // Lista completa e filtrada de canais
    private var channels: List<M3UItem> = emptyList()
    private var filteredChannels: List<M3UItem> = emptyList()

    // SharedPreferences keys para a URL e o nome da lista
    private val PREFS_NAME = "tvplayer_prefs"
    private val KEY_LIST_URL = "list_url"
    private val KEY_LIST_NAME = "list_name"

    private val gson = Gson()

    // Seletor de arquivo para carregar um arquivo M3U (opção local)
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                contentResolver.openInputStream(it)?.bufferedReader().use { reader ->
                    val fileContent = reader?.readText() ?: ""
                    onM3UContentReceived(fileContent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Erro ao ler o arquivo.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvCurrentListInfo = findViewById(R.id.tvCurrentListUrl)
        recyclerView = findViewById(R.id.recyclerViewChannels)
        recyclerView.layoutManager = LinearLayoutManager(this)
        progressBar = findViewById(R.id.progressBarLoading)
        edtSearch = findViewById(R.id.edtSearch)
        fabLoadM3U = findViewById(R.id.fabLoadM3U)

        edtSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                filterChannels(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        fabLoadM3U.setOnClickListener { showLoadOptionsDialog() }

        // Exibe info salva
        val savedUrl = loadListUrlFromPrefs()
        val savedName = loadListNameFromPrefs()
        tvCurrentListInfo.text = if (!savedUrl.isNullOrBlank() && !savedName.isNullOrBlank())
            "Lista: $savedName\nURL: $savedUrl" else "Nenhuma lista selecionada"
    }

    private fun showLoadOptionsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Carregar Lista M3U")
            .setMessage("Escolha uma opção para carregar a lista:")
            .setPositiveButton("Via URL") { dialog, _ ->
                dialog.dismiss(); showUrlInputDialog()
            }
            .setNegativeButton("Arquivo Local") { dialog, _ ->
                dialog.dismiss(); filePickerLauncher.launch("text/*")
            }
            .create().show()
    }

    private fun showUrlInputDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_input_url, null)
        val edtListName = dialogView.findViewById<EditText>(R.id.edtListName)
        val edtUrl = dialogView.findViewById<EditText>(R.id.edtUrlM3U)
        AlertDialog.Builder(this)
            .setTitle("Carregar Lista M3U")
            .setView(dialogView)
            .setPositiveButton("Carregar") { dialog, _ ->
                val url = edtUrl.text.toString().trim()
                val listName = edtListName.text.toString().trim()
                if (url.isNotEmpty() && listName.isNotEmpty()) {
                    fetchM3UFromUrl(url, listName)
                } else {
                    Toast.makeText(this, "Insira um nome e uma URL válidos.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
            .create().show()
    }

    private fun fetchM3UFromUrl(urlString: String, listName: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                // Leitura em streaming para evitar OOM
                val items = withContext(Dispatchers.IO) {
                    val url = URL(urlString)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    conn.requestMethod = "GET"
                    conn.doInput = true
                    conn.connect()
                    conn.inputStream.buffered().reader().use { reader ->
                        M3UParser.parseStream(reader as BufferedReader)
                    }.also {
                        conn.disconnect()
                    }
                }
                // Atualiza UI
                channels = items
                filteredChannels = items
                saveListUrlToPrefs(urlString)
                saveListNameToPrefs(listName)
                tvCurrentListInfo.text = "Lista: $listName\nURL: $urlString"
                updateRecyclerView(filteredChannels)

            } catch (e: SocketTimeoutException) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Tempo de conexão esgotado.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Erro ao carregar a lista.", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun onM3UContentReceived(m3uContent: String) {
        progressBar.visibility = View.GONE
        channels = M3UParser.parse(m3uContent)
        filteredChannels = channels
        updateRecyclerView(filteredChannels)
    }

    private fun updateRecyclerView(channelsList: List<M3UItem>) {
        adapter = ChannelAdapter(channelsList) { selectedChannel ->
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("channel_url", selectedChannel.url)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
    }

    private fun filterChannels(query: String) {
        val lowerQuery = query.lowercase()
        filteredChannels = channels.filter { it.name.lowercase().contains(lowerQuery) }
        adapter.updateData(filteredChannels)
    }

    private fun saveListUrlToPrefs(url: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIST_URL, url).apply()
    }

    private fun loadListUrlFromPrefs(): String? =
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LIST_URL, null)

    private fun saveListNameToPrefs(name: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIST_NAME, name).apply()
    }

    private fun loadListNameFromPrefs(): String? =
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LIST_NAME, null)
}
