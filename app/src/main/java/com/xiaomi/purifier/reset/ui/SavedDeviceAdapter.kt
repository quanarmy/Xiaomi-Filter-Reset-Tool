package com.xiaomi.purifier.reset.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.xiaomi.purifier.reset.R
import com.xiaomi.purifier.reset.data.SavedDevice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SavedDeviceAdapter(
    private var items: List<SavedDevice>,
    private val onResetClick: (SavedDevice) -> Unit,
    private val onEditAliasClick: (SavedDevice) -> Unit,
    private val onViewLogsClick: (SavedDevice) -> Unit
) : RecyclerView.Adapter<SavedDeviceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvDeviceTitle)
        val tvPercent: TextView = view.findViewById(R.id.tvDevicePercent)
        val tvType: TextView = view.findViewById(R.id.tvDeviceType)
        val tvUid: TextView = view.findViewById(R.id.tvDeviceUid)
        val tvResetCount: TextView = view.findViewById(R.id.tvDeviceResetCount)
        val tvLastReset: TextView = view.findViewById(R.id.tvDeviceLastReset)
        val btnReset: MaterialButton = view.findViewById(R.id.btnResetThisDevice)
        val btnViewLogs: MaterialButton = view.findViewById(R.id.btnViewLogs)
        val btnEditAlias: ImageView = view.findViewById(R.id.btnEditAlias)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.displayTitle
        holder.tvPercent.text = "${item.lastPercent}%"
        holder.tvType.text = item.filterType.getDisplayName(holder.itemView.context)
        holder.tvUid.text = "UID: ${item.uid}"
        holder.tvResetCount.text = holder.itemView.context.getString(R.string.label_reset_count, item.resetCount)

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        holder.tvLastReset.text = holder.itemView.context.getString(R.string.label_last_reset, sdf.format(Date(item.lastResetTimestamp)))

        holder.btnReset.setOnClickListener { onResetClick(item) }
        holder.btnEditAlias.setOnClickListener { onEditAliasClick(item) }
        holder.btnViewLogs.setOnClickListener { onViewLogsClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<SavedDevice>) {
        items = newItems
        notifyDataSetChanged()
    }
}
