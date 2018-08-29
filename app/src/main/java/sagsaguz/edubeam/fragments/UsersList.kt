package sagsaguz.edubeam.fragments

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.SwitchCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import sagsaguz.edubeam.CustomerActivitiesActivity
import sagsaguz.edubeam.R
import sagsaguz.edubeam.adapter.UserListAdapter
import java.util.*
import kotlin.collections.ArrayList
import android.text.Editable
import android.text.TextWatcher
import com.amazonaws.AmazonClientException
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import sagsaguz.edubeam.MainActivity.Companion.mainActivity
import sagsaguz.edubeam.utils.AWSProvider
import sagsaguz.edubeam.utils.LeadsDO

class UsersList : Fragment() {

    lateinit var swFilter : SwitchCompat
    lateinit var ivSearch : ImageView
    lateinit var rlSearch : RelativeLayout
    lateinit var etSearchUser : EditText
    lateinit var cancelSearch : TextView

    val fCustomerList = ArrayList<LeadsDO>()
    val sCustomerList = ArrayList<LeadsDO>()

    lateinit var user : UserListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val view = inflater.inflate(R.layout.user_list, container, false)

        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
        val listView = view.findViewById<ListView>(R.id.user_list_fragment)

        if (uCustomersList.isEmpty()) {
            listView.visibility = View.GONE
            tvMessage.visibility = View.VISIBLE
        } else {
            tvMessage.visibility = View.GONE
            listView.visibility = View.VISIBLE
        }

        Collections.sort(uCustomersList, CustomerNameComparator())

        user = UserListAdapter(this.context!!, fCustomerList)
        listView.adapter = user

        filterByRating()

        listView.setOnItemClickListener { parent, view, position, id ->
            val intent = Intent(context, CustomerActivitiesActivity::class.java)
            intent.putExtra("lead", fCustomerList[position])
            startActivity(intent)
        }

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { adapterView, view, pos, l ->
            deleteUserDialog(fCustomerList[pos])
        }

        swFilter = view.findViewById<SwitchCompat>(R.id.swFilter)
        swFilter.setOnCheckedChangeListener { compoundButton, checked ->
            if (checked) {
                filterByDate()
            } else {
                filterByRating()
            }
        }

        rlSearch = view.findViewById(R.id.rlSearch)
        etSearchUser = view.findViewById(R.id.etSearchUser)
        cancelSearch = view.findViewById(R.id.cancelSearch)

        ivSearch = view.findViewById(R.id.ivSearch)
        ivSearch.setOnClickListener {
            rlSearch.visibility = View.VISIBLE
        }

        cancelSearch.setOnClickListener {
            etSearchUser.text.clear()
            rlSearch.visibility = View.GONE
        }

        etSearchUser.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) {
                val text = etSearchUser.text.toString().toLowerCase()
                val newList = ArrayList<LeadsDO>()
                fCustomerList.clear()
                newList.addAll(sCustomerList)
                for (str in newList) {
                    val name = str.name.toString().toLowerCase()
                    if (name.contains(text))
                        fCustomerList.add(str)
                }
                user.notifyDataSetChanged()
            }
            override fun afterTextChanged(editable: Editable) {}
        })

        return view
    }

    companion object {

        var uCustomersList = ArrayList<LeadsDO>()

        fun newInstance(customersList: ArrayList<LeadsDO>): UsersList {
            val args = Bundle()
            val fragment = UsersList()
            fragment.arguments = args
            this.uCustomersList = customersList
            return fragment
        }

    }

    private fun deleteUserDialog(customerDetails: LeadsDO): Boolean {
        val dialog = Dialog(this.context)
        dialog.setContentView(R.layout.conformation_dialog)
        dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)
        val tvCSMS = dialog.findViewById<TextView>(R.id.tvCSMS)
        tvCSMS.text = "Are you sure, you want to delete "+customerDetails.name+ " details?"
        val tvYes = dialog.findViewById<TextView>(R.id.tvYes)
        tvYes.setOnClickListener {
            dialog.dismiss()
            DeleteUser().execute(customerDetails)
        }
        val tvNo = dialog.findViewById<TextView>(R.id.tvNo)
        tvNo.setOnClickListener { dialog.dismiss() }

        dialog.show()
        return true
    }

    private fun filterByRating(){
        fCustomerList.clear()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Collections.sort(uCustomersList, CustomerRatingComparator().thenComparing(CustomerNameComparator()))
        } else {
            Collections.sort(uCustomersList, CustomerRatingComparator())
        }

        for (i in 0 until uCustomersList.size){
            if (uCustomersList[i].assignedTo != "Not Assigned")
                fCustomerList.add(uCustomersList[i])
        }

        sCustomerList.clear()
        sCustomerList.addAll(fCustomerList)

        user.notifyDataSetChanged()
    }

    private fun filterByDate(){
        fCustomerList.clear()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Collections.sort(uCustomersList, CustomerDateComparator().thenComparing(CustomerNameComparator()))
        } else {
            Collections.sort(uCustomersList, CustomerRatingComparator())
        }

        for (i in 0 until uCustomersList.size){
            if (uCustomersList[i].assignedTo != "Not Assigned")
                fCustomerList.add(uCustomersList[i])
        }

        sCustomerList.clear()
        sCustomerList.addAll(fCustomerList)

        user.notifyDataSetChanged()
    }

    inner class CustomerDateComparator : Comparator<LeadsDO> {
        override fun compare(obj1: LeadsDO, obj2: LeadsDO): Int {
            return obj2.createdDate!!.compareTo(obj1.createdDate!!)
        }
    }

    inner class CustomerRatingComparator : Comparator<LeadsDO> {
        override fun compare(obj1: LeadsDO, obj2: LeadsDO): Int {
            return obj2.rating!!.compareTo(obj1.rating!!)
        }
    }

    inner class CustomerNameComparator : Comparator<LeadsDO> {

        override fun compare(obj1: LeadsDO, obj2: LeadsDO): Int {
            return obj1.name!!.compareTo(obj2.name!!)
        }
    }


    @SuppressLint("StaticFieldLeak")
    inner class DeleteUser : AsyncTask<LeadsDO, Void, Boolean>() {

        private var userDetails = LeadsDO()
        private val progressDialog = ProgressDialog(mainActivity, R.style.MyAlertDialogStyle)

        override fun onPreExecute() {
            progressDialog.setMessage("Deleting user details, Please wait...")
            progressDialog.setCancelable(false)
            progressDialog.show()
        }

        override fun doInBackground(vararg cDetails: LeadsDO?): Boolean {
            userDetails = cDetails[0]!!

            val awsProvider = AWSProvider()
            val dynamoDBClient = AmazonDynamoDBClient(awsProvider.getCredentialsProvider(mainActivity!!))
            dynamoDBClient.setRegion(Region.getRegion(Regions.US_EAST_1))
            val dynamoDBMapper = DynamoDBMapper.builder()
                    .dynamoDBClient(dynamoDBClient)
                    .awsConfiguration(AWSMobileClient.getInstance().configuration)
                    .build()

            try {
                dynamoDBMapper.delete(userDetails)
                return true
            } catch (e : AmazonClientException){
                return false
            }
        }

        override fun onPostExecute(result: Boolean?) {
            progressDialog.dismiss()
            if (result!!){
                mainActivity!!.refreshOnUpdate()
            } else {
                Toast.makeText(mainActivity, "Please try again", Toast.LENGTH_SHORT).show()
            }
        }

    }

}