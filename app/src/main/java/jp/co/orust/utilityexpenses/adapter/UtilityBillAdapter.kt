package jp.co.orust.utilityexpenses.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.orust.utilityexpenses.R
import jp.co.orust.utilityexpenses.data.UtilityBill
import java.text.NumberFormat
import java.util.Locale

class UtilityBillAdapter(private val onItemClicked: (UtilityBill, Int) -> Unit) : // ラムダの引数を変更
    ListAdapter<UtilityBill, UtilityBillAdapter.UtilityBillViewHolder>(UtilityBillsComparator()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UtilityBillViewHolder {
        val holder = UtilityBillViewHolder.create(parent)
        holder.itemView.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                onItemClicked(getItem(position), position) // itemとpositionを渡す
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: UtilityBillViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current)
    }

    class UtilityBillViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateTextView: TextView = itemView.findViewById(R.id.textViewDate)
        private val categoryTextView: TextView = itemView.findViewById(R.id.textViewCategory)
        private val amountTextView: TextView = itemView.findViewById(R.id.textViewAmount)

        fun bind(bill: UtilityBill) {
            dateTextView.text = itemView.context.getString(R.string.date_format, bill.year, bill.month)
            categoryTextView.text = bill.category
            val formatter = NumberFormat.getCurrencyInstance(Locale.JAPAN)
            amountTextView.text = formatter.format(bill.amount.toLong())
        }


        companion object {
            fun create(parent: ViewGroup): UtilityBillViewHolder {
                val view: View = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_utility_bill, parent, false)
                return UtilityBillViewHolder(view)
            }
        }
    }

    class UtilityBillsComparator : DiffUtil.ItemCallback<UtilityBill>() {
        override fun areItemsTheSame(oldItem: UtilityBill, newItem: UtilityBill): Boolean {
            return oldItem.id == newItem.id
        }
        override fun areContentsTheSame(oldItem: UtilityBill, newItem: UtilityBill): Boolean {
            return oldItem == newItem
        }
    }
}