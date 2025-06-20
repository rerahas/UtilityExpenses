package jp.co.orust.utilityexpenses.data



import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "utility_bills")
data class UtilityBill(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val category: String, // "電気", "ガス", "水道"
    val year: Int,
    val month: Int,
    val amount: Int // 金額
)