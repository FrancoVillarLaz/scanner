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
            init {
                binding.root.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            fun bind(nomina: Nomina) {
                binding.apply {
                    // ✅ Mostrar actividad
                    tvActividad.text = nomina.actividad.activityName

                    if (nomina.operario == null) {
                        tvEstado.text = "Operario no disponible"
                        tvEstado.setTextColor(0xFFCCCCCC.toInt())
                    } else {
                        tvEstado.text = nomina.status
                        val statusColor = when (nomina.status.uppercase()) {
                            "ACTIVO" -> 0xFF43E9E8.toInt()
                            "INACTIVO" -> 0xFFFF6B6B.toInt()
                            else -> 0xFFCCCCCC.toInt()
                        }
                        tvEstado.setTextColor(statusColor)
                    }

                    tvTipoIngreso.text = nomina.incomeType

                    root.setOnClickListener {
                        onClick(nomina)
                    }
                }
            }
        }
}
