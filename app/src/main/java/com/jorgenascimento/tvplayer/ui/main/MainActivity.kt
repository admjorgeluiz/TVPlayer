package com.jorgenascimento.tvplayer.ui.main

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jorgenascimento.tvplayer.R
import com.jorgenascimento.tvplayer.data.model.M3UItem
import com.jorgenascimento.tvplayer.data.parser.M3UParser
import com.jorgenascimento.tvplayer.databinding.ActivityMainBinding
import com.jorgenascimento.tvplayer.databinding.DialogInputUrlBinding
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var channelAdapter: ChannelAdapter

    private var fullChannelList: List<M3UItem> = emptyList()

    companion object {
        private const val PREFS_NAME = "tvplayer_prefs"
        private const val KEY_LIST_URL = "list_url"
        private const val KEY_LIST_NAME = "list_name"
        private const val TAG = "MainActivity"
        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 15_000
    }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "Falha ao persistir permissão para URI: $selectedUri", e)
            }
            val fileName = getFileNameFromUri(selectedUri) ?: getString(R.string.dialog_option_arquivo_local)
            loadM3UFromSource(selectedUri.toString(), fileName)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MobileAds.initialize(this) {}
        binding.adViewMain.loadAd(AdRequest.Builder().build())

        setupRecyclerView()
        setupSearch()
        setupFab()

        loadSavedList()
    }

    private fun setupRecyclerView() {
        channelAdapter = ChannelAdapter { m3uItem ->
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("channel_url", m3uItem.url)
            }
            startActivity(intent)
        }
        binding.recyclerViewChannels.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = channelAdapter
            itemAnimator = null
        }
    }

    private fun setupSearch() {
        binding.edtSearch.addTextChangedListener { editable ->
            val query = editable?.toString()?.trim()?.lowercase() ?: ""
            // Log.d(TAG, "--- INÍCIO DA BUSCA ---")
            // Log.d(TAG, "Query de busca digitada: '$query'")
            // Log.d(TAG, "ListAdapter currentList size ANTES de submit: ${channelAdapter.currentList.size}")

            if (query.isEmpty()) {
                channelAdapter.submitList(fullChannelList)
            } else {
                val filteredList = fullChannelList.filter { item ->
                    val itemNameLower = item.name.lowercase()
                    val groupTitleLower = item.groupTitle?.lowercase()
                    val nameMatches = itemNameLower.contains(query)
                    val groupMatches = groupTitleLower?.contains(query) == true
                    nameMatches || groupMatches
                }
                channelAdapter.submitList(null) // Força limpeza
                channelAdapter.submitList(filteredList) // Submete a nova lista filtrada
            }

            binding.recyclerViewChannels.post {
                // Log.d(TAG, "ListAdapter currentList size (dentro do post): ${channelAdapter.currentList.size}")
                if (channelAdapter.currentList.isNotEmpty()) {
                    binding.recyclerViewChannels.scrollToPosition(0)
                }
            }
            // Log.d(TAG, "--- FIM DA BUSCA ---")
        }
    }

    private fun setupFab() {
        binding.fabLoadM3U.setOnClickListener { showLoadOptionsDialog() }
    }

    private fun loadSavedList() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(KEY_LIST_URL, null)
        val savedName = prefs.getString(KEY_LIST_NAME, null)

        if (!savedUrl.isNullOrBlank() && !savedName.isNullOrBlank()) {
            updateListInfoUI(savedName, savedUrl)
            loadM3UFromSource(savedUrl, savedName)
        } else {
            binding.tvCurrentListInfo.text = getString(R.string.nenhuma_lista_selecionada)
            channelAdapter.submitList(emptyList())
            fullChannelList = emptyList()
        }
    }

    private fun showLoadOptionsDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_title_carregar_lista))
            .setItems(
                arrayOf(
                    getString(R.string.dialog_option_via_url),
                    getString(R.string.dialog_option_arquivo_local)
                )
            ) { dialog, which ->
                when (which) {
                    0 -> showUrlInputDialog()
                    1 -> filePickerLauncher.launch("*/*")
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_button_cancelar), null)
            .show()
    }

    private fun showUrlInputDialog() {
        val dialogBinding = DialogInputUrlBinding.inflate(LayoutInflater.from(this))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_title_carregar_lista))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.dialog_button_carregar)) { dialog, _ ->
                val url = dialogBinding.edtUrlM3U.text.toString().trim()
                val name = dialogBinding.edtListName.text.toString().trim()
                if (url.isNotEmpty() && name.isNotEmpty()) {
                    loadM3UFromSource(url, name)
                } else {
                    Toast.makeText(this, getString(R.string.toast_insira_nome_url_validos), Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_button_cancelar), null)
            .show()
    }

    private fun loadM3UFromSource(sourceUrlOrUri: String, listName: String) {
        binding.progressBarLoading.visibility = View.VISIBLE
        channelAdapter.submitList(null) // Limpa a lista atual enquanto carrega uma nova
        fullChannelList = emptyList()   // Limpa a lista de referência também

        lifecycleScope.launch {
            try {
                // SR_CORRECTION: Fazer o parse diretamente do stream sem ler para uma String gigante
                val parsedList: List<M3UItem> = withContext(Dispatchers.IO) {
                    openStreamReader(sourceUrlOrUri).use { reader ->
                        M3UParser.parseStream(reader) // Parse direto do BufferedReader
                    }
                }
                // SR_CORRECTION: Chamar uma função para processar a lista de M3UItems parseada
                processParsedM3UList(parsedList, listName, sourceUrlOrUri)
                saveListConfig(listName, sourceUrlOrUri)

            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "Timeout ao carregar M3U de: $sourceUrlOrUri", e)
                withContext(Dispatchers.Main) { // Garante que a UI é atualizada na thread principal
                    Toast.makeText(this@MainActivity, getString(R.string.toast_timeout_baixar_lista), Toast.LENGTH_LONG).show()
                    handleLoadError()
                }
            } catch (e: IOException) {
                Log.e(TAG, "IOException ao carregar M3U de: $sourceUrlOrUri", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_erro_generico_carregar_lista), Toast.LENGTH_LONG).show()
                    handleLoadError()
                }
            } catch (e: Exception) { // Incluindo OutOfMemoryError se o parser ainda consumir muito, mas improvável aqui
                Log.e(TAG, "Erro geral ao carregar M3U de: $sourceUrlOrUri", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_erro_carregar_lista, e.localizedMessage ?: "Desconhecido"), Toast.LENGTH_LONG).show()
                    handleLoadError()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.progressBarLoading.visibility = View.GONE
                }
            }
        }
    }

    @Throws(IOException::class, SocketTimeoutException::class)
    private fun openStreamReader(source: String): BufferedReader {
        return if (source.startsWith("content:")) {
            val uri = Uri.parse(source)
            contentResolver.openInputStream(uri)?.bufferedReader()
                ?: throw IOException(getString(R.string.toast_erro_abrir_uri_local, uri.toString()))
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

    // SR_CORRECTION: Função renomeada e modificada para aceitar List<M3UItem>
    private fun processParsedM3UList(parsedList: List<M3UItem>, listName: String, sourceIdentifier: String) {
        Log.d(TAG, "Iniciando processParsedM3UList para lista: '$listName'")
        fullChannelList = parsedList // Atualiza a lista de referência
        Log.d(TAG, "Parser retornou ${fullChannelList.size} canais.")
        if (fullChannelList.isNotEmpty()) {
            Log.d(TAG, "Exemplo do primeiro canal parseado: Nome='${fullChannelList.first().name}', Grupo='${fullChannelList.first().groupTitle}', Logo='${fullChannelList.first().logo}'")
        }

        channelAdapter.submitList(fullChannelList) // Submete a lista parseada ao adapter
        updateListInfoUI(listName, sourceIdentifier)

        if (fullChannelList.isEmpty()) {
            Toast.makeText(this, "Lista '$listName' carregada, mas está vazia ou o formato é inválido.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Lista '$listName' carregada com ${fullChannelList.size} canais.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleLoadError() {
        channelAdapter.submitList(emptyList())
        fullChannelList = emptyList()
        binding.tvCurrentListInfo.text = getString(R.string.nenhuma_lista_selecionada)
    }

    private fun saveListConfig(listName: String, listUrlOrUri: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_LIST_NAME, listName)
            putString(KEY_LIST_URL, listUrlOrUri)
            apply()
        }
    }

    private fun updateListInfoUI(listName: String, listUrlOrUri: String) {
        binding.tvCurrentListInfo.text = getString(R.string.info_lista_carregada, listName, listUrlOrUri)
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        fileName = cursor.getString(displayNameIndex)
                    }
                }
            }
        }
        if (fileName == null) {
            fileName = uri.path
            val cut = fileName?.lastIndexOf('/')
            if (cut != -1 && cut != null) {
                fileName = fileName?.substring(cut + 1)
            }
        }
        return fileName?.substringBeforeLast(".")
    }

    override fun onDestroy() {
        binding.adViewMain.destroy()
        super.onDestroy()
    }
}
