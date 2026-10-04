package com.xiaomi.purifier.reset.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.xiaomi.purifier.reset.R
import com.xiaomi.purifier.reset.data.ChangeLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChangeLogAdapter(
    private var items: List<ChangeLogEntry>,
    private val onRestoreClick: (ChangeLogEntry) -> Unit
) : RecyclerView.Adapter<ChangeLogAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvActionTitle: TextView = view.findViewById(R.id.tvLogActionTitle)
        val tvTimestamp: TextView = view.findViewById(R.id.tvLogTimestamp)
        val tvDeviceName: TextView = view.findViewById(R.id.tvLogDeviceName)
        val tvPercentTransition: TextView = view.findViewById(R.id.tvLogPercentTransition)
        val tvHexTransition: TextView = view.findViewById(R.id.tvLogHexTransition)
        val btnRestore: MaterialButton = view.findViewById(R.id.btnLogRestoreState)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_change_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isVi = holder.itemView.context.resources.configuration.locales[0].language == "vi"
        holder.tvActionTitle.text = if (isVi) item.actionType.labelVi else item.actionType.labelEn

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        holder.tvTimestamp.text = sdf.format(Date(item.timestamp))

        val aliasText = if (item.deviceAlias.isNotBlank()) "${item.deviceAlias} " else ""
        holder.tvDeviceName.text = "${aliasText}(UID: ${item.uid})"

        holder.tvPercentTransition.text = holder.itemView.context.getString(R.string.label_life_transition, item.previousPercent, item.newPercent)
        holder.tvHexTransition.text = "${item.previousWorkTimeHex} ➔ ${item.newWorkTimeHex}"

        holder.btnRestore.setOnClickListener {
            onRestoreClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<ChangeLogEntry>) {
        items = newItems
        notifyDataSetChanged()
    }
}
