package com.example.keepalive

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter that displays installed apps with a checkbox for selecting
 * which apps to keep alive.
 */
class AppListAdapter(
    private val onCheckedChange: (AppInfo, Boolean) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.AppViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.iv_app_icon)
        private val name: TextView = itemView.findViewById(R.id.tv_app_name)
        private val pkg: TextView = itemView.findViewById(R.id.tv_app_package)
        private val check: CheckBox = itemView.findViewById(R.id.cb_select_app)

        fun bind(app: AppInfo) {
            icon.setImageDrawable(app.icon)
            name.text = app.appName
            pkg.text = app.packageName

            // Avoid triggering the listener when we programmatically set the state.
            check.setOnCheckedChangeListener(null)
            check.isChecked = app.selected
            check.setOnCheckedChangeListener { _, isChecked ->
                app.selected = isChecked
                onCheckedChange(app, isChecked)
            }

            // Tap anywhere on the row toggles the checkbox.
            itemView.setOnClickListener { check.isChecked = !check.isChecked }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo) =
                oldItem == newItem
        }
    }
}
