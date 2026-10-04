package com.xiaomi.purifier.reset.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.xiaomi.purifier.reset.R
import com.xiaomi.purifier.reset.data.FilterTagData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private var items: List<FilterTagData>,
    private val onRestoreClick: (FilterTagData) -> Unit,
    private val onItemClick: (FilterTagData) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFilterType: TextView = view.findViewById(R.id.tvHistoryFilterType)
        val tvPercent: TextView = view.findViewById(R.id.tvHistoryPercent)
        val tvUid: TextView = view.findViewById(R.id.tvHistoryUid)
        val tvPwd: TextView = view.findViewById(R.id.tvHistoryPwd)
        val tvDate: TextView = view.findViewById(R.id.tvHistoryDate)
        val btnRestore: MaterialButton = view.findViewById(R.id.btnHistoryRestore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvFilterType.text = item.filterType.getDisplayName(holder.itemView.context)
        holder.tvPercent.text = "${item.remainingPercent}%"
        holder.tvUid.text = "UID: ${item.uid}"
        holder.tvPwd.text = "PWD: ${item.passwordHex}"

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        holder.tvDate.text = sdf.format(Date(item.timestamp))

        holder.btnRestore.setOnClickListener {
            onRestoreClick(item)
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<FilterTagData>) {
        items = newItems
        notifyDataSetChanged()
    }
}
