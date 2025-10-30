package com.inncome.scanner.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.inncome.scanner.data.entities.Nomina
import com.inncome.scanner.databinding.ItemNominaBinding

class NominaAdapter(
        private val nominas: List<Nomina>,
        private val onClick: (Nomina) -> Unit
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

            fun bind(nomina: Nomina) {
                binding.apply {
                    // ✅ Mostrar actividad
                    tvActividad.text = nomina.actividad.activityName

                    // ✅ Mostrar estado con validación
                    if (nomina.operario == null) {
                        tvEstado.text = "Operario no disponible"
                        tvEstado.setTextColor(0xFFCCCCCC.toInt())
                    } else {
                        tvEstado.text = nomina.status
                        // Color según estado
                        val statusColor = when (nomina.status.uppercase()) {
                            "ACTIVO" -> 0xFF43E9E8.toInt()
                            "INACTIVO" -> 0xFFFF6B6B.toInt()
                            else -> 0xFFCCCCCC.toInt()
                        }
                        tvEstado.setTextColor(statusColor)
                    }

                    // ✅ Mostrar tipo de ingreso
                    tvTipoIngreso.text = nomina.incomeType

                    // ✅ Click listener
                    root.setOnClickListener {
                        onClick(nomina)
                    }
                }
            }
        }
}
