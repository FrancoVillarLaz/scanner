package com.inncome.scanner.dialog



import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.inncome.scanner.R
import com.inncome.scanner.adapter.NominaAdapter
import com.inncome.scanner.data.entities.Nomina
import com.inncome.scanner.databinding.DialogNominaSelectionBinding
import com.inncome.scanner.databinding.ItemNominaBinding

class NominaSelectionDialog(
    context: Context,
    private val nominas: List<Nomina>,
    private val onNominaSelected: (Nomina) -> Unit
) : Dialog(context, R.style.CustomDialogTheme) {

    private lateinit var binding: DialogNominaSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogNominaSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupCancelButton()
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

    private fun setupCancelButton() {
        binding.btnCancelar.setOnClickListener {
            dismiss()
        }
    }


}
