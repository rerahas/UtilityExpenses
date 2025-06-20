package jp.co.orust.utilityexpenses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.co.orust.utilityexpenses.adapter.UtilityBillAdapter
import jp.co.orust.utilityexpenses.data.AppDatabase
import jp.co.orust.utilityexpenses.data.UtilityBill
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var spinnerCategory: Spinner
    private lateinit var editTextYear: EditText
    private lateinit var editTextMonth: EditText
    private lateinit var editTextAmount: EditText
    private lateinit var buttonSave: Button
    private lateinit var recyclerViewBills: RecyclerView
    private lateinit var barChart: BarChart
    private lateinit var textViewMonthlyTotal: TextView
    private lateinit var textViewSummaryTitle: TextView

    private lateinit var billAdapter: UtilityBillAdapter

    // Database
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val billDao by lazy { database.utilityBillDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize all UI components
        setupViews()
        setupSpinner()
        setupRecyclerView()
        setupChart()
        observeBills() // Start observing data from the database

        buttonSave.setOnClickListener {
            saveBillData()
        }
    }

    private fun setupViews() {
        spinnerCategory = findViewById(R.id.spinnerCategory)
        editTextYear = findViewById(R.id.editTextYear)
        editTextMonth = findViewById(R.id.editTextMonth)
        editTextAmount = findViewById(R.id.editTextAmount)
        buttonSave = findViewById(R.id.buttonSave)
        recyclerViewBills = findViewById(R.id.recyclerViewBills)
        barChart = findViewById(R.id.barChart)
        textViewMonthlyTotal = findViewById(R.id.textViewMonthlyTotal)
        textViewSummaryTitle = findViewById(R.id.textViewSummaryTitle)

        // Pre-fill with current year and month
        val cal = Calendar.getInstance()
        editTextYear.setText(cal.get(Calendar.YEAR).toString())
        editTextMonth.setText((cal.get(Calendar.MONTH) + 1).toString())
    }

    private fun setupSpinner() {
        ArrayAdapter.createFromResource(
            this,
            R.array.utility_categories,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategory.adapter = adapter
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
                val billToDelete = billAdapter.currentList[position]
                showDeleteConfirmationDialog(billToDelete)
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
            textViewSummaryTitle.visibility = View.GONE
            textViewMonthlyTotal.visibility = View.GONE
            return
        }

        textViewSummaryTitle.visibility = View.VISIBLE
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
        spinnerCategory.setSelection(0)
    }

    // --- Dialogs ---

    private fun showDeleteConfirmationDialog(bill: UtilityBill) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_confirmation))
            .setMessage("${bill.year}年${bill.month}月 (${bill.category}) のデータを削除します。")
            .setNegativeButton(getString(R.string.button_cancel)) { dialog, _ ->
                // User cancelled the action, notify adapter to revert the swipe
                billAdapter.notifyItemChanged(billAdapter.currentList.indexOf(bill))
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
        val editSpinnerCategory = dialogView.findViewById<Spinner>(R.id.editSpinnerCategory)
        val editYear = dialogView.findViewById<EditText>(R.id.editYear)
        val editMonth = dialogView.findViewById<EditText>(R.id.editMonth)
        val editAmount = dialogView.findViewById<EditText>(R.id.editAmount)

        // Setup spinner for the dialog
        ArrayAdapter.createFromResource(
            this, R.array.utility_categories, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            editSpinnerCategory.adapter = adapter
            // Set current category
            val categoryPosition = adapter.getPosition(bill.category)
            editSpinnerCategory.setSelection(categoryPosition)
        }

        // Pre-fill data
        editYear.setText(bill.year.toString())
        editMonth.setText(bill.month.toString())
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
        val yearStr: String
        val monthStr: String
        val amountStr: String

        if (isEdit && dialogView != null) {
            category = dialogView.findViewById<Spinner>(R.id.editSpinnerCategory).selectedItem.toString()
            yearStr = dialogView.findViewById<EditText>(R.id.editYear).text.toString()
            monthStr = dialogView.findViewById<EditText>(R.id.editMonth).text.toString()
            amountStr = dialogView.findViewById<EditText>(R.id.editAmount).text.toString()
        } else {
            category = spinnerCategory.selectedItem.toString()
            yearStr = editTextYear.text.toString()
            monthStr = editTextMonth.text.toString()
            amountStr = editTextAmount.text.toString()
        }

        // Validation
        if (yearStr.isBlank() || monthStr.isBlank() || amountStr.isBlank()) {
            Toast.makeText(this, getString(R.string.toast_fill_all_fields), Toast.LENGTH_SHORT).show()
            return null
        }
        val year = yearStr.toIntOrNull()
        val month = monthStr.toIntOrNull()
        val amount = amountStr.toIntOrNull()
        if (year == null || year < 2000 || year > 2100) {
            Toast.makeText(this, getString(R.string.toast_invalid_year), Toast.LENGTH_SHORT).show()
            return null
        }
        if (month == null || month !in 1..12) {
            Toast.makeText(this, getString(R.string.toast_invalid_month), Toast.LENGTH_SHORT).show()
            return null
        }
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
