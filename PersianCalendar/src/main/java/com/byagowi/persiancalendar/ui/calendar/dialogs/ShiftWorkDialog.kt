package com.byagowi.persiancalendar.ui.calendar.dialogs

import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.byagowi.persiancalendar.PREF_SHIFT_WORK_RECURS
import com.byagowi.persiancalendar.PREF_SHIFT_WORK_SETTING
import com.byagowi.persiancalendar.PREF_SHIFT_WORK_STARTING_JDN
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.databinding.ShiftWorkItemBinding
import com.byagowi.persiancalendar.databinding.ShiftWorkSettingsBinding
import com.byagowi.persiancalendar.entities.ShiftWorkRecord
import com.byagowi.persiancalendar.ui.calendar.CalendarFragmentDirections
import com.byagowi.persiancalendar.utils.Jdn
import com.byagowi.persiancalendar.utils.appPrefs
import com.byagowi.persiancalendar.utils.applyAppLanguage
import com.byagowi.persiancalendar.utils.formatDate
import com.byagowi.persiancalendar.utils.formatNumber
import com.byagowi.persiancalendar.utils.layoutInflater
import com.byagowi.persiancalendar.utils.mainCalendar
import com.byagowi.persiancalendar.utils.putJdn
import com.byagowi.persiancalendar.utils.shiftWorkRecurs
import com.byagowi.persiancalendar.utils.shiftWorkStartingJdn
import com.byagowi.persiancalendar.utils.shiftWorkTitles
import com.byagowi.persiancalendar.utils.shiftWorks
import com.byagowi.persiancalendar.utils.spacedComma
import com.byagowi.persiancalendar.utils.updateStoredPreference

fun Fragment.showShiftWorkDialog(selectedJdn: Jdn) {
    val activity = activity ?: return
    applyAppLanguage(activity)
    updateStoredPreference(activity)

    var isFirstSetup = false
    var jdn = shiftWorkStartingJdn ?: run {
        isFirstSetup = true
        selectedJdn
    }

    val binding = ShiftWorkSettingsBinding.inflate(activity.layoutInflater, null, false)
    binding.recyclerView.layoutManager = LinearLayoutManager(activity)
    val shiftWorkItemAdapter = ShiftWorkItemsAdapter(
        if (shiftWorks.isEmpty()) listOf(ShiftWorkRecord("d", 0)) else shiftWorks,
        binding
    )
    binding.recyclerView.adapter = shiftWorkItemAdapter

    binding.description.text = getString(
        if (isFirstSetup) R.string.shift_work_starting_date
        else R.string.shift_work_starting_date_edit
    ).format(formatDate(jdn.toCalendar(mainCalendar)))

    binding.resetLink.setOnClickListener {
        jdn = selectedJdn
        binding.description.text = getString(R.string.shift_work_starting_date)
            .format(formatDate(jdn.toCalendar(mainCalendar)))
        shiftWorkItemAdapter.reset()
    }
    binding.recurs.isChecked = shiftWorkRecurs
    binding.root.onCheckIsTextEditor()

    AlertDialog.Builder(activity)
        .setView(binding.root)
        .setTitle(null)
        .setPositiveButton(R.string.accept) { _, _ ->
            val result = shiftWorkItemAdapter.rows.filter { it.length != 0 }.joinToString(",") {
                "${it.type.replace("=", "").replace(",", "")}=${it.length}"
            }

            activity.appPrefs.edit {
                putJdn(PREF_SHIFT_WORK_STARTING_JDN, if (result.isEmpty()) null else jdn)
                putString(PREF_SHIFT_WORK_SETTING, result)
                putBoolean(PREF_SHIFT_WORK_RECURS, binding.recurs.isChecked)
            }

            updateStoredPreference(activity)
            findNavController().navigate(CalendarFragmentDirections.navigateToSelf())
        }
        .setCancelable(true)
        .setNegativeButton(R.string.cancel, null)
        .create().also {
            // XXX: Even using this bringing of virtual keyboard doesn't work
            binding.root.postDelayed({
                it.window?.clearFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                )
                it.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }, 1000)
        }
        .show()
}

