package com.example.tvplayer.ui.main

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: android.view.View // Pode ser ProgressBar
    private lateinit var adapter: ChannelAdapter
    private var channels: List<M3UItem> = emptyList()

    // Registrar o seletor para arquivo local
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                contentResolver.openInputStream(it)?.bufferedReader().use { reader ->
                    val fileContent = reader?.readText() ?: ""
                    loadChannels(fileContent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Erro ao ler o arquivo.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // O layout activity_main.xml contém o RecyclerView, ProgressBar e o FAB
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerViewChannels)
        recyclerView.layoutManager = LinearLayoutManager(this)

        progressBar = findViewById(R.id.progressBarLoading)

        // Opcional: Carregar inicialmente uma lista teste – comente se não desejar.
        // loadChannels(loadM3UContentHardcoded())

        // Configura o FAB para mostrar opções de carregamento
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabLoadM3U)
            .setOnClickListener {
                showLoadOptionsDialog()
            }
    }

    /**
     * Atualiza o RecyclerView com a lista de canais a partir do conteúdo M3U.
     */
    private fun loadChannels(m3uContent: String) {
        // Oculta o indicador de carregamento, pois o conteúdo já foi baixado
        progressBar.visibility = android.view.View.GONE

        channels = M3UParser.parse(m3uContent)
        adapter = ChannelAdapter(channels) { selectedChannel ->
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("channel_url", selectedChannel.url)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        // Log para verificação
        channels.forEach {
            Log.d("MainActivity", "Channel: ${it.name}, URL: ${it.url}")
        }
    }

    /**
     * Exibe um diálogo com duas opções: via URL ou arquivo local.
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
     * Exibe um diálogo para o usuário inserir a URL da lista M3U.
     */
    private fun showUrlInputDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_input_url, null)
        val edtUrl = dialogView.findViewById<EditText>(R.id.edtUrlM3U)
        AlertDialog.Builder(this)
            .setTitle("Carregar Lista M3U")
            .setView(dialogView)
            .setPositiveButton("Carregar") { dialog, _ ->
                val url = edtUrl.text.toString().trim()
                if (url.isNotEmpty()) {
                    fetchM3UFromUrl(url)
                } else {
                    Toast.makeText(this, "Insira uma URL válida.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    /**
     * Faz o download do conteúdo M3U a partir da URL e atualiza a lista.
     */
    private fun fetchM3UFromUrl(urlString: String) {
        // Exibe o indicador de carregamento antes de iniciar a operação
        progressBar.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            try {
                val m3uContent = withContext(Dispatchers.IO) {
                    URL(urlString).readText()
                }
                loadChannels(m3uContent)
            } catch (e: Exception) {
                e.printStackTrace()
                progressBar.visibility = android.view.View.GONE
                Toast.makeText(this@MainActivity, "Erro ao carregar a lista.", Toast.LENGTH_SHORT).show()
            }
        }
    }

}
