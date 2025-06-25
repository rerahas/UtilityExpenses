package jp.co.orust.utilityexpenses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import jp.co.orust.utilityexpenses.adapter.UtilityBillAdapter
import jp.co.orust.utilityexpenses.data.AppDatabase
import jp.co.orust.utilityexpenses.data.UtilityBill
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var textViewYear: TextView
    private lateinit var textViewMonth: TextView
    private lateinit var editTextAmount: EditText
    private lateinit var buttonSave: Button
    private lateinit var recyclerViewBills: RecyclerView
    private lateinit var barChart: BarChart
    private lateinit var textViewMonthlyTotal: TextView
    private lateinit var textViewInputLabel: TextView
    private lateinit var inputSection: LinearLayout
    private lateinit var summaryDivider: View // ★ 線のビューへの参照

    private lateinit var billAdapter: UtilityBillAdapter
    private var activeCategory: String = ""
    private var inputCategory: String = ""

    private var currentYear: Int = 0
    private var currentMonth: Int = 0

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val billDao by lazy { database.utilityBillDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupViews()
        setupBottomNavigation()
        setupRecyclerView()
        setupChart()

        bottomNavigationView.selectedItemId = R.id.nav_total
    }

    private fun setupViews() {
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        textViewYear = findViewById(R.id.textViewYear)
        textViewMonth = findViewById(R.id.textViewMonth)
        editTextAmount = findViewById(R.id.editTextAmount)
        buttonSave = findViewById(R.id.buttonSave)
        recyclerViewBills = findViewById(R.id.recyclerViewBills)
        barChart = findViewById(R.id.barChart)
        textViewMonthlyTotal = findViewById(R.id.textViewMonthlyTotal)
        textViewInputLabel = findViewById(R.id.textViewInputLabel)
        inputSection = findViewById(R.id.inputSection)
        summaryDivider = findViewById(R.id.summary_divider) // ★ 初期化

        val cal = Calendar.getInstance()
        currentYear = cal.get(Calendar.YEAR)
        currentMonth = cal.get(Calendar.MONTH) + 1
        updateDateTextViews()

        textViewYear.setOnClickListener { showYearPickerDialog() }
        textViewMonth.setOnClickListener { showMonthPickerDialog() }
        buttonSave.setOnClickListener { saveBillData() }
    }

    private fun updateDateTextViews() {
        textViewYear.text = getString(R.string.year_format, currentYear)
        textViewMonth.text = getString(R.string.month_format, currentMonth)
    }

    private fun setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener { item ->
            val categoryTotal = getString(R.string.category_total)
            val categoryElectricity = getString(R.string.category_electricity)
            val categoryGas = getString(R.string.category_gas)
            val categoryWater = getString(R.string.category_water)

            when (item.itemId) {
                R.id.nav_total -> {
                    activeCategory = categoryTotal
                    inputSection.visibility = View.GONE
                    summaryDivider.visibility = View.GONE // ★ 合計タブでは非表示
                }
                R.id.nav_electricity -> {
                    activeCategory = categoryElectricity
                    inputCategory = activeCategory
                    inputSection.visibility = View.VISIBLE
                    summaryDivider.visibility = View.VISIBLE // ★ 他タブでは表示
                }
                R.id.nav_gas -> {
                    activeCategory = categoryGas
                    inputCategory = activeCategory
                    inputSection.visibility = View.VISIBLE
                    summaryDivider.visibility = View.VISIBLE // ★ 他タブでは表示
                }
                R.id.nav_water -> {
                    activeCategory = categoryWater
                    inputCategory = activeCategory
                    inputSection.visibility = View.VISIBLE
                    summaryDivider.visibility = View.VISIBLE // ★ 他タブでは表示
                }
            }
            textViewInputLabel.text = activeCategory
            observeBills()
            true
        }
    }

    private fun observeBills() {
        lifecycleScope.launch {
            billDao.getAllBills().collect { bills ->
                val listToShow = if (activeCategory == getString(R.string.category_total)) {
                    bills
                } else {
                    bills.filter { it.category == activeCategory }
                }
                billAdapter.submitList(listToShow)
                updateChart(bills)
                updateSummary(bills)
            }
        }
    }

    private fun setupRecyclerView() {
        billAdapter = UtilityBillAdapter { bill, position ->
            showEditOrDeleteDialog(bill, position)
        }
        recyclerViewBills.adapter = billAdapter
        recyclerViewBills.layoutManager = LinearLayoutManager(this)

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val billToDelete = billAdapter.currentList[position]
                    showDeleteConfirmationDialog(billToDelete, position, fromSwipe = true)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerViewBills)
    }

    private fun saveBillData() {
        val bill = createBillFromInput() ?: return
        lifecycleScope.launch {
            billDao.insert(bill)
            Toast.makeText(this@MainActivity, getString(R.string.toast_saved_successfully), Toast.LENGTH_SHORT).show()
            clearInputFields()
        }
    }

    private fun updateBillData(bill: UtilityBill) {
        lifecycleScope.launch {
            billDao.update(bill)
            Toast.makeText(this@MainActivity, getString(R.string.toast_updated), Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteBillData(bill: UtilityBill) {
        lifecycleScope.launch {
            billDao.delete(bill)
            Toast.makeText(this@MainActivity, getString(R.string.toast_deleted), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSummary(bills: List<UtilityBill>) {
        if (bills.isEmpty()) {
            textViewMonthlyTotal.visibility = View.GONE
            return
        }
        textViewMonthlyTotal.visibility = View.VISIBLE
        val latestBill = bills.first()
        val latestYear = latestBill.year
        val latestMonth = latestBill.month
        val totalForMonth = bills.filter { it.year == latestYear && it.month == latestMonth }.sumOf { it.amount }
        val formatter = NumberFormat.getCurrencyInstance(Locale.JAPAN)
        textViewMonthlyTotal.text = getString(R.string.monthly_total_format, latestYear, latestMonth, formatter.format(totalForMonth.toLong()))
    }

    private fun clearInputFields() {
        editTextAmount.text.clear()
        val cal = Calendar.getInstance()
        currentYear = cal.get(Calendar.YEAR)
        currentMonth = cal.get(Calendar.MONTH) + 1
        updateDateTextViews()
    }

    private fun showYearPickerDialog() {
        val numberPicker = NumberPicker(this)
        numberPicker.minValue = currentYear - 10
        numberPicker.maxValue = currentYear + 10
        numberPicker.value = currentYear
        MaterialAlertDialogBuilder(this)
            .setTitle("年を選択")
            .setView(numberPicker)
            .setPositiveButton("OK") { _, _ ->
                currentYear = numberPicker.value
                updateDateTextViews()
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .show()
    }

    private fun showMonthPickerDialog() {
        val numberPicker = NumberPicker(this)
        numberPicker.minValue = 1
        numberPicker.maxValue = 12
        numberPicker.value = currentMonth
        MaterialAlertDialogBuilder(this)
            .setTitle("月を選択")
            .setView(numberPicker)
            .setPositiveButton("OK") { _, _ ->
                currentMonth = numberPicker.value
                updateDateTextViews()
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .show()
    }

    private fun showEditOrDeleteDialog(bill: UtilityBill, position: Int) {
        val options = arrayOf(getString(R.string.dialog_edit_title), getString(R.string.button_delete))
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_select_action_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditDialog(bill)
                    1 -> showDeleteConfirmationDialog(bill, position)
                }
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .show()
    }

    private fun showDeleteConfirmationDialog(bill: UtilityBill, position: Int, fromSwipe: Boolean = false) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_dialog_title))
            .setMessage(getString(R.string.delete_confirmation_message, bill.year, bill.month, bill.category))
            .setNegativeButton(getString(R.string.button_cancel)) { dialog, _ ->
                if (fromSwipe) { billAdapter.notifyItemChanged(position) }
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.button_delete)) { _, _ -> deleteBillData(bill) }
            .setCancelable(false)
            .show()
    }

    private fun showEditDialog(bill: UtilityBill) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_bill, null)
        val editRadioGroup = dialogView.findViewById<RadioGroup>(R.id.editRadioGroup)
        val editNumberPickerYear = dialogView.findViewById<NumberPicker>(R.id.editNumberPickerYear)
        val editNumberPickerMonth = dialogView.findViewById<NumberPicker>(R.id.editNumberPickerMonth)
        val editAmount = dialogView.findViewById<EditText>(R.id.editAmount)

        when (bill.category) {
            getString(R.string.category_electricity) -> editRadioGroup.check(R.id.radio_electricity)
            getString(R.string.category_gas) -> editRadioGroup.check(R.id.radio_gas)
            getString(R.string.category_water) -> editRadioGroup.check(R.id.radio_water)
        }
        val cal = Calendar.getInstance().get(Calendar.YEAR)
        editNumberPickerYear.minValue = cal - 10
        editNumberPickerYear.maxValue = cal + 10
        editNumberPickerYear.value = bill.year
        editNumberPickerMonth.minValue = 1
        editNumberPickerMonth.maxValue = 12
        editNumberPickerMonth.value = bill.month
        editAmount.setText(bill.amount.toString())

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_edit_title))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.button_cancel), null)
            .setPositiveButton(getString(R.string.button_save)) { _, _ ->
                val updatedBill = createBillFromInput(isEdit = true, dialogView = dialogView, originalId = bill.id)
                updatedBill?.let { updateBillData(it) }
            }
            .show()
    }

    private fun setupChart() {
        barChart.description.isEnabled = false
        barChart.setDrawGridBackground(false)
        barChart.setDrawBarShadow(false)
        barChart.legend.isEnabled = false
        barChart.animateY(1000)
        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        barChart.axisLeft.setDrawGridLines(false)
        barChart.axisLeft.axisMinimum = 0f
        barChart.axisRight.isEnabled = false

        barChart.setScaleEnabled(false)
        barChart.setPinchZoom(false)
        barChart.isDoubleTapToZoomEnabled = false

        barChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                if (e == null || activeCategory == getString(R.string.category_total)) {
                    return
                }

                val bill = e.data as? UtilityBill ?: return
                val position = billAdapter.currentList.indexOf(bill)
                if (position != -1) {
                    showEditOrDeleteDialog(bill, position)
                }
            }

            override fun onNothingSelected() {}
        })
    }

    private fun updateChart(bills: List<UtilityBill>) {
        if (bills.isEmpty()) {
            barChart.data = null
            barChart.visibility = View.GONE
            barChart.invalidate()
            return
        }
        barChart.visibility = View.VISIBLE

        val allMonthKeys = bills
            .map { "${it.year}-${it.month.toString().padStart(2, '0')}" }
            .distinct()
            .sortedDescending()
            .take(6)
            .reversed()

        if (allMonthKeys.isEmpty()) {
            barChart.data = null
            barChart.visibility = View.GONE
            barChart.invalidate()
            return
        }

        val barData: BarData

        if (activeCategory == getString(R.string.category_total)) {
            val entries = ArrayList<BarEntry>()
            val categoryElectricity = getString(R.string.category_electricity)
            val categoryGas = getString(R.string.category_gas)
            val categoryWater = getString(R.string.category_water)

            allMonthKeys.forEachIndexed { index, monthKey ->
                val billsForMonth = bills.filter { "${it.year}-${it.month.toString().padStart(2, '0')}" == monthKey }
                val stackValues = floatArrayOf(
                    billsForMonth.filter { it.category == categoryElectricity }.sumOf { it.amount }.toFloat(),
                    billsForMonth.filter { it.category == categoryGas }.sumOf { it.amount }.toFloat(),
                    billsForMonth.filter { it.category == categoryWater }.sumOf { it.amount }.toFloat()
                )
                entries.add(BarEntry(index.toFloat(), stackValues))
            }

            val dataSet = BarDataSet(entries, "")
            dataSet.stackLabels = arrayOf(categoryElectricity, categoryGas, categoryWater)
            dataSet.colors = listOf(
                ContextCompat.getColor(this, R.color.purple_200),
                ContextCompat.getColor(this, R.color.teal_200),
                ContextCompat.getColor(this, R.color.design_default_color_primary)
            )
            dataSet.valueTextSize = 10f
            dataSet.isHighlightEnabled = false

            barData = BarData(dataSet)
            barChart.legend.isEnabled = true

        } else {
            val entries = ArrayList<BarEntry>()
            val billsForCategory = bills.filter { it.category == activeCategory }

            allMonthKeys.forEachIndexed { index, monthKey ->
                val billForMonth = billsForCategory.find { "${it.year}-${it.month.toString().padStart(2, '0')}" == monthKey }
                val total = billForMonth?.amount?.toFloat() ?: 0f
                entries.add(BarEntry(index.toFloat(), total, billForMonth))
            }

            val dataSet = BarDataSet(entries, activeCategory)
            dataSet.color = when(activeCategory) {
                getString(R.string.category_electricity) -> ContextCompat.getColor(this, R.color.purple_200)
                getString(R.string.category_gas) -> ContextCompat.getColor(this, R.color.teal_200)
                getString(R.string.category_water) -> ContextCompat.getColor(this, R.color.design_default_color_primary)
                else -> ContextCompat.getColor(this, R.color.black)
            }
            dataSet.valueTextSize = 10f
            dataSet.highLightAlpha = 0

            barData = BarData(dataSet)
            barChart.legend.isEnabled = false
        }

        barData.barWidth = 0.5f
        barChart.data = barData

        val labels = allMonthKeys.map { "${it.split('-')[1].toInt()}月" }
        val xAxis = barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.labelCount = labels.size
        xAxis.setCenterAxisLabels(false)

        barChart.invalidate()
    }

    private fun createBillFromInput(isEdit: Boolean = false, dialogView: View? = null, originalId: Int = 0): UtilityBill? {
        val category: String
        val year: Int
        val month: Int
        val amountStr: String
        if (isEdit && dialogView != null) {
            val radioGroup = dialogView.findViewById<RadioGroup>(R.id.editRadioGroup)
            category = when (radioGroup.checkedRadioButtonId) {
                R.id.radio_electricity -> getString(R.string.category_electricity)
                R.id.radio_gas -> getString(R.string.category_gas)
                R.id.radio_water -> getString(R.string.category_water)
                else -> ""
            }
            year = dialogView.findViewById<NumberPicker>(R.id.editNumberPickerYear).value
            month = dialogView.findViewById<NumberPicker>(R.id.editNumberPickerMonth).value
            amountStr = dialogView.findViewById<EditText>(R.id.editAmount).text.toString()
        } else {
            category = inputCategory
            year = currentYear
            month = currentMonth
            amountStr = editTextAmount.text.toString()
        }

        if (category.isBlank() || amountStr.isBlank()) {
            Toast.makeText(this, getString(R.string.toast_fill_all_fields), Toast.LENGTH_SHORT).show()
            return null
        }
        val amount = amountStr.toIntOrNull()
        if (amount == null || amount < 0) {
            Toast.makeText(this, getString(R.string.toast_invalid_amount), Toast.LENGTH_SHORT).show()
            return null
        }

        return UtilityBill(id = if (isEdit) originalId else 0, category = category, year = year, month = month, amount = amount)
    }
}