private class ShiftWorkItemsAdapter(
    var rows: List<ShiftWorkRecord>, private val binding: ShiftWorkSettingsBinding
) : RecyclerView.Adapter<ShiftWorkItemsAdapter.ViewHolder>() {

    init {
        updateShiftWorkResult()
    }

    fun shiftWorkKeyToString(type: String): String = shiftWorkTitles[type] ?: type

    private fun updateShiftWorkResult() =
        rows.filter { it.length != 0 }.joinToString(spacedComma) {
            binding.root.context.getString(R.string.shift_work_record_title)
                .format(formatNumber(it.length), shiftWorkKeyToString(it.type))
        }.also {
            binding.result.text = it
            binding.result.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ShiftWorkItemBinding.inflate(parent.context.layoutInflater, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)

    override fun getItemCount(): Int = rows.size + 1

    fun reset() {
        rows = listOf(ShiftWorkRecord("d", 0))
        notifyDataSetChanged()
        updateShiftWorkResult()
    }

    inner class ViewHolder(private val binding: ShiftWorkItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            val context = binding.root.context

            binding.lengthSpinner.adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                (0..7).map {
                    if (it == 0) binding.root.context.getString(R.string.shift_work_days_head)
                    else formatNumber(it)
                }
            )

            binding.typeAutoCompleteTextView.run {
                val adapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_dropdown_item,
                    resources.getStringArray(R.array.shift_work)
                )
                setAdapter(adapter)
                setOnClickListener {
                    if (text.toString().isNotEmpty()) adapter.filter.filter(null)
                    showDropDown()
                }
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>, view: View, position: Int, id: Long
                    ) {
                        rows = rows.mapIndexed { i, x ->
                            if (i == bindingAdapterPosition)
                                ShiftWorkRecord(text.toString(), x.length)
                            else x
                        }
                        updateShiftWorkResult()
                    }

                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {}

                    override fun beforeTextChanged(
                        s: CharSequence?, start: Int, count: Int, after: Int
                    ) = Unit

                    override fun onTextChanged(
                        s: CharSequence?, start: Int, before: Int, count: Int
                    ) {
                        rows = rows.mapIndexed { i, x ->
                            if (i == bindingAdapterPosition)
                                ShiftWorkRecord(text.toString(), x.length)
                            else x
                        }
                        updateShiftWorkResult()
                    }
                })
                // Don't allow inserting '=' or ',' as they have special meaning
                filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
                    if (Regex("[=,]") in (source ?: "")) "" else null
                })
            }

            binding.remove.setOnClickListener { remove() }

            binding.lengthSpinner.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>) {}
                    override fun onItemSelected(
                        parent: AdapterView<*>, view: View, position: Int, id: Long
                    ) {
                        rows = rows.mapIndexed { i, x ->
                            if (i == bindingAdapterPosition) ShiftWorkRecord(x.type, position)
                            else x
                        }
                        updateShiftWorkResult()
                    }
                }

            binding.addButton.setOnClickListener {
                rows = rows + ShiftWorkRecord("r", 0)
                notifyDataSetChanged()
                updateShiftWorkResult()
            }
        }

        fun remove() {
            rows = rows.filterIndexed { i, _ -> i != bindingAdapterPosition }
            notifyDataSetChanged()
            updateShiftWorkResult()
        }

        fun bind(position: Int) = if (position < rows.size) {
            val shiftWorkRecord = rows[position]
            binding.rowNumber.text = "%s:".format(formatNumber(position + 1))
            binding.lengthSpinner.setSelection(shiftWorkRecord.length)
            binding.typeAutoCompleteTextView.setText(shiftWorkKeyToString(shiftWorkRecord.type))
            binding.detail.visibility = View.VISIBLE
            binding.addButton.visibility = View.GONE
        } else {
            binding.detail.visibility = View.GONE
            binding.addButton.visibility = if (rows.size < 20) View.VISIBLE else View.GONE
        }
    }
}
