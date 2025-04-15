package com.example.tvplayer.ui.config

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.tvplayer.R
import com.example.tvplayer.ui.list.ChannelListActivity

class ListConfigActivity : AppCompatActivity() {

    private lateinit var edtListName: EditText
    private lateinit var edtListUrl: EditText
    private lateinit var btnSave: Button
    private lateinit var btnSelectFile: Button
    private lateinit var btnViewList: Button

    private val PREFS_NAME = "tvplayer_prefs"
    private val KEY_LIST_URL = "list_url"
    private val KEY_LIST_NAME = "list_name"

    // Registrar o seletor de arquivo para carregar um arquivo M3U (filtra com "*/*" para aceitar qualquer MIME)
    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                // Tenta persistir a permissão para a URI
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                try {
                    // Lê o conteúdo (apenas para validação ou processamento adicional se necessário)
                    contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
                        val fileContent = reader?.readText() ?: ""
                        // Se desejar, você pode testar se o conteúdo contém "#EXTM3U"
                        if (!fileContent.contains("#EXTM3U", ignoreCase = true)) {
                            Toast.makeText(
                                this,
                                "Arquivo selecionado não parece ser um arquivo M3U.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@registerForActivityResult
                        }
                    }
                    // Salva a URI do arquivo (como string) para uso futuro
                    saveListConfig("", uri.toString())
                    // Atualiza o campo de URL para mostrar o link e permitir edição
                    edtListUrl.setText(uri.toString())
                    Toast.makeText(this, "Arquivo selecionado e carregado.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Erro ao ler o arquivo.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Nenhum arquivo selecionado.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_config)

        edtListName = findViewById(R.id.edtListName)
        edtListUrl = findViewById(R.id.edtListUrl)
        btnSave = findViewById(R.id.btnSave)
        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnViewList = findViewById(R.id.btnViewList)

        // Carrega os dados salvos, se existirem
        loadListConfig()

        btnSave.setOnClickListener {
            val listName = edtListName.text.toString().trim()
            val listUrl = edtListUrl.text.toString().trim()
            if (listName.isEmpty() || listUrl.isEmpty()) {
                Toast.makeText(this, "Por favor, insira um nome e uma URL válidos.", Toast.LENGTH_SHORT).show()
            } else {
                saveListConfig(listName, listUrl)
                Toast.makeText(this, "Dados salvos com sucesso.", Toast.LENGTH_SHORT).show()
            }
        }

        btnSelectFile.setOnClickListener {
            // Lança o seletor com filtro ampliado
            filePickerLauncher.launch("*/*")
        }

        btnViewList.setOnClickListener {
            val listUrl = edtListUrl.text.toString().trim()
            if (listUrl.isEmpty()) {
                Toast.makeText(this, "Por favor, insira uma URL ou selecione um arquivo.", Toast.LENGTH_SHORT).show()
            } else {
                // Navega para a tela que exibe a lista de canais
                val intent = Intent(this, ChannelListActivity::class.java)
                startActivity(intent)
            }
        }
    }

    /**
     * Salva o nome e a URL (ou URI) da lista nos SharedPreferences.
     */
    private fun saveListConfig(listName: String, listUrl: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putString(KEY_LIST_NAME, listName)
            putString(KEY_LIST_URL, listUrl)
            apply()
        }
    }

    /**
     * Carrega os dados salvos dos SharedPreferences e atualiza os EditTexts.
     */
    private fun loadListConfig() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedName = prefs.getString(KEY_LIST_NAME, "")
        val savedUrl = prefs.getString(KEY_LIST_URL, "")
        edtListName.setText(savedName)
        edtListUrl.setText(savedUrl)
    }
}
