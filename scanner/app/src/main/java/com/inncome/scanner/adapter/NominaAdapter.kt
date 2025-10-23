package com.inncome.scanner.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inncome.scanner.data.NominaDetail
import com.inncome.scanner.databinding.ItemNominaBinding

class NominaAdapter(
    private val nominas: List<NominaDetail>,
    private val onNominaSelected: (NominaDetail) -> Unit
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

    override fun getItemCount() = nominas.size

    inner class NominaViewHolder(
        private val binding: ItemNominaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(nomina: NominaDetail) {
            binding.apply {
                tvTipoNomina.text = nomina.incomeType
                tvActividad.text = nomina.actividad?.activityName ?: "Sin actividad"
                tvEstado.text = nomina.status
                tvFechaCreacion.text = "Creada: ${nomina.createdAt}"
                tvCantidadIngresos.text = "${nomina.ingresos.size} ingreso(s) previo(s)"

                // Color según estado
                val colorEstado = when (nomina.status) {
                    "ACTIVO" -> android.graphics.Color.parseColor("#00FF00")
                    "PENDIENTE" -> android.graphics.Color.parseColor("#FFAA00")
                    else -> android.graphics.Color.parseColor("#FF0000")
                }
                tvEstado.setTextColor(colorEstado)

                root.setOnClickListener {
                    onNominaSelected(nomina)
                }
            }
        }
    }
}