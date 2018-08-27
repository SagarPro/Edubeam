package sagsaguz.edubeam

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Dialog
import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.*
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import android.support.design.widget.BaseTransientBottomBar
import android.support.design.widget.Snackbar
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewPager
import android.text.TextUtils
import android.text.method.KeyListener
import android.util.Log
import android.view.*
import android.widget.*
import com.amazonaws.AmazonClientException
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.ScanRequest
import com.amazonaws.services.dynamodbv2.model.ScanResult
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import sagsaguz.edubeam.fragments.PendingList
import sagsaguz.edubeam.alarm.AlarmReceiver
import sagsaguz.edubeam.fragments.NewList
import sagsaguz.edubeam.fragments.TodayList
import sagsaguz.edubeam.fragments.UsersList
import sagsaguz.edubeam.utils.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity() {

    lateinit var rlMainActivity : RelativeLayout

    lateinit var pbMain : ProgressBar

    lateinit var viewPager : ViewPager
    lateinit var tabParts : TabLayout
    private lateinit var adapter : ViewPagerAdapter
    var leadsList = ArrayList<LeadsDO>()

    var displayLeads = ArrayList<LeadsDO>()

    var newLeads = ArrayList<String>()
    var existingLeadId = ArrayList<String>()
    var stateList = HashMap<String, String>()

    lateinit var dialog : Dialog

    val agentDo = AgentsDO()

    var agentDet = AgentsDO()
    val agentList = ArrayList<AgentsDO>()

    lateinit var adminEmail : String
    lateinit var adminName : String

    lateinit var adminPreferences: SharedPreferences

    lateinit var progressDialog : ProgressDialog

    var updateStatus = "noUpdate"

    companion object {
        @SuppressLint("StaticFieldLeak")
        var mainActivity: MainActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        title = "Edubeam Leads"

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        rlMainActivity = findViewById(R.id.rlMainActivity)

        mainActivity = this

        progressDialog = ProgressDialog(this@MainActivity, R.style.MyAlertDialogStyle)

        pbMain = findViewById(R.id.pbMain)
        pbMain.indeterminateDrawable.setColorFilter(resources.getColor(R.color.colorEmerald), android.graphics.PorterDuff.Mode.MULTIPLY)
        pbMain.visibility = View.GONE

        dialog = Dialog(this)

        adminPreferences = getSharedPreferences("AdminDetails", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = adminPreferences.getString("Agent", "")
        agentDet = gson.fromJson<AgentsDO>(json, AgentsDO::class.java)
        adminEmail = agentDet.emailId.toString()
        adminName = agentDet.name.toString()

        /*newLeads.add("ad7cb808-5980-4826-acde-94a33b13c9a0")
        NewLead().execute()*/

        if (adminEmail == Config.SAEMAIL) {
            LeadDetails().execute()
        } else {
            LeadByAgentDetails().execute(adminName)
        }

        //createStateList()

    }

    private fun setViewPager(){
        viewPager = findViewById(R.id.viewPager)
        viewPager.offscreenPageLimit = 1

        tabParts = findViewById(R.id.tabParts)
        tabParts.setSelectedTabIndicatorColor(resources.getColor(R.color.colorPrimary))

        if (progressDialog.isShowing)
            progressDialog.dismiss()

        setTabDetails()
    }

    private fun setTabDetails(){
        setupViewPager(viewPager)
        tabParts.setupWithViewPager(viewPager)
    }

    private fun setupViewPager(viewPager: ViewPager) {
        adapter = ViewPagerAdapter(supportFragmentManager)
        adapter.addFrag(UsersList.newInstance(displayLeads), "LEADS")
        adapter.addFrag(TodayList.newInstance(displayLeads), "TODAY")
        adapter.addFrag(PendingList.newInstance(displayLeads), "PENDING")
        if (adminEmail == Config.SAEMAIL)
            adapter.addFrag(NewList.newInstance(leadsList), "NEW LEADS")
        viewPager.adapter = adapter
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val newLead = menu.findItem(R.id.new_leads)
        newLead.isVisible = adminEmail == Config.SAEMAIL
        val filter = menu.findItem(R.id.filter)
        filter.isVisible = adminEmail == Config.SAEMAIL
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            R.id.new_leads -> {
                FetchNewLead().execute()
                return true
            }
            R.id.filter -> {
                agentsDialog(agentList)
                return true
            }
            R.id.refresh -> {
                refreshOnUpdate()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun basicSnackBar(message: String) {
        val snackbar = Snackbar.make(rlMainActivity, message, Snackbar.LENGTH_SHORT)
        val sbView = snackbar.view
        sbView.setBackgroundColor(ContextCompat.getColor(baseContext, R.color.colorBlue))
        val textView = sbView.findViewById<TextView>(android.support.design.R.id.snackbar_text)
        textView.setTextColor(ContextCompat.getColor(baseContext, R.color.colorWhite))
        snackbar.show()
    }

    fun showSnackBar(message: String, type: String) {
        val snackbar = Snackbar.make(rlMainActivity, message, Snackbar.LENGTH_SHORT)
                .setAction("Try Again") {
                    when (type){
                        "leads" ->
                            if (adminEmail == Config.SAEMAIL) {
                                LeadDetails().execute()
                            } else {
                                LeadByAgentDetails().execute(adminName)
                            }
                        "addAgent" -> AddAgent().execute()
                        "newLeads" -> FetchNewLead().execute()
                    }
                }
        snackbar.setActionTextColor(ContextCompat.getColor(baseContext, R.color.colorAccent))

        val sbView = snackbar.view
        sbView.setBackgroundColor(ContextCompat.getColor(baseContext, R.color.colorBlue))
        val textView = sbView.findViewById<TextView>(android.support.design.R.id.snackbar_text)
        textView.setTextColor(ContextCompat.getColor(baseContext, R.color.colorWhite))
        snackbar.duration = BaseTransientBottomBar.LENGTH_INDEFINITE
        snackbar.show()
    }

    private fun showAddAgentDialog(){
        dialog.setContentView(R.layout.add_agent_dialog)
        dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        val etAgentEmail = dialog.findViewById<EditText>(R.id.etAgentEmail)
        val etAgentPhone = dialog.findViewById<EditText>(R.id.etAgentPhone)
        val etAgentName = dialog.findViewById<EditText>(R.id.etAgentName)
        val etAgentPassword = dialog.findViewById<EditText>(R.id.etAgentPassword)

        val btnCreate = dialog.findViewById<Button>(R.id.btnCreate)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)

        btnCreate.setOnClickListener {
            if (TextUtils.isEmpty(etAgentEmail.text.toString()) ||
                    TextUtils.isEmpty(etAgentPhone.text.toString()) ||
                    TextUtils.isEmpty(etAgentName.text.toString()) ||
                    TextUtils.isEmpty(etAgentPassword.text.toString())) {
                basicSnackBar("Please enter valid details for all fields")
            } else {
                agentDo.name = etAgentName.text.toString()
                agentDo.emailId = etAgentEmail.text.toString()
                agentDo.phone = etAgentPhone.text.toString()
                agentDo.password = etAgentPassword.text.toString()
                agentDo.accessType = "Access"
                AddAgent().execute()
            }
        }
        btnCancel.setOnClickListener{ dialog.dismiss() }

        dialog.show()
    }

    fun getAgents(): ArrayList<AgentsDO>{
        return agentList
    }

    fun refreshOnUpdate(){
        if (adminEmail == Config.SAEMAIL) {
            LeadDetails().execute()
        } else {
            LeadByAgentDetails().execute(adminName)
        }
    }

    /*fun createStateList(){
        val state = HashMap<String, String>()
        state.put("Andhra Pradesh", "Sandhya")
        state.put("Arunachal Pradesh", "Tanya")
        state.put("Assam", "Tanya")
        state.put("Bihar", "Tanya")
        state.put("Chhattisgarh", "Sandhya")
        state.put("Goa", "Sandhya")
        state.put("Gujarat", "Sandhya")
        state.put("Haryana", "Tanya")
        state.put("Himachal Pradesh", "Tanya")
        state.put("Jammu & Kashmir", "Sandhya")
        state.put("Jharkhand", "Tanya")
        state.put("Karnataka", "Sandhya")
        state.put("Kerala", "Sandhya")
        state.put("Madhya Pradesh", "Sandhya")
        state.put("Maharashtra", "Tanya")
        state.put("Manipur", "Tanya")
        state.put("Meghalaya", "Tanya")
        state.put("Mizoram", "Tanya")
        state.put("Nagalanad", "Tanya")
        state.put("Odisha", "Tanya")
        state.put("Punjab", "Sandhya")
        state.put("Rajasthan", "Sandhya")
        state.put("Sikkim", "Tanya")
        state.put("Tamil Nadu", "Sandhya")
        state.put("Telangana", "Sandhya")
        state.put("Tripura", "Tanya")
        state.put("Uttarakhand", "Sandhya")
        state.put("West Bengal", "Tanya")
        state.put("Andaman", "Sandhya")
        state.put("Daman", "Sandhya")

        Thread(Runnable {
            val awsProvider = AWSProvider()
            val dynamoDBClient = AmazonDynamoDBClient(awsProvider.getCredentialsProvider(baseContext))
            dynamoDBClient.setRegion(Region.getRegion(Regions.US_EAST_1))
            val dynamoDBMapper = DynamoDBMapper.builder()
                    .dynamoDBClient(dynamoDBClient)
                    .awsConfiguration(AWSMobileClient.getInstance().configuration)
                    .build()

            val stateAssign = StateAssignDO()
            stateAssign.emailId = Config.SAEMAIL
            stateAssign.phone = Config.SAPHONE
            stateAssign.stateList = state
            dynamoDBMapper.save(stateAssign)
            basicSnackBar("Done Uploading")

        }).start()

    }*/

    private fun agentsDialog(agentList: ArrayList<AgentsDO>){
        dialog.setContentView(R.layout.list_view_dialog)
        dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvTitle)
        tvTitle.text = "Select Agent"
        val ivStates = dialog.findViewById<ImageView>(R.id.ivStates)
        ivStates.visibility = View.VISIBLE
        val ivAddAgent = dialog.findViewById<ImageView>(R.id.ivAddAgent)
        ivAddAgent.visibility = View.VISIBLE
        val tvNote = dialog.findViewById<TextView>(R.id.tvNote)
        tvNote.visibility = View.VISIBLE
        val lvCentres = dialog.findViewById<ListView>(R.id.lvItems)
        val agentName = ArrayList<String>()
        Collections.sort(agentList, NameComparator())
        for (i in 0 until agentList.size){
            agentName.add(agentList[i].name.toString())
        }

        ivAddAgent.setOnClickListener { showAddAgentDialog() }

        ivStates.setOnClickListener { showStates() }

        val agentsListAdapter = AgentsListAdapter(this@MainActivity, agentName)
        lvCentres.adapter = agentsListAdapter

        lvCentres.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            if (dialog.isShowing) {
                dialog.dismiss()
            }
            leadsByAgent(agentName[position])
        }

        lvCentres.onItemLongClickListener = AdapterView.OnItemLongClickListener { adapterView, view, pos, l ->
            showAgentDetailsDialog(agentList[pos])
        }
        dialog.show()
    }

    private fun leadsByAgent(agentName: String){
        displayLeads.clear()
        if (agentName == "Head Office") {
            for (i in 0 until leadsList.size){
                displayLeads.add(leadsList[i])
            }
        } else {
            for (i in 0 until leadsList.size) {
                if (leadsList[i].assignedTo == agentName) {
                    displayLeads.add(leadsList[i])
                }
            }
        }
        setViewPager()
    }

    private fun showStates(){
        val dialog2 = Dialog(this)
        dialog2.setContentView(R.layout.states_dialog)
        dialog2.window!!.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialog2.findViewById<TextView>(R.id.tvTitle)
        tvTitle.text = "States List"
        val lvStates = dialog2.findViewById<ListView>(R.id.lvStates)

        val nfdVal = stateList
        val nnfd = HashMap<String, String>()
        val nfdKeys = nfdVal.keys
        val states = ArrayList<String>()
        for (key1 in nfdKeys) {
            nnfd.put(key1, nfdVal[key1].toString())
            states.add(key1)
        }

        val agentName = ArrayList<String>()
        Collections.sort(agentList, NameComparator())
        for (i in 0 until agentList.size){
            agentName.add(agentList[i].name.toString())
        }

        if (agentName.contains("Head Office"))
            agentName.remove("Head Office")

        Collections.sort(states)
        val stateListAdapter = StateListAdapter(this@MainActivity, states, agentName, stateList)
        lvStates.adapter = stateListAdapter

        lvStates.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            val dialog3 = Dialog(this)
            dialog3.setContentView(R.layout.state_agent_dialog)
            dialog3.window!!.setBackgroundDrawableResource(android.R.color.transparent)

            val stateName = dialog3.findViewById<TextView>(R.id.stateName)
            stateName.text = states[position]

            val spAgent = dialog3.findViewById<Spinner>(R.id.spAgent)
            val agentAdapter = SpinnerAdapter(baseContext, R.layout.status_item, agentName)
            spAgent!!.adapter = agentAdapter
            try {
                spAgent.setSelection(agentName.indexOf(stateList[states[position]]))
            } catch (e:Exception){
            }
            spAgent.isEnabled = false

            val btnEdit = dialog3.findViewById<Button>(R.id.btnEdit)
            val btnSave = dialog3.findViewById<Button>(R.id.btnSave)
            btnSave.visibility = View.GONE
            val btnCancel = dialog3.findViewById<Button>(R.id.btnCancel)

            btnCancel.setOnClickListener { dialog3.dismiss() }

            btnEdit.setOnClickListener {
                btnEdit.visibility = View.GONE
                btnSave.visibility = View.VISIBLE
                spAgent.isEnabled = true
            }

            btnSave.setOnClickListener {
                dialog3.dismiss()

                btnEdit.visibility = View.VISIBLE
                btnSave.visibility = View.GONE
                spAgent.isEnabled = false

                val progressDialog = ProgressDialog(this@MainActivity, R.style.MyAlertDialogStyle)
                progressDialog.setMessage("Updating details, please wait...")
                progressDialog.setCancelable(false)
                progressDialog.show()
                Thread(Runnable {
                    try {
                        val awsProvider = AWSProvider()
                        val dynamoDBClient = AmazonDynamoDBClient(awsProvider.getCredentialsProvider(baseContext))
                        dynamoDBClient.setRegion(Region.getRegion(Regions.US_EAST_1))
                        val dynamoDBMapper = DynamoDBMapper.builder()
                                .dynamoDBClient(dynamoDBClient)
                                .awsConfiguration(AWSMobileClient.getInstance().configuration)
                                .build()
                        val stateDO = StateAssignDO()
                        stateDO.emailId = Config.SAEMAIL
                        stateDO.phone = Config.SAPHONE

                        stateList.put(states[position], spAgent.selectedItem.toString())
                        stateDO.stateList = stateList

                        dynamoDBMapper.save<StateAssignDO>(stateDO)
                        progressDialog.dismiss()
                        basicSnackBar("Successfully updated State Assigning details. Refresh to get updated values")
                    } catch (e : AmazonClientException){
                        progressDialog.dismiss()
                        basicSnackBar("Failed to updated State Assigning details. Try again.")
                    }
                }).start()
                dialog2.dismiss()
                stateListAdapter.notifyDataSetChanged()
            }

            dialog3.show()
        }

        dialog2.show()

    }

    inner class NameComparator : Comparator<AgentsDO> {
        override fun compare(obj1: AgentsDO, obj2: AgentsDO): Int {
            return obj1.name.toString().toLowerCase().compareTo(obj2.name.toString().toLowerCase())
        }
    }

    private fun showAgentDetailsDialog(agentDetails: AgentsDO): Boolean {
        dialog.setContentView(R.layout.agent_details_dialog)
        dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)

        val tvAName = dialog.findViewById<TextView>(R.id.tvAName)
        tvAName.text = agentDetails.name
        val tvAEmail = dialog.findViewById<TextView>(R.id.tvAEmail)
        tvAEmail.text = agentDetails.emailId
        val tvAPhone = dialog.findViewById<TextView>(R.id.tvAPhone)
        tvAPhone.text = agentDetails.phone
        val etAPassword = dialog.findViewById<EditText>(R.id.etAPassword)
        etAPassword.setText(agentDetails.password)
        etAPassword.tag = etAPassword.keyListener
        etAPassword.keyListener = null
        val spAccessType = dialog.findViewById<Spinner>(R.id.spAccessType)
        val accessTypeItem = ArrayList<String>()
        accessTypeItem.add("Access")
        accessTypeItem.add("Restrict")
        val accessTypeAdapter = SpinnerAdapter(this, R.layout.status_item, accessTypeItem)
        spAccessType.adapter = accessTypeAdapter
        spAccessType.setSelection(accessTypeItem.indexOf(agentDetails.accessType))
        spAccessType.isEnabled = false

        val btnCancel = dialog.findViewById<Button>(R.id.btnCCancel)
        val btnEdit = dialog.findViewById<Button>(R.id.btnCEdit)
        val btnSave = dialog.findViewById<Button>(R.id.btnCSave)
        btnSave.visibility = View.GONE

        btnCancel.setOnClickListener{ dialog.dismiss() }

        btnEdit.setOnClickListener{
            btnEdit.visibility = View.GONE
            etAPassword.keyListener = etAPassword.tag as KeyListener
            btnSave.visibility = View.VISIBLE
            spAccessType.isEnabled = true
        }

        btnSave.setOnClickListener{
            val progressDialog = ProgressDialog(this@MainActivity, R.style.MyAlertDialogStyle)
            progressDialog.setMessage("Updating agent details...")
            progressDialog.setCancelable(false)
            progressDialog.show()
            btnSave.visibility = View.GONE
            btnEdit.visibility = View.VISIBLE
            etAPassword.keyListener = null
            spAccessType.isEnabled = false
            Thread(Runnable {
                val awsProvider = AWSProvider()
                val dynamoDBClient = AmazonDynamoDBClient(awsProvider.getCredentialsProvider(baseContext))
                dynamoDBClient.setRegion(Region.getRegion(Regions.US_EAST_1))
                val dynamoDBMapper = DynamoDBMapper.builder()
                        .dynamoDBClient(dynamoDBClient)
                        .awsConfiguration(AWSMobileClient.getInstance().configuration)
                        .build()
                val agentsDO = AgentsDO()
                agentsDO.name = tvAName.text.toString()
                agentsDO.emailId = tvAEmail.text.toString()
                agentsDO.phone = tvAPhone.text.toString()
                agentsDO.password = etAPassword.text.toString()
                agentsDO.accessType = spAccessType.selectedItem.toString()
                dynamoDBMapper.save<AgentsDO>(agentsDO)
                progressDialog.dismiss()
                basicSnackBar("Successfully updated "+agentDetails.name+" details.")
            }).start()
        }

        dialog.show()
        return true
    }


    internal inner class ViewPagerAdapter (manager: FragmentManager) : FragmentPagerAdapter(manager) {
        private val mFragmentList = java.util.ArrayList<Fragment>()
        private val mFragmentTitleList = java.util.ArrayList<String>()

        override fun getItem(position: Int): Fragment {
            return mFragmentList[position]
        }

        override fun getCount(): Int {
            return mFragmentList.size
        }

        fun addFrag(fragment: Fragment, title: String) {
            mFragmentList.add(fragment)
            mFragmentTitleList.add(title)
        }

        override fun getPageTitle(position: Int): CharSequence? {
            return mFragmentTitleList[position]
        }

    }

    class AgentsListAdapter : BaseAdapter {

        private var agentList = ArrayList<String>()
        private var inflater: LayoutInflater? = null

        constructor(context: Context, agentList: ArrayList<String>) : super() {
            this.agentList = agentList
            inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        }

        @SuppressLint("ViewHolder", "InflateParams")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
            val holder = Holder()
            val rowView = inflater!!.inflate(R.layout.list_view_item, null)
            holder.agentName = rowView.findViewById(R.id.agentName)
            holder.agentName!!.text = agentList[position]
            holder.view = rowView.findViewById(R.id.view)
            return rowView
        }

        override fun getItem(position: Int): Any {
            return agentList[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return agentList.size
        }

        private inner class Holder {
            internal var agentName: TextView? = null
            internal var view: View? = null
        }
    }

    class StateListAdapter : BaseAdapter {

        private var state = ArrayList<String>()
        private var inflater: LayoutInflater? = null
        private var context: Context?= null
        private var agents = ArrayList<String>()
        private var stateList = HashMap<String, String>()

        constructor(context: Context, state: ArrayList<String>, agents: ArrayList<String>, stateList: HashMap<String, String>) : super() {
            this.context = context
            this.state = state
            this.agents = agents
            this.stateList = stateList
            inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        }

        @SuppressLint("ViewHolder", "InflateParams")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
            val holder = Holder()
            val rowView = inflater!!.inflate(R.layout.state_list_item, null)
            holder.stateName = rowView.findViewById(R.id.stateName)
            holder.stateName!!.text = state[position]
            holder.agentName = rowView.findViewById(R.id.agentName)
            holder.agentName!!.text = stateList[state[position]]

            return rowView
        }

        override fun getItem(position: Int): Any {
            return state[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return state.size
        }

        private inner class Holder {
            internal var stateName: TextView? = null
            internal var agentName: TextView? = null
        }
    }

    class SpinnerAdapter(context: Context, resourceId: Int, private val objects: List<String>) : ArrayAdapter<String>(context, resourceId, objects) {

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return getCustomView(position, convertView, parent)
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return getCustomView(position, convertView, parent)
        }

        private fun getCustomView(position: Int, convertView: View?, parent: ViewGroup): View {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val row = inflater.inflate(R.layout.status_item, parent, false)
            val label = row!!.findViewById<TextView>(R.id.tvSpinner)
            label.text = objects[position]
            return row
        }

    }

    override fun onSupportNavigateUp(): Boolean {
        dialog.setContentView(R.layout.conformation_dialog)
        dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)
        val tvCSMS = dialog.findViewById<TextView>(R.id.tvCSMS)
        tvCSMS.text = "Are you sure, you want to logout."
        val tvYes = dialog.findViewById<TextView>(R.id.tvYes)
        tvYes.setOnClickListener {
            dialog.dismiss()
            val prefsEditor = adminPreferences.edit()
            prefsEditor.putString("Login", "logout")
            prefsEditor.apply()
            startActivity(Intent(baseContext, LoginActivity::class.java))
            finish()
        }
        val tvNo = dialog.findViewById<TextView>(R.id.tvNo)
        tvNo.setOnClickListener { dialog.dismiss() }
        dialog.show()
        return false
    }


    //Leadsquared starts from here

    @SuppressLint("StaticFieldLeak")
    inner class LeadDetails : AsyncTask<Void, Void, Boolean>() {

        override fun onPreExecute() {
            progressDialog.setMessage("Loading Leads, please wait...")
            progressDialog.setCancelable(false)
            progressDialog.show()
            leadsList.clear()
            agentList.clear()
            stateList.clear()
            displayLeads.clear()
        }

        override fun doInBackground(vararg p0: Void?): Boolean {

            val awsProvider = AWSProvider()
            val dynamoDBClient = AmazonDynamoDBClient(awsProvider.getCredentialsProvider(baseContext))
            dynamoDBClient.setRegion(Region.getRegion(Regions.US_EAST_1))

            try {
                var result: ScanResult? = null
                do {
                    val req = ScanRequest()
                    req.tableName = Config.LEADTABLE
                    if (result != null) {
                        req.exclusiveStartKey = result.lastEvaluatedKey
                    }
                    result = dynamoDBClient.scan(req)
                    val rows = result.items
                    for (map in rows) {

                        val leadsDo = LeadsDO()

                        leadsDo.phone = map["phone"]!!.s
                        leadsDo.emailId = map["emailId"]!!.s
                        leadsDo.name = map["name"]!!.s
                        leadsDo.rating = map["rating"]!!.s
                        leadsDo.status = map["status"]!!.s
                        leadsDo.assignedTo = map["assignedTo"]!!.s
                        leadsDo.createdDate = map["createdDate"]!!.s
                        leadsDo.leadScore = map["leadScore"]!!.s
                        leadsDo.state = map["state"]!!.s
                        leadsDo.leadId = map["leadId"]!!.s
                        leadsDo.leadType = map["leadType"]!!.s

                        val nfdVal = map["NFD"]!!.m
                        val nfd = HashMap<String, String>()
                        val nfdKeys = nfdVal.keys
                        for (key1 in nfdKeys) {
                            val key = key1 as String
                            nfd.put(key, nfdVal[key]!!.s)
                        }

                        leadsDo.nfd = nfd

                        leadsList.add(leadsDo)
                        displayLeads.add(leadsDo)

                    }
                } while (result!!.lastEvaluatedKey != null)

                var result1: ScanResult? = null
                do {
                    val req = ScanRequest()
                    req.tableName = Config.AGENTTABLE
                    if (result1 != null) {
                        req.exclusiveStartKey = result1.lastEvaluatedKey
                    }
                    result1 = dynamoDBClient.scan(req)
                    val rows = result1.items
                    for (map in rows) {

                        val agentDo = AgentsDO()

                        agentDo.phone = map["phone"]!!.s
                        agentDo.emailId = map["emailId"]!!.s
                        agentDo.name = map["name"]!!.s
                        agentDo.password = map["password"]!!.s
                        agentDo.accessType = map["accessType"]!!.s

                        agentList.add(agentDo)

                    }
                } while (result1!!.lastEvaluatedKey != null)

                val dynamoDBMapper = DynamoDBMapper.builder()
                        .dynamoDBClient(dynamoDBClient)
                        .awsConfiguration(AWSMobileClient.getInstance().configuration)
                        .build()

                val states = dynamoDBMapper.load(StateAssignDO::class.java, Config.SAEMAIL, Config.SAPHONE)
                stateList = states.stateList as HashMap<String, String>

                return true
            } catch (e : AmazonClientException){
                showSnackBar("Network connection error!!", "leads")
                return false
            }
        }

        override fun onPostExecute(result: Boolean?) {
            if (dialog.isShowing) {
                dialog.dismiss()
            }
            if (result!!){
                if (updateStatus == "update"){
                    updateStatus = "noUpdate"
                    setViewPager()
                } else {
                    FetchLeadScore().execute()
                }
            } else {
                progressDialog.dismiss()
            }
        }

    }


    @SuppressLint("StaticFieldLeak")
    inner class LeadByAgentDetails : AsyncTask<String, Void, Boolean>() {

        override fun onPreExecute() {
            progressDialog.setMessage("Loading Leads, please wait...")
            progressDialog.setCancelable(false)
            progressDialog.show()
            leadsList.clear()
            displayLeads.clear()
        }

        override fun doInBackground(vararg p0: String?): Boolean {

            val awsProvider = AWSProvider()
            val dynamoDBClient = AmazonDynamoDBClient(awsProvider.getCredentialsProvider(baseContext))
            dynamoDBClient.setRegion(Region.getRegion(Regions.US_EAST_1))

            try {

                if (adminEmail != Config.SAEMAIL) {

                    var agent = AgentsDO()
                    agent.phone = agentDet.phone

                    val dynamoDBMapper = DynamoDBMapper.builder()
                            .dynamoDBClient(dynamoDBClient)
                            .awsConfiguration(AWSMobileClient.getInstance().configuration)
                            .build()

                    val queryExpression = DynamoDBQueryExpression<AgentsDO>()
                            .withHashKeyValues(agent)
                            .withConsistentRead(false)
                    val cResult = dynamoDBMapper.query(AgentsDO::class.java, queryExpression)

                    val gson = Gson()
                    for (i in 0 until cResult.size) {
                        val jsonFormOfItem = gson.toJson(cResult[i])
                        agent = gson.fromJson(jsonFormOfItem, AgentsDO::class.java)
                    }

                    if (agent.accessType != "Access"){
                        val prefsEditor = adminPreferences.edit()
                        prefsEditor.putString("Login", "logout")
                        prefsEditor.apply()
                        startActivity(Intent(baseContext, LoginActivity::class.java))
                        finish()
                    }

                }

                var result: ScanResult? = null
                do {
                    val req = ScanRequest()
                    req.tableName = Config.LEADTABLE
                    if (result != null) {
                        req.exclusiveStartKey = result.lastEvaluatedKey
                    }
                    result = dynamoDBClient.scan(req)
                    val rows = result.items
                    for (map in rows) {

                        if (map["assignedTo"]!!.s == p0[0]) {

                            val leadsDo = LeadsDO()

                            leadsDo.phone = map["phone"]!!.s
                            leadsDo.emailId = map["emailId"]!!.s
                            leadsDo.name = map["name"]!!.s
                            leadsDo.rating = map["rating"]!!.s
                            leadsDo.status = map["status"]!!.s
                            leadsDo.assignedTo = map["assignedTo"]!!.s
                            leadsDo.createdDate = map["createdDate"]!!.s
                            leadsDo.leadScore = map["leadScore"]!!.s
                            leadsDo.state = map["state"]!!.s
                            leadsDo.leadId = map["leadId"]!!.s
                            leadsDo.leadType = map["leadType"]!!.s

                            val nfdVal = map["NFD"]!!.m
                            val nfd = HashMap<String, String>()
                            val nfdKeys = nfdVal.keys
                            for (key1 in nfdKeys) {
                                val key = key1 as String
                                nfd.put(key, nfdVal[key]!!.s)
                            }

                            leadsDo.nfd = nfd

                            leadsList.add(leadsDo)
                            displayLeads.add(leadsDo)

                        }

                    }
                } while (result!!.lastEvaluatedKey != null)

                return true
            } catch (e : AmazonClientException){
                showSnackBar("Network connection error!!", "leads")
                return false
            }
        }

        override fun onPostExecute(result: Boolean?) {
            if (dialog.isShowing) {
                dialog.dismiss()
            }
            if (result!!){
                if (updateStatus == "update"){
                    updateStatus = "noUpdate"
                    setViewPager()
                } else {
                    FetchLeadScore().execute()
                }
            } else {
                progressDialog.dismiss()
            }
        }

    }


    @SuppressLint("StaticFieldLeak")
    inner class FetchLeadScore : AsyncTask<Void, Void, Boolean>() {

        val leads = ArrayList<LeadsDO>()

        @SuppressLint("SimpleDateFormat")
        override fun doInBackground(vararg p0: Void?): Boolean {

            val awsProvider = AWSProvider()
            val dynamoDBClient = AmazonDynamoDBClient(awsProvider.getCredentialsProvider(baseContext))
            dynamoDBClient.setRegion(Region.getRegion(Regions.US_EAST_1))
            val dynamoDBMapper = DynamoDBMapper.builder()
                    .dynamoDBClient(dynamoDBClient)
                    .awsConfiguration(AWSMobileClient.getInstance().configuration)
                    .build()

            for (j in 0 until leadsList.size){

                val leadId = leadsList[j].leadId

                val lsqURL = "https://api.leadsquared.com/v2/LeadManagement.svc/Leads.GetById?accessKey=${Config.LSACCESSKEY}&secretKey=${Config.LSSECRETKEY}&id=$leadId"

                var connection: HttpURLConnection? = null
                var reader: BufferedReader? = null

                try {
                    val url = URL(lsqURL)
                    connection = url.openConnection() as HttpURLConnection
                    connection.connect()

                    val stream = connection.inputStream

                    reader = BufferedReader(InputStreamReader(stream))

                    val buffer = StringBuilder()
                    var line = reader.readLine()

                    while ( line != null) {
                        buffer.append(line).append("\n")
                        Log.d("Response: ", "> " + line)
                        line = reader.readLine()
                    }

                    try {

                        val fetchLead = dynamoDBMapper.load(LeadsDO()::class.java, leadsList[j].phone, leadsList[j].emailId)

                        val jArray = JSONArray(buffer.toString())
                        var jsonObject = JSONObject()
                        for (i in 0 until jArray.length()) {
                            jsonObject = jArray.getJSONObject(i)
                            val leadScore = jsonObject.getString("Score")
                            if (leadScore != fetchLead.leadScore) {
                                fetchLead.leadScore = leadScore
                                dynamoDBMapper.save(fetchLead)
                            }
                            leads.add(fetchLead)
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                        return false
                    }

                } catch (e: IOException) {
                    e.printStackTrace()
                    return false
                } finally {
                    if (connection != null) {
                        connection.disconnect()
                    }
                    try {
                        if (reader != null) {
                            reader.close()
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        return false
                    }
                }
            }
            leadsList.clear()
            displayLeads.clear()
            for (i in 0 until leads.size){
                leadsList.add(leads[i])
                displayLeads.add(leads[i])
            }

            return true

        }

        override fun onPostExecute(result: Boolean?) {
            progressDialog.dismiss()
            if (result!!){
                setViewPager()
            } else {
                Toast.makeText(baseContext, "Failed to load leads", Toast.LENGTH_LONG).show()
                showSnackBar("Failed to update leads", "newLeads")
            }
        }

    }


    @SuppressLint("StaticFieldLeak")
    inner class AddAgent : AsyncTask<Void, Void, String>() {

        override fun onPreExecute() {
            progressDialog.setMessage("Adding agent, please wait...")
            progressDialog.setCancelable(false)
            progressDialog.show()
        }

        override fun doInBackground(vararg p0: Void?): String {

            val awsProvider = AWSProvider()
            val dynamoDBClient = AmazonDynamoDBClient(awsProvider.getCredentialsProvider(baseContext))
            dynamoDBClient.setRegion(Region.getRegion(Regions.US_EAST_1))
            val dynamoDBMapper = DynamoDBMapper.builder()
                    .dynamoDBClient(dynamoDBClient)
                    .awsConfiguration(AWSMobileClient.getInstance().configuration)
                    .build()

            try {
                var result: ScanResult? = null
                do {
                    val req = ScanRequest()
                    req.tableName = Config.AGENTTABLE
                    if (result != null) {
                        req.exclusiveStartKey = result.lastEvaluatedKey
                    }
                    result = dynamoDBClient.scan(req)
                    val rows = result!!.items
                    for (map in rows) {
                        try {
                            if (map["phone"]!!.s == agentDo.phone) {
                                return "exist"
                            }
                        } catch (e: NumberFormatException) {
                            println(e.message)
                        }
                    }
                } while (result!!.lastEvaluatedKey != null)

                dynamoDBMapper.save<AgentsDO>(agentDo)

                return "added"
            } catch (e: AmazonClientException) {
                showSnackBar("Network connection error!!", "addAgent")
                return "error"
            }

        }

        override fun onPostExecute(result: String) {
            progressDialog.dismiss()
            if (result == "added"){
                basicSnackBar("Successfully created Agent")
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
                refreshOnUpdate()
            } else if (result == "exist") {
                basicSnackBar("Agent with these details already exists")
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    inner class FetchNewLead : AsyncTask<Void, Void, Boolean>() {

        val leadId = ArrayList<String>()

        override fun onPreExecute() {
            progressDialog.setMessage("Fetching New Leads, please wait...")
            progressDialog.setCancelable(false)
            progressDialog.show()

            existingLeadId.clear()
            newLeads.clear()
        }

        @SuppressLint("SimpleDateFormat")
        override fun doInBackground(vararg p0: Void?): Boolean {

            val awsProvider = AWSProvider()
            val dynamoDBClient = AmazonDynamoDBClient(awsProvider.getCredentialsProvider(baseContext))
            dynamoDBClient.setRegion(Region.getRegion(Regions.US_EAST_1))
            val dynamoDBMapper = DynamoDBMapper.builder()
                    .dynamoDBClient(dynamoDBClient)
                    .awsConfiguration(AWSMobileClient.getInstance().configuration)
                    .build()

            val leadIds = dynamoDBMapper.load(LeadIdsDO::class.java, Config.SAEMAIL , Config.SAPHONE)
            if (leadIds != null){
                existingLeadId = leadIds.leadId as ArrayList<String>
            } else {
                val leadIdList = LeadIdsDO()
                leadIdList.emailId = Config.SAEMAIL
                leadIdList.phone = Config.SAPHONE
                leadIdList.leadId = existingLeadId
                dynamoDBMapper.save(leadIdList)
            }

            val lsqURL = "https://api.leadsquared.com/v2/LeadManagement.svc/List.GetLeads?accessKey=${Config.LSACCESSKEY}&secretKey=${Config.LSSECRETKEY}&listId=${Config.EDUBEAMLISTID}"

            var connection: HttpURLConnection? = null
            var reader: BufferedReader? = null

            try {
                val url = URL(lsqURL)
                connection = url.openConnection() as HttpURLConnection
                connection.connect()

                val stream = connection.inputStream

                reader = BufferedReader(InputStreamReader(stream))

                val buffer = StringBuilder()
                var line = reader.readLine()

                while ( line != null) {
                    buffer.append(line).append("\n")
                    Log.d("Response: ", "> " + line)
                    line = reader.readLine()
                }

                val ss = buffer.toString().replace("[", "").replace("]", "")
                Log.d("SS: ", "> " + ss)

                val jb = JSONObject(buffer.toString())
                val st = jb.getJSONArray("ProspectId")
                for (i in 0 until st.length()) {
                    leadId.add(st.getString(i))
                }

                return true

            } catch (e: IOException) {
                e.printStackTrace()
                return false
            } finally {
                if (connection != null) {
                    connection.disconnect()
                }
                try {
                    if (reader != null) {
                        reader.close()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    return false
                }

            }
        }

        override fun onPostExecute(result: Boolean?) {
            if (result!!){
                for (i in 0 until leadId.size){
                    if (!existingLeadId.contains(leadId[i])) {
                        existingLeadId.add(leadId[i])
                        newLeads.add(leadId[i])
                    }
                }
                if (newLeads.size >= 30){
                    Toast.makeText(baseContext, "Please check your database and try again.", Toast.LENGTH_LONG).show()
                } else {
                    if (newLeads.size != 0) {
                        NewLead().execute()
                    } else {
                        progressDialog.dismiss()
                        Toast.makeText(baseContext, "No New Leads", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                progressDialog.dismiss()
                Toast.makeText(baseContext, "Failed to update data", Toast.LENGTH_LONG).show()
                showSnackBar("Failed to update leads", "newLeads")
            }
        }

    }


    @SuppressLint("StaticFieldLeak")
    inner class NewLead : AsyncTask<Void, Void, Boolean>() {

        val newLead = LeadsDO()

        override fun onPreExecute() {
            progressDialog.setMessage("Adding Leads, please wait...")
            progressDialog.setCancelable(false)
            progressDialog.show()
        }

        @SuppressLint("SimpleDateFormat")
        override fun doInBackground(vararg p0: Void?): Boolean {

            val awsProvider = AWSProvider()
            val dynamoDBClient = AmazonDynamoDBClient(awsProvider.getCredentialsProvider(baseContext))
            dynamoDBClient.setRegion(Region.getRegion(Regions.US_EAST_1))
            val dynamoDBMapper = DynamoDBMapper.builder()
                    .dynamoDBClient(dynamoDBClient)
                    .awsConfiguration(AWSMobileClient.getInstance().configuration)
                    .build()

            try {

                val stateAssign = dynamoDBMapper.load(StateAssignDO::class.java, Config.SAEMAIL, Config.SAPHONE)
                val states = stateAssign.stateList

                for (j in 0 until newLeads.size){

                    val lsqURL = "https://api.leadsquared.com/v2/LeadManagement.svc/Leads.GetById?accessKey=${Config.LSACCESSKEY}&secretKey=${Config.LSSECRETKEY}&id=${newLeads[j]}"

                    var connection: HttpURLConnection? = null
                    var reader: BufferedReader? = null

                    try {
                        val url = URL(lsqURL)
                        connection = url.openConnection() as HttpURLConnection
                        connection.connect()

                        val stream = connection.inputStream

                        reader = BufferedReader(InputStreamReader(stream))

                        val buffer = StringBuilder()
                        var line = reader.readLine()

                        while ( line != null) {
                            buffer.append(line).append("\n")
                            Log.d("Response: ", "> " + line)
                            line = reader.readLine()
                        }

                        try {

                            val jArray = JSONArray(buffer.toString())
                            var jsonObject = JSONObject()
                            for (i in 0 until jArray.length()) {
                                jsonObject = jArray.getJSONObject(i)
                                val street = jsonObject.getString("FirstName")
                                Log.i("LeadName", street)

                                if (jsonObject.getString("Mobile") == "null")
                                    newLead.phone = jsonObject.getString("Phone")
                                else
                                    newLead.phone = jsonObject.getString("Mobile")
                                newLead.emailId = jsonObject.getString("EmailAddress")

                                var createdOn = jsonObject.getString("CreatedOn")
                                createdOn = createdOn!!.substring(0, 10)

                                newLead.createdDate = createdOn
                                newLead.leadScore = jsonObject.getString("Score")
                                newLead.name = jsonObject.getString("FirstName")
                                val state = jsonObject.getString("mx_Select_Your_State")
                                if (state != null){
                                    newLead.state = state
                                    newLead.assignedTo = states!![state]
                                } else {
                                    newLead.state = "Not Specified"
                                    newLead.assignedTo = "Not Assigned"
                                }
                                newLead.status = "open"
                                newLead.rating = "5"
                                newLead.leadId = newLeads[j]

                                val customerType = jsonObject.getString("mx_I_would_like_to")
                                if (customerType != null) {
                                    if (customerType.contains("New"))
                                        newLead.leadType = "New Preschool"
                                    else
                                        newLead.leadType = "Upgrade Preschool"
                                } else {
                                    newLead.leadType = "Not Specified"
                                }

                                val nfd = HashMap<String, String>()

                                val originalFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                                val targetFormat = SimpleDateFormat("yyyy/MM/dd")
                                val date = originalFormat.parse(createdOn)
                                val dd = targetFormat.format(date)

                                nfd.put(dd, "New Lead")

                                newLead.nfd = nfd

                                if (createdOn.substring(0, 4) == "2018")
                                    dynamoDBMapper.save(newLead)
                            }
                        } catch (e: JSONException) {
                            e.printStackTrace()
                            return false
                        }

                    } catch (e: IOException) {
                        e.printStackTrace()
                        return false
                    } finally {
                        if (connection != null) {
                            connection.disconnect()
                        }
                        try {
                            if (reader != null) {
                                reader.close()
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                            return false
                        }
                    }
                }

                val leadIds = LeadIdsDO()
                leadIds.emailId = Config.SAEMAIL
                leadIds.phone = Config.SAPHONE
                leadIds.leadId = existingLeadId
                dynamoDBMapper.save(leadIds)

            } catch (e : AmazonClientException){
                return false
            }

            return true
        }

        override fun onPostExecute(result: Boolean?) {
            if (result!!){
                //Toast.makeText(baseContext, "Found "+newLeads.size+" New Leads", Toast.LENGTH_LONG).show()
                LeadDetails().execute()
            } else {
                progressDialog.dismiss()
                Toast.makeText(baseContext, "Failed to update data", Toast.LENGTH_LONG).show()
                showSnackBar("Failed to update leads", "newLeads")
            }
        }

    }

}
