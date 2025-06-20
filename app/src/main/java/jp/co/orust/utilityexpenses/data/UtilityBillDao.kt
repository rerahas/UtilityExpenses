package jp.co.orust.utilityexpenses.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UtilityBillDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bill: UtilityBill)

    @Update
    suspend fun update(bill: UtilityBill)

    @Delete
    suspend fun delete(bill: UtilityBill)

    @Query("SELECT * FROM utility_bills ORDER BY year DESC, month DESC")
    fun getAllBills(): Flow<List<UtilityBill>>
}
