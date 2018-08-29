package sagsaguz.edubeam.fragments

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.ProgressDialog
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.amazonaws.AmazonClientException
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.ScanRequest
import com.amazonaws.services.dynamodbv2.model.ScanResult
import sagsaguz.edubeam.MainActivity
import sagsaguz.edubeam.MainActivity.Companion.mainActivity
import sagsaguz.edubeam.R
import sagsaguz.edubeam.adapter.UserListAdapter
import sagsaguz.edubeam.utils.AWSProvider
import sagsaguz.edubeam.utils.AgentsDO
import sagsaguz.edubeam.utils.Config
import sagsaguz.edubeam.utils.LeadsDO
import java.util.*

class NewList : Fragment() {

    lateinit var tvMessage: TextView
    lateinit var lvNew: ListView

    lateinit var user : UserListAdapter
    lateinit var selAgentName : String
    lateinit var selLeadType : String

    val fCustomerList = ArrayList<LeadsDO>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val view = inflater.inflate(R.layout.today_list, container, false)

        tvMessage = view.findViewById(R.id.tvMessage)
        tvMessage.text = "Not found new Leads"
        lvNew = view.findViewById(R.id.today_list_fragment)

        user = UserListAdapter(this.context!!, fCustomerList)
        lvNew.adapter = user

        filterByName()

        lvNew.setOnItemClickListener { parent, view1, position, id ->
            agentsDialog((activity as MainActivity).getAgents(), fCustomerList[position])
        }

        return view
    }

    companion object {

        var nCustomersList = ArrayList<LeadsDO>()

        fun newInstance(customersList: ArrayList<LeadsDO>): NewList {
            val args = Bundle()
            val fragment = NewList()
            fragment.arguments = args
            this.nCustomersList = customersList
            return fragment
        }
    }

    private fun agentsDialog(agentList: ArrayList<AgentsDO>, customer: LeadsDO){
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.list_view_dialog)
        dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvTitle)
        tvTitle.text = "Assign Agent"
        val lvAgents = dialog.findViewById<ListView>(R.id.lvItems)
        val agentName = ArrayList<String>()
        Collections.sort(agentList, AgentsNameComparator())
        for (i in 0 until agentList.size){
            if (!agentName.contains(agentList[i].name.toString()))
                agentName.add(agentList[i].name.toString())
        }
        if (agentName.contains("Head Office"))
            agentName.remove("Head Office")

        val agentsListAdapter = MainActivity.AgentsListAdapter(context!!, agentName)
        lvAgents.adapter = agentsListAdapter

        lvAgents.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            val dialog2 = Dialog(context)
            dialog2.setContentView(R.layout.conformation_dialog)
            dialog2.window!!.setBackgroundDrawableResource(android.R.color.transparent)
            dialog2.setCancelable(false)
            val tvCSMS = dialog2.findViewById<TextView>(R.id.tvCSMS)
            tvCSMS.text = "Are you sure, you want to assign " + agentName[position] + " to " + customer.name + "?..."
            val tvYes = dialog2.findViewById<TextView>(R.id.tvYes)
            tvYes.setOnClickListener {
                dialog.dismiss()
                selAgentName = agentName[position]

                val dialog3 = Dialog(context)
                dialog3.setContentView(R.layout.conformation_dialog)
                dialog3.window!!.setBackgroundDrawableResource(android.R.color.transparent)
                val tvCSMS1 = dialog3.findViewById<TextView>(R.id.tvCSMS)
                tvCSMS1.text = "Is " + customer.name + " an Existing Customer or New Customer?"
                val tvYes1 = dialog3.findViewById<TextView>(R.id.tvYes)
                tvYes1.text = "New"
                tvYes1.setOnClickListener {
                    dialog3.dismiss()
                    dialog2.dismiss()
                    dialog.dismiss()
                    selLeadType = "New Preschool"
                    UpdateCustomerDetails().execute(customer)
                }
                val tvNo1 = dialog3.findViewById<TextView>(R.id.tvNo)
                tvNo1.text = "Upgrade"
                tvNo1.setOnClickListener {
                    dialog3.dismiss()
                    dialog2.dismiss()
                    dialog.dismiss()
                    selLeadType = "Upgrade Preschool"
                    UpdateCustomerDetails().execute(customer)
                }
                dialog3.show()

            }
            val tvNo = dialog2.findViewById<TextView>(R.id.tvNo)
            tvNo.setOnClickListener { dialog2.dismiss() }
            dialog2.show()
        }

        dialog.show()
    }

    private fun filterByName(){
        fCustomerList.clear()

        Collections.sort(nCustomersList, CustomerNameComparator())

        for (i in 0 until nCustomersList.size) {
            if (nCustomersList[i].assignedTo == "Not Assigned")
                fCustomerList.add(nCustomersList[i])
        }

        if (fCustomerList.isEmpty()) {
            lvNew.visibility = View.GONE
            tvMessage.visibility = View.VISIBLE
        } else {
            tvMessage.visibility = View.GONE
            lvNew.visibility = View.VISIBLE
        }

        user.notifyDataSetChanged()
    }

    inner class CustomerNameComparator : Comparator<LeadsDO> {
        override fun compare(obj1: LeadsDO, obj2: LeadsDO): Int {
            return obj1.name.toString().toLowerCase().compareTo(obj2.name.toString().toLowerCase())
        }
    }

    inner class AgentsNameComparator : Comparator<AgentsDO> {
        override fun compare(obj1: AgentsDO, obj2: AgentsDO): Int {
            return obj1.name.toString().toLowerCase().compareTo(obj2.name.toString().toLowerCase())
        }
    }


    @SuppressLint("StaticFieldLeak")
    inner class UpdateCustomerDetails : AsyncTask<LeadsDO, Void, Boolean>() {

        val progressDialog = ProgressDialog(context, R.style.MyAlertDialogStyle)

        override fun onPreExecute() {
            progressDialog.setMessage("Assigning agent, please wait...")
            progressDialog.setCancelable(false)
            progressDialog.show()
        }

        override fun doInBackground(vararg p0: LeadsDO): Boolean {

            val awsProvider = AWSProvider()
            val dynamoDBClient = AmazonDynamoDBClient(awsProvider.getCredentialsProvider(context!!))
            dynamoDBClient.setRegion(Region.getRegion(Regions.US_EAST_1))
            val dynamoDBMapper = DynamoDBMapper.builder()
                    .dynamoDBClient(dynamoDBClient)
                    .awsConfiguration(AWSMobileClient.getInstance().configuration)
                    .build()

            try {
                val lead = p0[0]
                lead.assignedTo = selAgentName
                lead.leadType = selLeadType
                dynamoDBMapper.save<LeadsDO>(lead)
                return true
            } catch (e: AmazonClientException) {
                return false
            }

        }

        override fun onPostExecute(result: Boolean) {
            progressDialog.dismiss()
            if (result){
                mainActivity!!.refreshOnUpdate()
            } else {
                (activity as MainActivity).basicSnackBar("Error occurred please try again.")
            }
        }
    }

}