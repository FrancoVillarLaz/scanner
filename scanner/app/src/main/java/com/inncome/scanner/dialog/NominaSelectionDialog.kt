package com.inncome.scanner.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.inncome.scanner.adapter.NominaAdapter
import com.inncome.scanner.data.NominaDetail
import com.inncome.scanner.databinding.DialogNominaSelectionBinding


class NominaSelectionDialog(
    context: Context,
    private val nominas: List<NominaDetail>,
    private val onNominaSelected: (NominaDetail) -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogNominaSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogNominaSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        setupRecyclerView()
        binding.btnCancelar.setOnClickListener { dismiss() }
    }

    private fun setupRecyclerView() {
        val adapter = NominaAdapter(nominas) { nomina ->
            onNominaSelected(nomina)
            dismiss()
        }

        binding.rvNominas.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
    }
}