package com.example.tvplayer.ui.main

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
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
        // O layout activity_main.xml contém o TextView para mostrar a lista atual (nome e URL), EditText de busca, RecyclerView, ProgressBar e FAB
        setContentView(R.layout.activity_main)

        tvCurrentListInfo = findViewById(R.id.tvCurrentListUrl) // Renomeado para info pois exibirá nome e URL
        recyclerView = findViewById(R.id.recyclerViewChannels)
        recyclerView.layoutManager = LinearLayoutManager(this)
        progressBar = findViewById(R.id.progressBarLoading)
        edtSearch = findViewById(R.id.edtSearch)
        fabLoadM3U = findViewById(R.id.fabLoadM3U)

        // Configura o campo de busca para filtrar os canais conforme o usuário digita
        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterChannels(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
        })

        // Configura o FAB para exibir as opções de carregamento
        fabLoadM3U.setOnClickListener {
            showLoadOptionsDialog()
        }

        // Exibe a informação da lista salva (se houver)
        val savedUrl = loadListUrlFromPrefs()
        val savedName = loadListNameFromPrefs()
        if (!savedUrl.isNullOrBlank() && !savedName.isNullOrBlank()) {
            tvCurrentListInfo.text = "Lista: $savedName\nURL: $savedUrl"
        } else {
            tvCurrentListInfo.text = "Nenhuma lista selecionada"
        }
    }

    /**
     * Exibe um diálogo com duas opções: carregar via URL ou selecionar um arquivo local.
     */
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

    /**
     * Exibe um diálogo para que o usuário insira manualmente a URL e o nome da lista.
     */
    private fun showUrlInputDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_input_url, null)
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
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    /**
     * Faz o download do conteúdo M3U a partir da URL fornecida, salva o nome e atualiza a lista.
     */
    private fun fetchM3UFromUrl(urlString: String, listName: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val m3uContent = withContext(Dispatchers.IO) { URL(urlString).readText() }
                onM3UContentReceived(m3uContent)
                // Salva a URL e o nome para futuras execuções
                saveListUrlToPrefs(urlString)
                saveListNameToPrefs(listName)
                tvCurrentListInfo.text = "Lista: $listName\nURL: $urlString"
            } catch (e: Exception) {
                e.printStackTrace()
                progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "Erro ao carregar a lista.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Processa o conteúdo M3U recebido, atualiza a lista de canais e o RecyclerView.
     */
    private fun onM3UContentReceived(m3uContent: String) {
        progressBar.visibility = View.GONE
        channels = M3UParser.parse(m3uContent)
        filteredChannels = channels
        updateRecyclerView(filteredChannels)
    }

    /**
     * Atualiza o RecyclerView com a lista de canais.
     */
    private fun updateRecyclerView(channelsList: List<M3UItem>) {
        adapter = ChannelAdapter(channelsList) { selectedChannel ->
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("channel_url", selectedChannel.url)
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
     * Salva a URL da lista nos SharedPreferences.
     */
    private fun saveListUrlToPrefs(url: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LIST_URL, url).apply()
    }

    /**
     * Carrega a URL da lista dos SharedPreferences.
     */
    private fun loadListUrlFromPrefs(): String? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LIST_URL, null)
    }

    /**
     * Salva o nome da lista nos SharedPreferences.
     */
    private fun saveListNameToPrefs(name: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LIST_NAME, name).apply()
    }

    /**
     * Carrega o nome da lista dos SharedPreferences.
     */
    private fun loadListNameFromPrefs(): String? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LIST_NAME, null)
    }
}
