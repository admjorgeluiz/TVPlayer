package com.jorgenascimento.tvplayer.ui.main

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.jorgenascimento.tvplayer.R
import com.jorgenascimento.tvplayer.data.model.M3UItem
import com.jorgenascimento.tvplayer.databinding.ItemChannelBinding

class ChannelAdapter(
    private val onItemClick: (M3UItem) -> Unit
) : ListAdapter<M3UItem, ChannelAdapter.ChannelViewHolder>(ChannelDiffCallback()) {

    companion object {
        private const val ADAPTER_TAG = "ChannelAdapter"
    }

    inner class ChannelViewHolder(
        private val binding: ItemChannelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: M3UItem) {
            binding.textChannelName.text = item.name
            // Log.d(ADAPTER_TAG, "Binding item (ViewHolder): ${item.name}") // Descomente para depuração fina do bind

            if (!item.logo.isNullOrEmpty()) {
                Glide.with(binding.imageLogo.context)
                    .load(item.logo)
                    .placeholder(R.mipmap.ic_launcher) // Use um placeholder apropriado
                    .error(R.mipmap.ic_launcher)       // Use uma imagem de erro apropriada
                    .into(binding.imageLogo)
            } else {
                binding.imageLogo.setImageResource(R.mipmap.ic_launcher) // Imagem padrão
            }
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val item = getItem(position)
        // Log.d(ADAPTER_TAG, "onBindViewHolder: position=$position, name='${item.name}'") // Descomente para depuração fina
        holder.bind(item)
    }
}

class ChannelDiffCallback : DiffUtil.ItemCallback<M3UItem>() {
    companion object {
        private const val DIFF_TAG = "ChannelDiffCallback"
    }

    override fun areItemsTheSame(oldItem: M3UItem, newItem: M3UItem): Boolean {
        // Itens são os mesmos se as URLs (identificador único) forem as mesmas
        val result = oldItem.url == newItem.url
        // Log.d(DIFF_TAG, "areItemsTheSame: old='${oldItem.name}', new='${newItem.name}', RESULT=$result")
        return result
    }

    override fun areContentsTheSame(oldItem: M3UItem, newItem: M3UItem): Boolean {
        // Para data class, '==' compara todos os campos.
        // Isto é geralmente o que se quer para determinar se o item precisa ser re-vinculado.
        val result = oldItem == newItem
        // Log.d(DIFF_TAG, "areContentsTheSame: old='${oldItem.name}', new='${newItem.name}', RESULT=$result")
        return result
    }
}
