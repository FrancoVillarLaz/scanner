package com.inncome.scanner.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.inncome.scanner.data.entities.HistoryItem
import com.inncome.scanner.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryAdapter : ListAdapter<HistoryItem, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val historyItem = getItem(position)
        holder.bind(historyItem)
    }

    inner class HistoryViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(historyItem: HistoryItem) {
            // Formatear DNI (agregar puntos)
            val dniFormateado = formatearDNI(historyItem.operario.documentNumber)
            binding.tvDniHistory.text = "DNI: $dniFormateado"

            // Nombre completo
            val nombreCompleto = "${historyItem.operario.firstName} ${historyItem.operario.lastName}"
            binding.tvNombreHistory.text = nombreCompleto

            // Actividad/Oficio
            binding.tvActividadHistory.text = historyItem.actividad.activityName

            // Tipo de ingreso (ENTRADA/SALIDA)
            val tipoIngreso = when (historyItem.incomeType.uppercase()) {
                "INCOME", "INNCOME" -> "ENTRADA"
                "OUTCOME" -> "SALIDA"
                else -> historyItem.incomeType
            }
            binding.tvTipoHistory.text = tipoIngreso

            // Color según tipo de ingreso
            val colorTipo = when (tipoIngreso.uppercase()) {
                "ENTRADA" -> 0xFF43E9E8.toInt()
                "SALIDA" -> 0xFFFF6B6B.toInt()
                else -> 0xFFCCCCCC.toInt()
            }
            binding.tvTipoHistory.setTextColor(colorTipo)

            // Color del indicador de estado
            val colorIndicator = when (historyItem.status.uppercase()) {
                "ACTIVO" -> 0xFF43E9E8.toInt()
                "INACTIVO" -> 0xFFFF6B6B.toInt()
                else -> 0xFFCCCCCC.toInt()
            }
            binding.statusIndicator.setBackgroundColor(colorIndicator)

            // Formatear fecha
            val fechaFormateada = formatearFecha(historyItem.createdAt)
            binding.tvFechaHistory.text = fechaFormateada
        }

        private fun formatearDNI(dni: String): String {
            return try {
                if (dni.length >= 7) {
                    val parte1 = dni.substring(0, 2)
                    val parte2 = dni.substring(2, 5)
                    val parte3 = dni.substring(5)
                    "$parte1.$parte2.$parte3"
                } else {
                    dni
                }
            } catch (e: Exception) {
                dni
            }
        }

        private fun formatearFecha(fechaISO: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                val date = inputFormat.parse(fechaISO)
                outputFormat.format(date)
            } catch (e: Exception) {
                fechaISO
            }
        }
    }
    companion object {
        private val HistoryDiffCallback = object : DiffUtil.ItemCallback<HistoryItem>() {
            override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
                return oldItem.id == newItem.id &&
                        oldItem.createdAt == newItem.createdAt &&
                        oldItem.incomeType == newItem.incomeType &&
                        oldItem.operario.documentNumber == newItem.operario.documentNumber
            }
        }
    }
}