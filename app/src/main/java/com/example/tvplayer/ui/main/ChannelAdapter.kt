package com.example.tvplayer.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tvplayer.R
import com.example.tvplayer.data.model.M3UItem

class ChannelAdapter(
    private var channels: List<M3UItem>,
    private val onItemClick: (M3UItem) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    inner class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val channelName: TextView = itemView.findViewById(R.id.textChannelName)
        private val channelLogo: ImageView = itemView.findViewById(R.id.imageLogo)

        fun bind(item: M3UItem) {
            channelName.text = item.name
            // Se quiser carregar a imagem, utilize uma biblioteca como Glide ou Picasso:
            // Glide.with(itemView.context).load(item.logo).into(channelLogo)
            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(channels[position])
    }

    override fun getItemCount(): Int = channels.size

    // Método para atualizar os dados do adapter e notificar as mudanças
    fun updateData(newChannels: List<M3UItem>) {
        channels = newChannels
        notifyDataSetChanged()
    }
}
