package com.example.tvplayer.ui.config

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.example.tvplayer.R
import com.example.tvplayer.ui.list.ChannelListActivity
import android.widget.Toast

class ListConfigActivity : AppCompatActivity() {

    private lateinit var edtListName: EditText
    private lateinit var edtListUrl: EditText
    private lateinit var btnSave: Button
    private lateinit var btnViewList: Button

    private val PREFS_NAME = "tvplayer_prefs"
    private val KEY_LIST_URL = "list_url"
    private val KEY_LIST_NAME = "list_name"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_config)

        edtListName = findViewById(R.id.edtListName)
        edtListUrl = findViewById(R.id.edtListUrl)
        btnSave = findViewById(R.id.btnSave)
        btnViewList = findViewById(R.id.btnViewList)

        // Carregar dados salvos (se existirem)
        loadListConfig()

        btnSave.setOnClickListener {
            val listName = edtListName.text.toString().trim()
            val listUrl = edtListUrl.text.toString().trim()
            if (listName.isEmpty() || listUrl.isEmpty()) {
                Toast.makeText(this, "Por favor, insira nome e URL válidos.", Toast.LENGTH_SHORT).show()
            } else {
                saveListConfig(listName, listUrl)
                Toast.makeText(this, "Dados salvos com sucesso.", Toast.LENGTH_SHORT).show()
            }
        }

        btnViewList.setOnClickListener {
            val listUrl = edtListUrl.text.toString().trim()
            // Verifica se há URL definida antes de navegar
            if (listUrl.isEmpty()) {
                Toast.makeText(this, "Por favor, insira uma URL válida.", Toast.LENGTH_SHORT).show()
            } else {
                // Navega para a Activity de exibição dos canais
                val intent = Intent(this, ChannelListActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun saveListConfig(listName: String, listUrl: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with (prefs.edit()) {
            putString(KEY_LIST_NAME, listName)
            putString(KEY_LIST_URL, listUrl)
            apply()
        }
    }

    private fun loadListConfig() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedName = prefs.getString(KEY_LIST_NAME, "")
        val savedUrl = prefs.getString(KEY_LIST_URL, "")
        edtListName.setText(savedName)
        edtListUrl.setText(savedUrl)
    }
}
