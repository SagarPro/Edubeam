package sagsaguz.edubeam.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast

class AlarmReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        Toast.makeText(context, "Background Working", Toast.LENGTH_SHORT).show()
        Log.d("alarm", "Background Working")

        /*val phone = "7259980952"
        val message = "Hello"
        SmsManager.getDefault().sendTextMessage(phone, null, message, null, null)
        Toast.makeText(context, "SMS sent", Toast.LENGTH_SHORT).show()*/

    }

}
