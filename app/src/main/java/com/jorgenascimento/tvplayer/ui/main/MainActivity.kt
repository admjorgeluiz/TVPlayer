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
import androidx.activity.viewModels // Import para by viewModels()
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Observer // Import para Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.jorgenascimento.tvplayer.R
import com.jorgenascimento.tvplayer.data.model.M3UItem
import com.jorgenascimento.tvplayer.databinding.ActivityMainBinding
import com.jorgenascimento.tvplayer.databinding.DialogInputUrlBinding
import com.jorgenascimento.tvplayer.ui.player.PlayerActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// Definições de Categoria (podem ficar fora da classe se preferir, ou no ViewModel)
enum class ChannelCategory {
    ALL, CHANNELS, MOVIES, SERIES, OTHERS
}

fun getChannelCategoryFromString(groupTitle: String?): ChannelCategory {
    val titleLower = groupTitle?.trim()?.lowercase() ?: return ChannelCategory.OTHERS
    if (titleLower.startsWith("séries") || titleLower.startsWith("series") || titleLower.startsWith("tv shows")) {
        return ChannelCategory.SERIES
    }
    if (titleLower.startsWith("filmes") || titleLower.startsWith("movies") || titleLower.startsWith("films")) {
        return ChannelCategory.MOVIES
    }
    if (titleLower.startsWith("canais") || titleLower.startsWith("channels") ||
        titleLower.startsWith("tv") || titleLower.startsWith("live") || titleLower.startsWith("ao vivo")) {
        return ChannelCategory.CHANNELS
    }
    return ChannelCategory.OTHERS
}


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var channelAdapter: ChannelAdapter

    // SR_CORRECTION: Obter instância do ViewModel
    private val channelViewModel: ChannelViewModel by viewModels()

    // fullChannelList agora é gerenciada pelo ViewModel
    private var listFilteredByCategory: List<M3UItem> = emptyList()
    private var currentSelectedCategory: ChannelCategory = ChannelCategory.ALL

    companion object {
        private const val PREFS_NAME = "tvplayer_prefs"
        private const val KEY_LIST_URL = "list_url"
        private const val KEY_LIST_NAME = "list_name"
        private const val TAG = "MainActivity"
        // Timeouts agora estão no ViewModel
    }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.w(TAG, "Falha ao persistir permissão para URI: $selectedUri", e)
            }
            val fileName = getFileNameFromUri(selectedUri) ?: getString(R.string.dialog_option_arquivo_local)
            // SR_CORRECTION: Chamar o ViewModel para carregar
            channelViewModel.loadM3UFromSourceIfNotLoaded(selectedUri.toString(), fileName, applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        MobileAds.initialize(this) {}
        binding.adViewMain.loadAd(AdRequest.Builder().build())

        setupRecyclerView()
        setupTabLayout()
        setupSearch()
        setupFab()

        observeViewModel() // Observar mudanças do ViewModel

        // SR_CORRECTION: A lógica de carregar a lista salva agora também usa o ViewModel
        // Se não houver estado salvo no ViewModel (primeira vez), então carrega dos SharedPreferences
        if (channelViewModel.channelListState.value is ListLoadingState.Idle || channelViewModel.channelListState.value == null) {
            loadSavedListFromPrefs()
        }
    }

    private fun observeViewModel() {
        channelViewModel.channelListState.observe(this, Observer { state ->
            when (state) {
                is ListLoadingState.Loading -> {
                    binding.progressBarLoading.visibility = View.VISIBLE
                    channelAdapter.submitList(null) // Limpa a lista na UI
                    // Não limpe listFilteredByCategory aqui, pois pode ser usado pela busca
                }
                is ListLoadingState.Success -> {
                    binding.progressBarLoading.visibility = View.GONE
                    // A fullChannelList agora está no ViewModel
                    // A Activity só precisa saber o nome da lista e a fonte para UI e SharedPreferences
                    updateListInfoUI(state.listName, state.sourceIdentifier)
                    saveListConfigToPrefs(state.listName, state.sourceIdentifier) // Salva nos prefs se carregado com sucesso

                    // Atualiza a lista de categorias e aplica o filtro da categoria selecionada
                    val currentTabPosition = binding.tabLayoutCategories.selectedTabPosition
                    if (currentTabPosition != -1 && currentTabPosition < binding.tabLayoutCategories.tabCount) {
                        updateChannelListForCategory() // Usa channelViewModel.fullChannelList
                    } else if (binding.tabLayoutCategories.tabCount > 0) {
                        binding.tabLayoutCategories.getTabAt(0)?.select()
                    }

                    if (channelViewModel.fullChannelList.isEmpty() && state.sourceIdentifier.startsWith("http")) {
                        Toast.makeText(this, "Lista '${state.listName}' carregada, mas nenhum canal válido foi encontrado ou o formato é inválido.", Toast.LENGTH_LONG).show()
                    } else if (channelViewModel.fullChannelList.isEmpty()) {
                        Toast.makeText(this, "Lista '${state.listName}' carregada está vazia.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Lista '${state.listName}' carregada com ${channelViewModel.fullChannelList.size} canais.", Toast.LENGTH_SHORT).show()
                    }
                }
                is ListLoadingState.Error -> {
                    binding.progressBarLoading.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    handleLoadErrorUI()
                }
                is ListLoadingState.Idle -> {
                    binding.progressBarLoading.visibility = View.GONE
                    // Estado inicial, pode não precisar fazer nada ou mostrar mensagem "nenhuma lista"
                }
            }
        })
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

    private fun setupTabLayout() {
        binding.tabLayoutCategories.addTab(binding.tabLayoutCategories.newTab().setText(getString(R.string.category_all)).setTag(ChannelCategory.ALL))
        binding.tabLayoutCategories.addTab(binding.tabLayoutCategories.newTab().setText(getString(R.string.category_channels)).setTag(ChannelCategory.CHANNELS))
        binding.tabLayoutCategories.addTab(binding.tabLayoutCategories.newTab().setText(getString(R.string.category_movies)).setTag(ChannelCategory.MOVIES))
        binding.tabLayoutCategories.addTab(binding.tabLayoutCategories.newTab().setText(getString(R.string.category_series)).setTag(ChannelCategory.SERIES))
        binding.tabLayoutCategories.addTab(binding.tabLayoutCategories.newTab().setText(getString(R.string.category_others)).setTag(ChannelCategory.OTHERS))

        binding.tabLayoutCategories.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentSelectedCategory = tab?.tag as? ChannelCategory ?: ChannelCategory.ALL
                Log.d(TAG, "Categoria selecionada: $currentSelectedCategory")
                updateChannelListForCategory()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                if (tab?.tag == currentSelectedCategory) {
                    updateChannelListForCategory()
                }
            }
        })
    }

    private fun updateChannelListForCategory() {
        // Log.d(TAG, "Atualizando lista para categoria: $currentSelectedCategory. Total de canais na lista do ViewModel: ${channelViewModel.fullChannelList.size}")
        listFilteredByCategory = if (currentSelectedCategory == ChannelCategory.ALL) {
            channelViewModel.fullChannelList // Usa a lista do ViewModel
        } else {
            channelViewModel.fullChannelList.filter { getChannelCategoryFromString(it.groupTitle) == currentSelectedCategory }
        }
        // Log.d(TAG, "Número de itens após filtro de categoria: ${listFilteredByCategory.size}")
        applyTextSearchFilter()
    }

    private fun setupSearch() {
        binding.edtSearch.addTextChangedListener { editable ->
            applyTextSearchFilter()
        }
    }

    private fun applyTextSearchFilter() {
        val query = binding.edtSearch.text.toString().trim().lowercase()
        val listToSubmit: List<M3UItem> = if (query.isEmpty()) {
            listFilteredByCategory
        } else {
            listFilteredByCategory.filter { item ->
                item.name.lowercase().contains(query)
            }
        }
        channelAdapter.submitList(null)
        channelAdapter.submitList(ArrayList(listToSubmit))

        if (listToSubmit.isNotEmpty()) {
            binding.recyclerViewChannels.post {
                binding.recyclerViewChannels.scrollToPosition(0)
            }
        }
    }

    private fun setupFab() {
        binding.fabLoadM3U.setOnClickListener { showLoadOptionsDialog() }
    }

    // SR_CORRECTION: Renomeado para clareza
    private fun loadSavedListFromPrefs() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(KEY_LIST_URL, null)
        val savedName = prefs.getString(KEY_LIST_NAME, null)

        if (!savedUrl.isNullOrBlank() && !savedName.isNullOrBlank()) {
            // Não chama updateListInfoUI diretamente, o ViewModel fará isso via LiveData
            channelViewModel.loadM3UFromSourceIfNotLoaded(savedUrl, savedName, applicationContext)
        } else {
            binding.tvCurrentListInfo.text = getString(R.string.nenhuma_lista_selecionada)
            // A lista já estará vazia no ViewModel ou será tratada pelo estado Idle
            channelViewModel.fullChannelList.let { // Acessa a lista do ViewModel
                listFilteredByCategory = if (currentSelectedCategory == ChannelCategory.ALL) it else it.filter { item -> getChannelCategoryFromString(item.groupTitle) == currentSelectedCategory }
                applyTextSearchFilter()
            }
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
                    // SR_CORRECTION: Chamar o ViewModel para carregar
                    channelViewModel.loadM3UFromSourceIfNotLoaded(url, name, applicationContext)
                } else {
                    Toast.makeText(this, getString(R.string.toast_insira_nome_url_validos), Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_button_cancelar), null)
            .show()
    }

    // SR_CORRECTION: loadM3UFromSource, openStreamReader, e processParsedM3UList foram movidos para o ViewModel
    // A Activity agora chama channelViewModel.loadM3UFromSourceIfNotLoaded(...)

    private fun handleLoadErrorUI() { // Renomeado para clareza, pois só atualiza UI
        // A lista no ViewModel já estará vazia ou o estado será Error
        channelViewModel.fullChannelList.let { // Acessa a lista do ViewModel
            listFilteredByCategory = if (currentSelectedCategory == ChannelCategory.ALL) it else it.filter { item -> getChannelCategoryFromString(item.groupTitle) == currentSelectedCategory }
            applyTextSearchFilter()
        }
        binding.tvCurrentListInfo.text = getString(R.string.nenhuma_lista_selecionada)
    }

    // SR_CORRECTION: Renomeado para clareza
    private fun saveListConfigToPrefs(listName: String, listUrlOrUri: String) {
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
