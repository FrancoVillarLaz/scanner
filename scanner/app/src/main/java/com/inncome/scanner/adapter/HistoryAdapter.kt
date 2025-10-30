package com.inncome.scanner.adapter

import android.util.Log
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

    private val TAG = "HistoryAdapter"
    private val currentItems = mutableListOf<HistoryItem>()

    // Listener para paginación
    var onLoadMore: (() -> Unit)? = null
    private var isLoading = false

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

        // ✅ Verificar si necesitamos cargar más datos (paginación)
        if (position >= itemCount - 5 && !isLoading) {
            onLoadMore?.invoke()
        }

        holder.bind(historyItem)
    }

    override fun submitList(list: List<HistoryItem>?) {
        Log.d(TAG, "submitList llamado con ${list?.size} items")

        // Validar datos antes de enviar al adapter
        list?.forEachIndexed { index, item ->
            if (item.nomina.operario == null) {
                Log.e(TAG, "❌ OPERARIO NULL en submitList - Index: $index, ID: ${item.id}")
            }
        }

        super.submitList(list)

        // Mantener copia para debugging
        currentItems.clear()
        if (list != null) {
            currentItems.addAll(list)
        }

        isLoading = false
    }

    fun setLoadingState(loading: Boolean) {
        isLoading = loading
    }

    inner class HistoryViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(historyItem: HistoryItem) {
            // ✅ VALIDACIÓN DEFENSIVA CRÍTICA
            if (historyItem.nomina.operario == null) {
                Log.e(TAG, "🛑 Bind con operario null - Posición: $adapterPosition, ID: ${historyItem.id}")
                manejarOperarioNull(historyItem)
                return
            }

            // ✅ Bind seguro solo si los datos son válidos
            bindSeguro(historyItem)
        }

        private fun bindSeguro(historyItem: HistoryItem) {
            val operario = historyItem.nomina.operario!!

            // Formatear DNI (agregar puntos)
            val dniFormateado = formatearDNI(operario.documentNumber)
            binding.tvDniHistory.text = "DNI: $dniFormateado"

            // Nombre completo
            val nombreCompleto = "${operario.firstName} ${operario.lastName}"
            binding.tvNombreHistory.text = nombreCompleto

            // Actividad/Oficio - con validación
            val actividadText = historyItem.nomina.actividad?.activityName ?: "Actividad no disponible"
            binding.tvActividadHistory.text = actividadText

            // ✅ CORRECCIÓN: Mapeo correcto de accessType
            val (tipoIngreso, color) = when (historyItem.accessType?.uppercase() ?: "") {
                "ENTRADA", "RE_INGRESO" -> Pair("ENTRADA", 0xFF43E9E8.toInt())
                "SALIDA" -> Pair("SALIDA", 0xFFFF6B6B.toInt())
                else -> Pair(historyItem.accessType ?: "DESCONOCIDO", 0xFFCCCCCC.toInt())
            }

            binding.tvTipoHistory.text = tipoIngreso
            binding.tvTipoHistory.setTextColor(color)
            binding.statusIndicator.setBackgroundColor(color)

            // Formatear fecha
            val fechaFormateada = formatearFecha(historyItem.createdAt)
            binding.tvFechaHistory.text = fechaFormateada

            // Log para debugging
            Log.d(TAG, "Item ${historyItem.id}: accessType=${historyItem.accessType} -> $tipoIngreso")
        }

        private fun manejarOperarioNull(historyItem: HistoryItem) {
            // Mostrar datos alternativos o placeholder
            binding.tvDniHistory.text = "DNI: No disponible"
            binding.tvNombreHistory.text = "Operario no disponible"
            binding.tvActividadHistory.text = historyItem.nomina.actividad?.activityName ?: "Actividad no disponible"

            val (tipoIngreso, color) = when (historyItem.accessType?.uppercase() ?: "") {
                "ENTRADA", "RE_INGRESO" -> Pair("ENTRADA", 0xFF43E9E8.toInt())
                "SALIDA" -> Pair("SALIDA", 0xFFFF6B6B.toInt())
                else -> Pair(historyItem.accessType ?: "DESCONOCIDO", 0xFFCCCCCC.toInt())
            }

            binding.tvTipoHistory.text = tipoIngreso
            binding.tvTipoHistory.setTextColor(color)
            binding.statusIndicator.setBackgroundColor(color)

            // Formatear fecha
            val fechaFormateada = formatearFecha(historyItem.createdAt)
            binding.tvFechaHistory.text = fechaFormateada

            Log.w(TAG, "Item con ID ${historyItem.id} mostrado con datos alternativos")
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
                Log.e(TAG, "Error formateando DNI: $dni", e)
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
                Log.e(TAG, "Error formateando fecha: $fechaISO", e)
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
                return try {
                    oldItem.id == newItem.id &&
                            oldItem.createdAt == newItem.createdAt &&
                            oldItem.accessType == newItem.accessType &&
                            (oldItem.nomina.operario != null && newItem.nomina.operario != null) &&
                            oldItem.nomina.operario.documentNumber == newItem.nomina.operario.documentNumber
                } catch (e: Exception) {
                    Log.e("HistoryDiff", "Error en areContentsTheSame", e)
                    false
                }
            }

            override fun getChangePayload(oldItem: HistoryItem, newItem: HistoryItem): Any? {
                return if (areContentsTheSame(oldItem, newItem)) {
                    null
                } else {
                    super.getChangePayload(oldItem, newItem)
                }
            }
        }
    }

    fun diagnosticarEstado(): String {
        val total = currentItems.size
        val conOperarioNull = currentItems.count { it.nomina.operario == null }
        return "Adapter: $total items, $conOperarioNull con operario null"
    }


}