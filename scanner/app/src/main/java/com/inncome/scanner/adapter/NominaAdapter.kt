package com.inncome.scanner.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inncome.scanner.data.entities.Nomina
import com.inncome.scanner.databinding.ItemNominaBinding

class NominaAdapter(
    private val nominas: List<Nomina>,
    private val onNominaClick: (Nomina) -> Unit
) : RecyclerView.Adapter<NominaAdapter.NominaViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NominaViewHolder {
        val binding = ItemNominaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NominaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NominaViewHolder, position: Int) {
        holder.bind(nominas[position])
    }

    override fun getItemCount(): Int = nominas.size

    inner class NominaViewHolder(
        private val binding: ItemNominaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(nomina: Nomina) {
            binding.tvNominaActividad.text = nomina.actividad.activityName
            binding.tvNominaIncomeType.text = nomina.incomeType
            binding.tvNominaStatus.text = nomina.status

            // Color según estado
            val statusColor = when (nomina.status.uppercase()) {
                "ACTIVO" -> 0xFF43E9E8.toInt()
                "INACTIVO" -> 0xFFFF6B6B.toInt()
                else -> 0xFFCCCCCC.toInt()
            }
            binding.tvNominaStatus.setTextColor(statusColor)

            // Click listener
            binding.root.setOnClickListener {
                onNominaClick(nomina)
            }
        }
    }
}