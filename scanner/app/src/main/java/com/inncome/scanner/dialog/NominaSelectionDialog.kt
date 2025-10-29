package com.inncome.scanner.dialog



import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import androidx.recyclerview.widget.LinearLayoutManager
import com.inncome.scanner.adapter.NominaAdapter
import com.inncome.scanner.data.entities.Nomina
import com.inncome.scanner.databinding.DialogNominaSelectionBinding

class NominaSelectionDialog(
    context: Context,
    private val nominas: List<Nomina>,
    private val onNominaSelected: (Nomina) -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogNominaSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = DialogNominaSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar ventana
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        setupRecyclerView()
        setupButtons()
    }

    private fun setupRecyclerView() {
        val adapter = NominaAdapter(nominas) { nominaSeleccionada ->
            onNominaSelected(nominaSeleccionada)
            dismiss()
        }

        binding.rvNominas.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
    }

    private fun setupButtons() {
        binding.btnCancelar.setOnClickListener {
            dismiss()
        }
    }
}