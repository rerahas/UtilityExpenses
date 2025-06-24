package jp.co.orust.utilityexpenses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
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
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
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

    private lateinit var billAdapter: UtilityBillAdapter
    private var selectedCategory: String = ""

    private var currentYear: Int = 0
    private var currentMonth: Int = 0

    // Database
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val billDao by lazy { database.utilityBillDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize all UI components
        setupViews()
        setupBottomNavigation()
        setupRecyclerView()
        setupChart()
        observeBills() // Start observing data from the database

        buttonSave.setOnClickListener {
            saveBillData()
        }
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

        // Pre-fill with current year and month
        val cal = Calendar.getInstance()
        currentYear = cal.get(Calendar.YEAR)
        currentMonth = cal.get(Calendar.MONTH) + 1

        updateDateTextViews()

        textViewYear.setOnClickListener {
            showYearPickerDialog()
        }

        textViewMonth.setOnClickListener {
            showMonthPickerDialog()
        }
    }

    private fun updateDateTextViews() {
        textViewYear.text = getString(R.string.year_format, currentYear)
        textViewMonth.text = getString(R.string.month_format, currentMonth)
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
            .setNegativeButton("キャンセル", null)
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
            .setNegativeButton("キャンセル", null)
            .show()
    }


    private fun setupBottomNavigation() {
        // Set default category
        selectedCategory = getString(R.string.category_electricity)
        textViewInputLabel.text = selectedCategory

        bottomNavigationView.setOnItemSelectedListener { item ->
            selectedCategory = when (item.itemId) {
                R.id.nav_electricity -> getString(R.string.category_electricity)
                R.id.nav_gas -> getString(R.string.category_gas)
                R.id.nav_water -> getString(R.string.category_water)
                else -> ""
            }
            textViewInputLabel.text = selectedCategory
            true
        }
    }

    private fun setupRecyclerView() {
        // Pass a lambda function to handle item clicks for editing
        billAdapter = UtilityBillAdapter { bill ->
            showEditDialog(bill)
        }
        recyclerViewBills.adapter = billAdapter
        recyclerViewBills.layoutManager = LinearLayoutManager(this)

        // Add swipe-to-delete functionality
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false // We don't use drag-and-drop

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                // Check if the position is valid
                if (position != RecyclerView.NO_POSITION) {
                    val billToDelete = billAdapter.currentList[position]
                    showDeleteConfirmationDialog(billToDelete, position) // Pass position here
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerViewBills)
    }

    private fun observeBills() {
        lifecycleScope.launch {
            billDao.getAllBills().collect { bills ->
                billAdapter.submitList(bills)
                updateSummary(bills)
                updateChart(bills)
            }
        }
    }

    // --- Data Manipulation ---

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

    // --- UI Update ---

    private fun updateSummary(bills: List<UtilityBill>) {
        if (bills.isEmpty()) {
            textViewMonthlyTotal.visibility = View.GONE
            return
        }

        textViewMonthlyTotal.visibility = View.VISIBLE

        val latestBill = bills.first()
        val latestYear = latestBill.year
        val latestMonth = latestBill.month

        val totalForMonth = bills.filter { it.year == latestYear && it.month == latestMonth }
            .sumOf { it.amount }

        val formatter = NumberFormat.getCurrencyInstance(Locale.JAPAN)
        textViewMonthlyTotal.text = getString(
            R.string.monthly_total_format,
            latestYear,
            latestMonth,
            formatter.format(totalForMonth)
        )
    }

    private fun clearInputFields() {
        editTextAmount.text.clear()
        // Reset pickers to current date
        val cal = Calendar.getInstance()
        currentYear = cal.get(Calendar.YEAR)
        currentMonth = cal.get(Calendar.MONTH) + 1
        updateDateTextViews()
    }

    // --- Dialogs ---

    private fun showDeleteConfirmationDialog(bill: UtilityBill, position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_dialog_title))
            .setMessage(getString(R.string.delete_confirmation_message, bill.year, bill.month, bill.category))
            .setNegativeButton(getString(R.string.button_cancel)) { dialog, _ ->
                // User cancelled the action, notify adapter to revert the swipe
                billAdapter.notifyItemChanged(position) // Use the safe position
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.button_delete)) { _, _ ->
                deleteBillData(bill)
            }
            .setCancelable(false)
            .show()
    }

    private fun showEditDialog(bill: UtilityBill) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_bill, null)
        val editRadioGroup = dialogView.findViewById<RadioGroup>(R.id.editRadioGroup)
        val editNumberPickerYear = dialogView.findViewById<NumberPicker>(R.id.editNumberPickerYear)
        val editNumberPickerMonth = dialogView.findViewById<NumberPicker>(R.id.editNumberPickerMonth)
        val editAmount = dialogView.findViewById<EditText>(R.id.editAmount)

        // Pre-fill data
        when (bill.category) {
            getString(R.string.category_electricity) -> editRadioGroup.check(R.id.radio_electricity)
            getString(R.string.category_gas) -> editRadioGroup.check(R.id.radio_gas)
            getString(R.string.category_water) -> editRadioGroup.check(R.id.radio_water)
        }


        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        editNumberPickerYear.minValue = currentYear - 10
        editNumberPickerYear.maxValue = currentYear + 10
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
                // Create an updated bill object from dialog input
                val updatedBill = createBillFromInput(
                    isEdit = true,
                    dialogView = dialogView,
                    originalId = bill.id
                )
                updatedBill?.let { updateBillData(it) }
            }
            .show()
    }

    // --- Chart ---

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
    }

    private fun updateChart(bills: List<UtilityBill>) {
        if (bills.size < 2) { // Need at least 2 entries to show a meaningful chart
            barChart.visibility = View.GONE
            return
        }
        barChart.visibility = View.VISIBLE

        // Group bills by month (e.g., "2025-05") and sum their amounts
        val monthlyTotals = bills
            .groupBy { "${it.year}-${it.month.toString().padStart(2, '0')}" }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toSortedMap(compareByDescending { it }) // Sort by most recent month first
            .toList()
            .take(6) // Take the last 6 months
            .reversed() // Reverse to show oldest to newest

        if (monthlyTotals.isEmpty()) {
            barChart.clear()
            barChart.invalidate()
            return
        }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        monthlyTotals.forEachIndexed { index, (monthKey, total) ->
            entries.add(BarEntry(index.toFloat(), total.toFloat()))
            // Format label e.g., "5月"
            labels.add("${monthKey.split('-')[1].toInt()}月")
        }

        val dataSet = BarDataSet(entries, "月次支出")
        dataSet.color = ContextCompat.getColor(this, R.color.design_default_color_primary)
        dataSet.valueTextSize = 10f

        val barData = BarData(dataSet)
        barData.barWidth = 0.5f

        barChart.data = barData
        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        barChart.xAxis.labelCount = labels.size
        barChart.invalidate() // Refresh chart
    }


    // --- Helper function to reduce code duplication ---
    private fun createBillFromInput(
        isEdit: Boolean = false,
        dialogView: View? = null,
        originalId: Int = 0
    ): UtilityBill? {
        val category: String
        val year: Int
        val month: Int
        val amountStr: String

        if (isEdit && dialogView != null) {
            val radioGroup = dialogView.findViewById<RadioGroup>(R.id.editRadioGroup)
            category = when(radioGroup.checkedRadioButtonId) {
                R.id.radio_electricity -> getString(R.string.category_electricity)
                R.id.radio_gas -> getString(R.string.category_gas)
                R.id.radio_water -> getString(R.string.category_water)
                else -> ""
            }

            year = dialogView.findViewById<NumberPicker>(R.id.editNumberPickerYear).value
            month = dialogView.findViewById<NumberPicker>(R.id.editNumberPickerMonth).value
            amountStr = dialogView.findViewById<EditText>(R.id.editAmount).text.toString()
        } else {
            category = selectedCategory
            year = currentYear
            month = currentMonth
            amountStr = editTextAmount.text.toString()
        }

        // Validation
        if (amountStr.isBlank()) {
            Toast.makeText(this, getString(R.string.toast_fill_all_fields), Toast.LENGTH_SHORT).show()
            return null
        }
        val amount = amountStr.toIntOrNull()

        // Year and month validation is implicitly handled by NumberPicker's range.
        if (amount == null || amount < 0) {
            Toast.makeText(this, getString(R.string.toast_invalid_amount), Toast.LENGTH_SHORT).show()
            return null
        }

        return UtilityBill(
            id = if (isEdit) originalId else 0,
            category = category,
            year = year,
            month = month,
            amount = amount
        )
    }
}