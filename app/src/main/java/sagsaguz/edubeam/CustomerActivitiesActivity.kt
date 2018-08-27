package sagsaguz.edubeam

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.ProgressDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.support.design.widget.BaseTransientBottomBar
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.method.KeyListener
import android.text.style.ForegroundColorSpan
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
import com.google.gson.Gson
import kotlinx.android.synthetic.main.new_enquiry_layout.*
import sagsaguz.edubeam.MainActivity.Companion.mainActivity
import sagsaguz.edubeam.utils.*
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class CustomerActivitiesActivity : AppCompatActivity() {

    lateinit var userName : TextView
    lateinit var userPhone : TextView
    lateinit var leadScore : TextView
    lateinit var llRating : LinearLayout

    lateinit var rlCustomerActivities : RelativeLayout
    lateinit var rlUserDetails : RelativeLayout

    lateinit var lvActivities : ListView

    lateinit var llOptions : LinearLayout
    lateinit var ibSMS : ImageButton
    lateinit var btnUpdate : Button
    lateinit var ibCall : ImageButton

    var dateList = ArrayList<String>()
    var fActivities = HashMap<String, String>()

    lateinit var dialog: Dialog

    lateinit var user : ActivitiesAdapter

    lateinit var leadDetails: LeadsDO

    lateinit var adminPreferences: SharedPreferences
    lateinit var agent: AgentsDO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.customer_activities_layout)

        title = "Lead Activities"

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val intent = intent
        leadDetails = intent.getSerializableExtra("lead") as LeadsDO

        adminPreferences = getSharedPreferences("AdminDetails", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = adminPreferences.getString("Agent", "")
        agent = gson.fromJson<AgentsDO>(json, AgentsDO::class.java)

        dialog = Dialog(this)

        rlCustomerActivities = findViewById(R.id.rlCustomerActivities)

        userName = findViewById(R.id.user_name)
        userName.text = leadDetails.name
        userPhone = findViewById(R.id.user_phone)
        userPhone.text = leadDetails.phone
        leadScore = findViewById(R.id.lead_score)
        leadScore.text = leadDetails.leadScore
        llRating = findViewById(R.id.llRating)
        val ratingPoint = leadDetails.rating!!.toInt()
        for (i in 0 until ratingPoint)
        {
            val imageView = ImageView(baseContext)
            imageView.setImageResource(R.drawable.icon_rating)
            imageView.layoutParams = LinearLayout.LayoutParams(40, 40)
            llRating.addView(imageView)
        }

        lvActivities = findViewById(R.id.lvActivities)

        user = ActivitiesAdapter(baseContext, dateList, fActivities)
        lvActivities.adapter = user

        customerActivities()

        llOptions = findViewById(R.id.llOptions)
        if (leadDetails.status != "open"){
            llOptions.visibility = View.GONE
        }
        ibSMS = findViewById(R.id.ibSMS)
        ibSMS.setOnClickListener {
            smsOrVideo()
        }
        btnUpdate = findViewById(R.id.btnUpdate)
        btnUpdate.setOnClickListener { updateNFD() }
        ibCall = findViewById(R.id.ibCall)
        ibCall.setOnClickListener { telephonePermissionCheck(leadDetails.phone.toString()) }

        rlUserDetails = findViewById(R.id.rlUserDetails)
        rlUserDetails.setOnClickListener {
            customerDetails()
        }

    }

    fun basicSnackBar(message: String) {
        val snackbar = Snackbar.make(rlCustomerActivities, message, Snackbar.LENGTH_SHORT)
        val sbView = snackbar.view
        sbView.setBackgroundColor(ContextCompat.getColor(baseContext, R.color.colorBlue))
        val textView = sbView.findViewById<TextView>(android.support.design.R.id.snackbar_text)
        textView.setTextColor(ContextCompat.getColor(baseContext, R.color.colorWhite))
        snackbar.show()
    }

    private fun customerDetails(){

        dialog.setContentView(R.layout.customer_details_dialog)
        dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        mainActivity!!.getAgents()

        val agentList = mainActivity!!.getAgents()
        val agentNames = ArrayList<String>()
        Collections.sort(agentList, AgentsNameComparator())
        for (i in 0 until agentList.size){
            agentNames.add(agentList[i].name.toString())
        }

        if (agentNames.contains("Head Office"))
            agentNames.remove("Head Office")

        val spAssignedTo = dialog.findViewById<Spinner> (R.id.spAssignedTo)
        val assignedToAdapter = SpinnerAdapter(this, R.layout.status_item, agentNames)
        spAssignedTo.adapter = assignedToAdapter
        try {
            spAssignedTo.setSelection(agentNames.indexOf(leadDetails.assignedTo))
        } catch (e:Exception){

        }
        spAssignedTo.isEnabled = false

        val assignedTo = dialog.findViewById<TextView> (R.id.AssignedTo)

        if (agent.name != "Head Office") {
            spAssignedTo.visibility = View.GONE
            assignedTo.visibility = View.GONE
        }

        val etName = dialog.findViewById<EditText> (R.id.etName)
        etName.setText(leadDetails.name)
        etName.tag = etName.keyListener
        etName.keyListener = null
        val etHouseLocality = dialog.findViewById<EditText> (R.id.etHouseLocality)
        etHouseLocality.setText(leadDetails.state)
        etHouseLocality.tag = etHouseLocality.keyListener
        etHouseLocality.keyListener = null
        val tvMobileNumber = dialog.findViewById<TextView> (R.id.tvMobileNumber)
        tvMobileNumber.text = leadDetails.phone
        val tvCreatedDate = dialog.findViewById<TextView> (R.id.tvCreatedDate)
        tvCreatedDate.text = leadDetails.createdDate
        val tvEmail = dialog.findViewById<TextView> (R.id.tvParentEmail)
        tvEmail.text = leadDetails.emailId

        val btnCancel = dialog.findViewById<Button>(R.id.btnCCancel)
        val btnEdit = dialog.findViewById<Button>(R.id.btnCEdit)
        val btnSave = dialog.findViewById<Button>(R.id.btnCSave)
        btnSave.visibility = View.GONE

        btnCancel.setOnClickListener{
            userName.text = capitalize(etName.text.toString())
            dialog.dismiss()
        }

        btnEdit.setOnClickListener{
            btnEdit.visibility = View.GONE
            etName.keyListener = etName.tag as KeyListener
            btnSave.visibility = View.VISIBLE
            spAssignedTo.isEnabled = true
        }

        btnSave.setOnClickListener {
            val progressDialog = ProgressDialog(this@CustomerActivitiesActivity, R.style.MyAlertDialogStyle)
            progressDialog.setMessage("Updating customer details...")
            progressDialog.setCancelable(false)
            progressDialog.show()
            btnSave.visibility = View.GONE
            btnEdit.visibility = View.VISIBLE
            etName.keyListener = null
            spAssignedTo.isEnabled = false

            Thread(Runnable {
                val awsProvider = AWSProvider()
                val dynamoDBClient = AmazonDynamoDBClient(awsProvider.getCredentialsProvider(baseContext))
                dynamoDBClient.setRegion(Region.getRegion(Regions.US_EAST_1))
                val dynamoDBMapper = DynamoDBMapper.builder()
                        .dynamoDBClient(dynamoDBClient)
                        .awsConfiguration(AWSMobileClient.getInstance().configuration)
                        .build()
                val leadsDo = leadDetails
                leadsDo.name = capitalize(etName.text.toString())
                leadsDo.assignedTo = spAssignedTo.selectedItem.toString()
                dynamoDBMapper.save<LeadsDO>(leadsDo)
                progressDialog.dismiss()
                mainActivity!!.updateStatus = "update"
            }).start()
        }

        dialog.show()
    }

    private fun capitalize(capString: String): String {
        val capBuffer = StringBuffer()
        val capMatcher = Pattern.compile("([a-z])([a-z]*)", Pattern.CASE_INSENSITIVE).matcher(capString)
        while (capMatcher.find()) {
            capMatcher.appendReplacement(capBuffer, capMatcher.group(1).toUpperCase() + capMatcher.group(2).toLowerCase())
        }
        return capMatcher.appendTail(capBuffer).toString()
    }

    private fun customerActivities(){

        dateList.clear()
        fActivities.clear()

        val nfdVal = leadDetails.nfd
        val nfdKeys = nfdVal!!.keys
        for (key1 in nfdKeys) {
            val key = key1
            dateList.add(key)
            fActivities.put(key, nfdVal[key].toString())
        }

        Collections.sort(dateList, CustomerDateComparator())

        user.notifyDataSetChanged()
        lvActivities.smoothScrollToPosition(dateList.size-1)
    }

    inner class CustomerDateComparator : Comparator<String> {
        override fun compare(emp1: String, emp2: String): Int {
            return emp1.compareTo(emp2)
        }
    }

    private fun smsOrVideo(){
        dialog.setContentView(R.layout.conformation_dialog)
        dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)
        val tvCSMS = dialog.findViewById<TextView>(R.id.tvCSMS)
        tvCSMS.text = "What type of message you want to send?."
        val tvYes = dialog.findViewById<TextView>(R.id.tvYes)
        tvYes.text = "SMS"
        tvYes.setOnClickListener {
            smsPermissionCheck(leadDetails.phone.toString())
        }
        val tvNo = dialog.findViewById<TextView>(R.id.tvNo)
        tvNo.text = "Video"
        tvNo.setOnClickListener {
            videoTemplates(leadDetails.phone.toString())
        }
        val tvPdf = dialog.findViewById<TextView>(R.id.tvPdf)
        tvPdf.visibility = View.VISIBLE
        tvPdf.setOnClickListener {
            pdfTemplates(leadDetails.phone.toString())
        }
        dialog.show()
    }

    private fun showTemplates(phoneNumber: String){
        dialog.setContentView(R.layout.sms_template)
        dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)

        val etCustomSMS = dialog.findViewById<EditText>(R.id.etCustomSMS)
        val ibSendSMS = dialog.findViewById<ImageButton>(R.id.ibSendSMS)
        ibSendSMS.setOnClickListener {
            if (TextUtils.isEmpty(etCustomSMS.text.toString())){
                Toast.makeText(baseContext, "Please enter your message", Toast.LENGTH_SHORT).show()
            } else {
                UpdateCallSMS().execute("SMS: Custom")
                sendSMS(phoneNumber, etCustomSMS.text.toString())
                dialog.dismiss()
            }
        }

        val lvSMSTemplate = dialog.findViewById<ListView>(R.id.lvSMSTemplate)

        val tvCancel = dialog.findViewById<TextView>(R.id.tvCancel)
        tvCancel.setOnClickListener { dialog.dismiss() }

        val templateNames = ArrayList<String>()
        templateNames.add("Lead Score Is Increasing")
        templateNames.add("Tried Calling But Didn't Received")
        templateNames.add("After Discussion")
        templateNames.add("Smart Digital Teaching")
        templateNames.add("Smart Books")
        templateNames.add("Package Quotation")
        templateNames.add("Registration Email")
        templateNames.add("First Discussion")
        templateNames.add("Curriculum Video")
        templateNames.add("Typical Preschool ROI")
        templateNames.add("Once Again Enquired")
        templateNames.add("Web Meeting")
        templateNames.add("Teacher's Training Video")
        templateNames.add("Package Offer")
        templateNames.add("Doubts Clarification Call")
        templateNames.add("All The Videos")
        templateNames.add("Montessori Teacher's Training")
        templateNames.add("Extra Revenue")
        templateNames.add("Material Technology")
        templateNames.add("Benefits Of Joining")
        templateNames.add("Multiple Intelligence App Demo")
        templateNames.add("Scheduled Web Meeting")
        templateNames.add("Bright Concept Teacher")
        templateNames.add("It Tools")

        val templateList = ArrayList<String>()
        templateList.add("Dear "+leadDetails.name+", Thanks for your interest. Hope you are finding our emails useful. Cheers -  Team Bright Edge Affiliate Program")
        templateList.add("Dear "+leadDetails.name+", Tried calling just now.  Looking forward to speak with you as we have scheduled.Thank You.")
        templateList.add("Dear "+leadDetails.name+", Hope you found the discussion fruitful on growing your preschool. Looking forward to having you in Bright Edge  family. Visit www.edubeam.net")
        templateList.add("Dear "+leadDetails.name+", Check our email on How to be the best in SMART (Digital) Teaching in your preschool.")
        templateList.add("Dear "+leadDetails.name+", As discussed sent you the link of our SMART Books, a highly popular & unique product. Pls check our emails & let us know if you need free sample.")
        templateList.add("Dear "+leadDetails.name+", Kindly check your registered email  to follow up with the quotation of our package giving precise list of our support items. Bright Edge Affiliate Program")
        templateList.add("Dear "+leadDetails.name+", Hope you found the discussion fruitful on growing your preschool, we have sent the registration email. Kindly check. www.edubeam.net")
        templateList.add("Dear "+leadDetails.name+", It was nice talking to you. Sent you the emails as discussed. Pls check. Talk to you once you go through the emails. Thanks.  Bright Edge Affiliate Program")
        templateList.add("Dear "+leadDetails.name+", Check our email and watch the video  on how to be the best in curriculum.")
        templateList.add("Dear "+leadDetails.name+", As per the discussion kindly watch video of the Typical Preschool ROI")
        templateList.add("Dear "+leadDetails.name+", Thank you for your enquiry once again. Contact us if you have any doubts regarding the same.")
        templateList.add("Dear "+leadDetails.name+", It would be pleasure to organise a web meeting with my team. Kindly call us to schedule the web meeting.")
        templateList.add("Dear "+leadDetails.name+", Kindly watch all the videos of the  Teacher's professional development training curriculum and discover the world class faculty.")
        templateList.add("Dear "+leadDetails.name+", Our best package offer to you will expire soon. We request you vail the offer made to you at the earliest.")
        templateList.add("Dear "+leadDetails.name+", Please feel free to call to check with us if you need any clarification in our package given to you.")
        templateList.add("Dear "+leadDetails.name+", Hope you liked our videos,having the best and unique methodology to support your school for the success.")
        templateList.add("Dear "+leadDetails.name+", Montessori activities nurture the brain development of the children.Kindly watch the Montessori Teacher's Training video to know more.")
        templateList.add("Dear "+leadDetails.name+", Get extra revenue by running our affiliation courses VEDIC MATHS and MONESSORI TEACHER'S TRAINING. To know more call us.")
        templateList.add("Dear "+leadDetails.name+", Kindly watch the video on the unique technology based montessori materials.")
        templateList.add("Dear "+leadDetails.name+", Make your preschool successful without paying any royalty charges and observe  the benefits of joining the Bright Edge Affiliate progarm.")
        templateList.add("Dear "+leadDetails.name+", Kindly let us know if you want to experience the Multiple Intelligence App - enchancing the overall development of a child")
        templateList.add("Dear "+leadDetails.name+", Gentle remainder for the scheduled  web meeting  in the next 30 minutes.")
        templateList.add("Dear "+leadDetails.name+", Over 500+, Bright Concept teacher smart class video topics, To watch demo videos kindly contact us")
        templateList.add("Dear "+leadDetails.name+", 10 gems of the Bright apps at your finger tips in affordable prices.")

        val smsTemplatesAdapter = SMSTemplateAdapter(this@CustomerActivitiesActivity,templateNames, templateList)
        lvSMSTemplate.adapter = smsTemplatesAdapter

        lvSMSTemplate.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            dialog.setContentView(R.layout.sms_confirmation_dialog)
            dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.setCancelable(false)
            val tvTitle = dialog.findViewById<TextView>(R.id.tvMessage)
            tvTitle.text = "Are you sure, you want to send this message?"
            val tvCSMS = dialog.findViewById<TextView>(R.id.tvCSMS)
            tvCSMS.text = templateList[position]
            val tvYes = dialog.findViewById<TextView>(R.id.tvYes)
            tvYes.setOnClickListener {
                UpdateCallSMS().execute("SMS: "+ templateNames[position])
                sendSMS(phoneNumber, templateList[position])
                dialog.dismiss()
            }
            val tvNo = dialog.findViewById<TextView>(R.id.tvNo)
            tvNo.setOnClickListener { dialog.dismiss() }
        }
        dialog.show()
    }

    private fun smsPermissionCheck(phoneNumber: String){

        val permissionCheck = ContextCompat.checkSelfPermission(this@CustomerActivitiesActivity, android.Manifest.permission.SEND_SMS)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            showTemplates(phoneNumber)
        } else {

            val dialogBuilder = AlertDialog.Builder(this@CustomerActivitiesActivity)
            val inflater = layoutInflater
            val dialogView = inflater.inflate(R.layout.permission_dialog, null)
            dialogBuilder.setView(dialogView)

            val b = dialogBuilder.create()

            val dialog_message = dialogView.findViewById<View>(R.id.dialog_message) as TextView
            dialog_message.text = "This app needs SMS permission for sending sms to customers."
            val pCancel = dialogView.findViewById<View>(R.id.pCancel) as TextView
            val pSettings = dialogView.findViewById<View>(R.id.pSettings) as TextView
            val pOk = dialogView.findViewById<View>(R.id.pOk) as TextView
            pCancel.setOnClickListener {
                b.dismiss()
            }
            pSettings.setOnClickListener {
                b.dismiss()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            pOk.setOnClickListener {
                b.dismiss()
                ActivityCompat.requestPermissions(this@CustomerActivitiesActivity, arrayOf(android.Manifest.permission.SEND_SMS), 3)
            }

            b.show()

        }
    }

    private fun sendSMS(phoneNumber: String, message: String){
        SmsManager.getDefault().sendTextMessage(phoneNumber, null, message, null, null)
        basicSnackBar("SMS sent successfully to "+leadDetails.name)
    }

    private fun videoTemplates(phoneNumber: String) {

        dialog.setContentView(R.layout.videos_template)
        dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        val spLanguage = dialog.findViewById<Spinner>(R.id.spLanguage)
        val languageItem = ArrayList<String>()
        languageItem.add("English")
        //languageItem.add("Kannada")
        val statusAdapter = SpinnerAdapter(this, R.layout.status_item, languageItem)
        spLanguage.adapter = statusAdapter


        val videoList = ArrayList<String>()
        videoList.add("https://youtu.be/sQAYSHcSqx8")
        videoList.add("https://youtu.be/f8tpuCYQRaE")
        videoList.add("https://youtu.be/P7do4rDQIo4")
        videoList.add("https://youtu.be/gKGBE9H4c0U")
        videoList.add("https://youtu.be/7Y3n5QZ4yz0")
        videoList.add("https://youtu.be/m4yVuN4sF4o")
        videoList.add("https://youtu.be/cPGGuZmb0Gs")
        videoList.add("https://youtu.be/7x5Xx4ofqzA")
        videoList.add("https://youtu.be/_RVu6v71yJE")
        videoList.add("https://youtu.be/ZM55vVqzOo0")
        videoList.add("https://youtu.be/E83D-sydmn8")
        videoList.add("https://youtu.be/XqU2QCvR-XY")
        videoList.add("https://youtu.be/ziorq1mRYLo")
        videoList.add("https://youtu.be/5IVNJO_MWPg")
        videoList.add("https://youtu.be/ozQfrPw4eFQ")
        videoList.add("https://youtu.be/FDUmGHhSNq8")
        videoList.add("https://youtu.be/85Qr4AJmD4U")
        videoList.add("https://youtu.be/i6nPbzn6tqE")
        videoList.add("https://youtu.be/RSsTCfhjFV4")
        videoList.add("https://youtu.be/jx2jWGTGrm0")

        val videoMap = HashMap<String, ArrayList<String>>()
        videoMap.put("English", videoList)

        spLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>, selectedItemView: View, position: Int, id: Long) {
                /*videoList.clear()
                videoList.addAll(videoMap[languageItem[position]] as ArrayList<String>)*/
            }

            override fun onNothingSelected(parentView: AdapterView<*>) {}
        }

        val lvVideoTemplate = dialog.findViewById<ListView>(R.id.lvVideoTemplate)

        val tvCancel = dialog.findViewById<TextView>(R.id.tvCancel)
        tvCancel.setOnClickListener { dialog.dismiss() }

        val videoNames = ArrayList<String>()
        videoNames.add("Benefits Of EduBEAM Partnership")
        videoNames.add("Highlights of EduBEAM Partnership")
        videoNames.add("Our Smart Support System")
        videoNames.add("Our smart books")
        videoNames.add("Montessori Activities")
        videoNames.add("Montessori Curriculum")
        videoNames.add("Smart Class Curriculum")
        videoNames.add("Activity Calendar")
        videoNames.add("Montessori Teacher Training")
        videoNames.add("Vedic MathsUp")
        videoNames.add("Salient Features Of EduBEAM Programme")
        videoNames.add("Preschool Owners Training")
        videoNames.add("Typical Preschool ROI")
        videoNames.add("Teacher's Professional Development Training")
        videoNames.add("Teacher's Professional Development Training Overview")
        videoNames.add("Technology on Montessori Materials")
        videoNames.add("Bright Kid Montessori House Program Curriculum")
        videoNames.add("Benefits of The Montessori Methodology")
        videoNames.add("My Bright Books")
        videoNames.add("Classroom Curriculum")

        val videoTemplatesAdapter = VideoTemplatesAdapter(this@CustomerActivitiesActivity, videoNames)
        lvVideoTemplate.adapter = videoTemplatesAdapter

        lvVideoTemplate.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            val message = "Hi " + leadDetails.name + ", Here is our video about " + videoNames[position] + "\n" + videoList[position]
            UpdateCallSMS().execute("Video: " + videoNames[position])
            sendWhatsApp(phoneNumber, message)
        }
    }

    private fun pdfTemplates(phoneNumber: String){

        dialog.setContentView(R.layout.pdf_template)
        dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        val pdfList = ArrayList<String>()
        pdfList.add("https://bit.ly/2o2A9da")
        pdfList.add("https://bit.ly/2LkCB8b")
        pdfList.add("https://bit.ly/2o3rmaM")
        pdfList.add("https://bit.ly/2PyE6TI")
        pdfList.add("https://bit.ly/2o0wDA7")
        pdfList.add("https://bit.ly/2o4slaW")
        pdfList.add("https://bit.ly/2o2H0n9")
        pdfList.add("https://bit.ly/2PqHNe1")
        pdfList.add("https://bit.ly/2Py5IYP")
        pdfList.add("https://bit.ly/2w81QWr")
        pdfList.add("https://bit.ly/2o1wNXN")
        pdfList.add("https://bit.ly/2MwNcT6")
        pdfList.add("https://bit.ly/2o40KXq")
        pdfList.add("https://bit.ly/2MwrcI1")
        pdfList.add("https://bit.ly/2BDYkbE")
        pdfList.add("https://bit.ly/2P0fnGr")
        pdfList.add("https://bit.ly/2MKehS0")
        pdfList.add("https://bit.ly/2LgojFC")

        val lvPdfTemplate = dialog.findViewById<ListView>(R.id.lvPdfTemplate)

        val tvCancel = dialog.findViewById<TextView>(R.id.tvCancel)
        tvCancel.setOnClickListener { dialog.dismiss() }

        val pdfNames = ArrayList<String>()
        pdfNames.add("Advantages Of Bright Concept Teacher")
        pdfNames.add("Montessori Materials")
        pdfNames.add("Pre-school Owner's Training")
        pdfNames.add("Montessori Teachers Training")
        pdfNames.add("Professional Development Training For Teachers")
        pdfNames.add("Bright Apps")
        pdfNames.add("Blended Curriculum")
        pdfNames.add("Activity Calendar")
        pdfNames.add("My Bright And Smart Books")
        pdfNames.add("Smart Class Activities")
        pdfNames.add("Montessori Activities")
        pdfNames.add("Standard Package")
        pdfNames.add("Basic Package")
        pdfNames.add("Summer Camp Training")
        pdfNames.add("Class Room Activities")
        pdfNames.add("Extra Curricular Activities")
        pdfNames.add("Multiple Benefits Of Edubeam")
        pdfNames.add("Digital Teaching Packages")

        val pdfTemplatesAdapter = PdfTemplatesAdapter(this@CustomerActivitiesActivity, pdfNames)
        lvPdfTemplate.adapter = pdfTemplatesAdapter

        lvPdfTemplate.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            val message = "Hi "+leadDetails.name+", Here is our pdf file about "+pdfNames[position]+"\n"+pdfList[position]
            UpdateCallSMS().execute("Pdf: "+pdfNames[position])
            sendWhatsApp(phoneNumber, message)
        }
    }

    private fun sendWhatsApp(phNumber: String, message: String) {

        val ph = "+91"+phNumber
        val packageManager = packageManager
        val i = Intent(Intent.ACTION_VIEW)
        try {
            val url = "https://api.whatsapp.com/send?phone="+ ph +"&text=" + URLEncoder.encode(message, "UTF-8")
            i.`package` = "com.whatsapp"
            i.data = Uri.parse(url)
            if (i.resolveActivity(packageManager) != null) {
                startActivity(i)
            }
        } catch (e: Exception){
            e.printStackTrace()
        }

    }

    private fun telephonePermissionCheck(phoneNumber: String){

        val permissionCheck = ContextCompat.checkSelfPermission(this@CustomerActivitiesActivity, android.Manifest.permission.CALL_PHONE)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            UpdateCallSMS().execute("Called")
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:"+phoneNumber)
            startActivity(intent)
        } else {

            val dialogBuilder = AlertDialog.Builder(this@CustomerActivitiesActivity)
            val inflater = layoutInflater
            val dialogView = inflater.inflate(R.layout.permission_dialog, null)
            dialogBuilder.setView(dialogView)

            val b = dialogBuilder.create()

            val dialog_message = dialogView.findViewById<View>(R.id.dialog_message) as TextView
            dialog_message.text = "This app needs Telephone permission for making calls to customers."
            val pCancel = dialogView.findViewById<View>(R.id.pCancel) as TextView
            val pSettings = dialogView.findViewById<View>(R.id.pSettings) as TextView
            val pOk = dialogView.findViewById<View>(R.id.pOk) as TextView
            pCancel.setOnClickListener {
                b.dismiss()
            }
            pSettings.setOnClickListener {
                b.dismiss()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            pOk.setOnClickListener {
                b.dismiss()
                ActivityCompat.requestPermissions(this@CustomerActivitiesActivity, arrayOf(android.Manifest.permission.CALL_PHONE), 3)
            }

            b.show()

        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun updateNFD(){
        dialog.setContentView(R.layout.update_nfd_dialog)
        dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        val etTodayUpdate = dialog.findViewById<EditText>(R.id.etTodayUpdate)
        //val etFollowUpdate = dialog.findViewById<EditText>(R.id.etFollowUpdate)
        val tvNFD = dialog.findViewById<TextView>(R.id.tvNFD)
        tvNFD.setOnClickListener {
            val myCalendar = Calendar.getInstance()
            val date = DatePickerDialog.OnDateSetListener { view, year, monthOfYear, dayOfMonth ->
                myCalendar.set(Calendar.YEAR, year)
                myCalendar.set(Calendar.MONTH, monthOfYear)
                myCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                val myFormat = "dd MMM, yyyy"
                val sdf = SimpleDateFormat(myFormat, Locale.US)

                tvNFD.text = sdf.format(myCalendar.time)
            }
            DatePickerDialog(this, date, myCalendar.get(Calendar.YEAR), myCalendar.get(Calendar.MONTH), myCalendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        val spStatus = dialog.findViewById<Spinner>(R.id.spStatus)
        val statusItem = ArrayList<String>()
        statusItem.add("open")
        statusItem.add("close")
        //spStatus.setBackgroundColor(resources.getColor(R.color.colorPrimary))
        val statusAdapter = SpinnerAdapter(this, R.layout.status_item, statusItem)
        spStatus.adapter = statusAdapter
        spStatus.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView:AdapterView<*>, selectedItemView:View, position:Int, id:Long) {
                if (statusItem[position] == "close") {
                    tvNFD.visibility = View.GONE
                } else {
                    tvNFD.visibility = View.VISIBLE
                }
            }
            override fun onNothingSelected(parentView:AdapterView<*>) { }
        }

        val spLead = dialog.findViewById<Spinner>(R.id.spLead)
        /*val leadItem = ArrayList<String>()
        leadItem.add("visited")
        leadItem.add("not visited")
        leadItem.add("converted")
        leadItem.add("lost")
        val leadAdapter = SpinnerAdapter(this, R.layout.status_item, leadItem)
        spLead.adapter = leadAdapter
        spLead.setSelection(leadItem.indexOf(customerDetails.leadStage))*/

        val ratingBar = dialog.findViewById<RatingBar>(R.id.ratingBar)
        ratingBar.rating = leadDetails.rating!!.toFloat()

        val btnUpdate = dialog.findViewById<Button>(R.id.btnUpdateNFD)
        btnUpdate.setOnClickListener {
            llRating.removeAllViews()
            val ratingPoint = ratingBar.rating.toInt()
            for (i in 0 until ratingPoint)
            {
                val imageView = ImageView(baseContext)
                imageView.setImageResource(R.drawable.icon_rating)
                imageView.layoutParams = LinearLayout.LayoutParams(40, 40)
                llRating.addView(imageView)
            }
            if (spStatus.selectedItem == "open") {
                if (TextUtils.isEmpty(etTodayUpdate.text.toString()) ||
                        TextUtils.isEmpty(tvNFD.text.toString())) {
                    Toast.makeText(baseContext, "Please enter all details", Toast.LENGTH_SHORT).show()
                } else {
                    val myCalendar = Calendar.getInstance()
                    val tDate = myCalendar.time

                    val originalFormat = SimpleDateFormat("dd MMM, yyyy", Locale.ENGLISH)
                    val targetFormat = SimpleDateFormat("yyyy/MM/dd")
                    val date = originalFormat.parse(tvNFD.text.toString())
                    val dd = targetFormat.format(date)

                    val year = dd.substring(0,4)
                    val month = dd.substring(5,7)
                    val day = dd.substring(8,10)

                    myCalendar.set(Calendar.YEAR, year.toInt())
                    myCalendar.set(Calendar.MONTH, month.toInt()-1)
                    myCalendar.set(Calendar.DAY_OF_MONTH, day.toInt())

                    val nfdDate = myCalendar.time

                    if (nfdDate.before(tDate) || nfdDate == tDate){
                        Toast.makeText(baseContext, "Please check your nfd", Toast.LENGTH_SHORT).show()
                    } else {
                        UpdateNFD().execute(spStatus.selectedItem.toString(), etTodayUpdate.text.toString(), ratingPoint.toString(), tvNFD.text.toString())
                    }
                }
            } else {
                if (TextUtils.isEmpty(etTodayUpdate.text.toString())) {
                    Toast.makeText(baseContext, "Please enter closing reason for enquiry", Toast.LENGTH_SHORT).show()
                } else {
                    dialog.setContentView(R.layout.conformation_dialog)
                    dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
                    dialog.setCancelable(false)
                    val tvCSMS = dialog.findViewById<TextView>(R.id.tvCSMS)
                    tvCSMS.text = "Are you sure, you want to close this enquiry."
                    val tvYes = dialog.findViewById<TextView>(R.id.tvYes)
                    tvYes.setOnClickListener {
                        //llOptions.visibility = View.GONE
                        UpdateNFD().execute(spStatus.selectedItem.toString(), etTodayUpdate.text.toString(), ratingPoint.toString())
                    }
                    val tvNo = dialog.findViewById<TextView>(R.id.tvNo)
                    tvNo.setOnClickListener { dialog.dismiss() }
                }
            }
        }
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)
        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }


    @SuppressLint("StaticFieldLeak")
    inner class UpdateNFD : AsyncTask<String, Void, Boolean>() {

        private var leadsDo = LeadsDO()
        private val progressDialog = ProgressDialog(this@CustomerActivitiesActivity, R.style.MyAlertDialogStyle)

        @SuppressLint("SimpleDateFormat")
        override fun onPreExecute() {

            leadsDo = leadDetails

            progressDialog.setMessage("Updating NFD, please wait...")
            progressDialog.setCancelable(false)
            progressDialog.show()

        }

        @SuppressLint("SimpleDateFormat")
        override fun doInBackground(vararg string: String?): Boolean {

            val myCalendar = Calendar.getInstance()
            val myFormat = "yyyy/MM/dd"
            val sdf = SimpleDateFormat(myFormat, Locale.US)
            val todayDate = sdf.format(myCalendar.time)

            val nfdVal = leadsDo.nfd
            val nnfd = HashMap<String, String>()
            val nfdKeys = nfdVal!!.keys
            for (key1 in nfdKeys) {
                nnfd.put(key1, nfdVal[key1].toString())
            }

            var tempNFD = "null"

            val newStatus = string[0]

            if (newStatus == "open"){
                val originalFormat = SimpleDateFormat("dd MMM, yyyy", Locale.ENGLISH)
                val targetFormat = SimpleDateFormat("yyyy/MM/dd")
                val date = originalFormat.parse(string[3].toString())
                val nfdDate = targetFormat.format(date)

                tempNFD = nfdDate

                nnfd.put(nfdDate, " ")
                if (!dateList.contains(nfdDate)) {
                    dateList.add(nfdDate)
                }
                fActivities.put(nfdDate, " ")
            }

            //nnfd.put(todayDate, string[1].toString())
            //fActivities.put(todayDate, string[1].toString())

            if (!dateList.contains(todayDate)){
                dateList.add(todayDate)
            }

            if (nnfd[todayDate] == " " || nnfd[todayDate] == null) {
                nnfd.put(todayDate, string[1].toString())
                fActivities.put(todayDate, string[1].toString())
            } else {
                nnfd.put(todayDate, nnfd[todayDate] + "\n" + string[1])
                fActivities.put(todayDate, fActivities[todayDate] + "\n" + string[1])
            }

            val newDateList = ArrayList<String>()

            for (i in 0 until dateList.size){
                if (fActivities[dateList[i]] == " " && dateList[i] != tempNFD){
                    fActivities.remove(dateList[i])
                } else {
                    newDateList.add(dateList[i])
                }
            }

            dateList.clear()
            dateList.addAll(newDateList)

            val newNfd = nnfd
            val nNfd = HashMap<String, String>()
            val newKeys = newNfd.keys
            for (key1 in newKeys) {
                val key = key1
                if (key == tempNFD){
                    nNfd.put(key, newNfd[key].toString())
                } else {
                    if (newNfd[key].toString() != " ") {
                        nNfd.put(key, newNfd[key].toString())
                    }
                }
            }

            /*if (string[0] == "open"){
                val originalFormat = SimpleDateFormat("dd MMM, yyyy", Locale.ENGLISH)
                val targetFormat = SimpleDateFormat("yyyy/MM/dd")
                val date = originalFormat.parse(string[3].toString())
                val nfdDate = targetFormat.format(date)
                nnfd.put(nfdDate, string[2].toString())
                if (!dateList.contains(nfdDate)) {
                    dateList.add(nfdDate)
                }
                fActivities.put(nfdDate, string[2].toString())
            }

            if (nnfd[todayDate] != null) {
                nnfd.put(todayDate, nnfd[todayDate] +"\n"+ string[1])
                fActivities.put(todayDate, nnfd[todayDate].toString())
            } else {
                nnfd.put(todayDate, string[1].toString())
                dateList.add(todayDate)
                fActivities.put(todayDate, string[1].toString())
            }*/

            leadsDo.status = newStatus.toString()
            leadsDo.rating = string[2].toString()
            //leadsDo.leadStage = string[3].toString()
            leadsDo.nfd = nNfd

            val awsProvider = AWSProvider()
            val dynamoDBClient = AmazonDynamoDBClient(awsProvider.getCredentialsProvider(baseContext))
            dynamoDBClient.setRegion(Region.getRegion(Regions.US_EAST_1))
            val dynamoDBMapper = DynamoDBMapper.builder()
                    .dynamoDBClient(dynamoDBClient)
                    .awsConfiguration(AWSMobileClient.getInstance().configuration)
                    .build()
            return try {
                dynamoDBMapper.save(leadsDo)
                true
            } catch (e: AmazonClientException) {
                basicSnackBar("Network connection error!!")
                false
            }

        }

        override fun onPostExecute(result: Boolean?) {
            progressDialog.dismiss()
            if (result!!) {
                leadDetails = leadsDo
                Collections.sort(dateList, CustomerDateComparator())
                //Collections.reverse(dateList)
                user.notifyDataSetChanged()
                lvActivities.smoothScrollToPosition(dateList.size-1)
                if (dialog.isShowing){
                    dialog.dismiss()
                }
                mainActivity!!.updateStatus = "update"
            }
        }

    }

    @SuppressLint("StaticFieldLeak")
    inner class UpdateCallSMS : AsyncTask<String, Void, Boolean>() {

        private var leadsDo = LeadsDO()
        private val progressDialog = ProgressDialog(this@CustomerActivitiesActivity, R.style.MyAlertDialogStyle)

        @SuppressLint("SimpleDateFormat")
        override fun onPreExecute() {

            leadsDo = leadDetails

            progressDialog.setMessage("Updating NFD, please wait...")
            progressDialog.setCancelable(false)
            progressDialog.show()

        }

        @SuppressLint("SimpleDateFormat")
        override fun doInBackground(vararg string: String?): Boolean {

            val myCalendar = Calendar.getInstance()
            val myFormat = "yyyy/MM/dd"
            val sdf = SimpleDateFormat(myFormat, Locale.US)
            val todayDate = sdf.format(myCalendar.time)

            val nfdVal = leadsDo.nfd
            val nnfd = HashMap<String, String>()
            val nfdKeys = nfdVal!!.keys
            for (key1 in nfdKeys) {
                nnfd.put(key1, nfdVal[key1].toString())
            }

            if (!dateList.contains(todayDate)){
                dateList.add(todayDate)
            }

            if (nnfd[todayDate] == " " || nnfd[todayDate] == null) {
                nnfd.put(todayDate, string[0].toString())
                fActivities.put(todayDate, string[0].toString())
            } else {
                nnfd.put(todayDate, nnfd[todayDate] + "\n" + string[0])
                fActivities.put(todayDate, fActivities[todayDate] + "\n" + string[0])
            }

            val newDateList = ArrayList<String>()

            for (i in 0 until dateList.size){
                newDateList.add(dateList[i])
            }

            dateList.clear()
            dateList.addAll(newDateList)

            val newNfd = nnfd
            val nNfd = HashMap<String, String>()
            val newKeys = newNfd.keys
            for (key1 in newKeys) {
                nNfd.put(key1, newNfd[key1].toString())
            }

            leadsDo.nfd = nNfd

            val awsProvider = AWSProvider()
            val dynamoDBClient = AmazonDynamoDBClient(awsProvider.getCredentialsProvider(baseContext))
            dynamoDBClient.setRegion(Region.getRegion(Regions.US_EAST_1))
            val dynamoDBMapper = DynamoDBMapper.builder()
                    .dynamoDBClient(dynamoDBClient)
                    .awsConfiguration(AWSMobileClient.getInstance().configuration)
                    .build()
            return try {
                dynamoDBMapper.save(leadsDo)
                true
            } catch (e: AmazonClientException) {
                basicSnackBar("Network connection error!!")
                false
            }

        }

        override fun onPostExecute(result: Boolean?) {
            progressDialog.dismiss()
            if (result!!) {
                leadDetails = leadsDo
                Collections.sort(dateList, CustomerDateComparator())
                //Collections.reverse(dateList)
                user.notifyDataSetChanged()
                lvActivities.smoothScrollToPosition(dateList.size-1)
                if (dialog.isShowing){
                    dialog.dismiss()
                }
                mainActivity!!.updateStatus = "update"
            }
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

    inner class AgentsNameComparator : Comparator<AgentsDO> {
        override fun compare(obj1: AgentsDO, obj2: AgentsDO): Int {
            return obj1.name.toString().toLowerCase().compareTo(obj2.name.toString().toLowerCase())
        }
    }

    inner class ActivitiesAdapter : BaseAdapter {

        private var dateList1 = ArrayList<String>()
        private var fActivities = HashMap<String, String>()
        private var context: Context? = null
        private var inflater: LayoutInflater? = null

        constructor(context: Context, dateList: ArrayList<String>, fActivities: HashMap<String, String>) : super() {
            this.dateList1 = dateList
            this.fActivities = fActivities
            this.context = context
            inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        }

        @SuppressLint("ViewHolder", "InflateParams", "SimpleDateFormat", "ResourceAsColor")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {

            val holder = Holder()
            val rowView = inflater!!.inflate(R.layout.activities_item, null)
            holder.tvDate = rowView.findViewById(R.id.tvDate)
            val originalFormat = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)
            val targetFormat = SimpleDateFormat("dd MMM, yyyy")
            val date = originalFormat.parse(dateList1[position])
            holder.tvDate!!.text = targetFormat.format(date)
            holder.tvActivity = rowView.findViewById(R.id.tvActivity)

            val newText = holder.tvActivity as TextView

            val activity = fActivities[dateList1[position]]
            val tokens = StringTokenizer(activity, "\n")
            val spannable = SpannableString(activity)
            var newStr = ""
            for (i in 0 until tokens.countTokens()){

                val str = tokens.nextToken()
                if (str.contains("Called")){
                    spannable.setSpan(ForegroundColorSpan(Color.GREEN), i+newStr.length, (newStr + str).length+i, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    newStr += str
                } else if (str.contains("SMS")){
                    spannable.setSpan(ForegroundColorSpan(Color.RED), i+newStr.length, (newStr + str).length+i, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    newStr += str
                } else if (str.contains("Video")){
                    spannable.setSpan(ForegroundColorSpan(Color.GRAY), i+newStr.length, (newStr + str).length+i, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    newStr += str
                } else if (str.contains("Pdf")){
                    spannable.setSpan(ForegroundColorSpan(Color.MAGENTA), i+newStr.length, (newStr + str).length+i, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    newStr += str
                }else {
                    spannable.setSpan(ForegroundColorSpan(Color.BLUE), i+newStr.length, (newStr + str).length+i, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    newStr += str
                }

            }

            newText.text = spannable

            return rowView
        }

        override fun getItem(position: Int): Any {
            return dateList1[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return dateList1.size
        }

        private inner class Holder {
            internal var tvDate: TextView? = null
            internal var tvActivity: TextView? = null
        }

    }

    class SMSTemplateAdapter : BaseAdapter {

        private var templateList = ArrayList<String>()
        private var templateNames = ArrayList<String>()
        private var inflater: LayoutInflater? = null

        constructor(context: Context, templateNames: ArrayList<String>, templateList: ArrayList<String>) : super() {
            this.templateList = templateList
            this.templateNames = templateNames
            inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        }

        @SuppressLint("ViewHolder", "InflateParams")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {

            val holder = Holder()
            val rowView = inflater!!.inflate(R.layout.sms_template_item, null)
            holder.tvTemplateName = rowView.findViewById(R.id.tvTemplateName)
            holder.tvTemplateName!!.text = templateNames[position]
            holder.tvTemplateMessage = rowView.findViewById(R.id.tvTemplateMessage)
            holder.tvTemplateMessage!!.text = templateList[position]

            return rowView
        }

        override fun getItem(position: Int): Any {
            return templateList[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return templateList.size
        }

        private inner class Holder {
            internal var tvTemplateName: TextView? = null
            internal var tvTemplateMessage: TextView? = null
        }
    }

    class VideoTemplatesAdapter : BaseAdapter {

        private var videoNames = ArrayList<String>()
        private var inflater: LayoutInflater? = null

        constructor(context: Context, videoNames: ArrayList<String>) : super() {
            this.videoNames = videoNames
            inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        }

        @SuppressLint("ViewHolder", "InflateParams")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {

            val holder = Holder()
            val rowView = inflater!!.inflate(R.layout.videos_template_item, null)
            holder.tvVideoName = rowView.findViewById(R.id.tvVideoName)
            holder.tvVideoName!!.text = videoNames[position]

            return rowView
        }

        override fun getItem(position: Int): Any {
            return videoNames[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return videoNames.size
        }

        private inner class Holder {
            internal var tvVideoName: TextView? = null
        }
    }

    class PdfTemplatesAdapter : BaseAdapter {

        private var pdfNames = ArrayList<String>()
        private var inflater: LayoutInflater? = null

        constructor(context: Context, pdfNames: ArrayList<String>) : super() {
            this.pdfNames = pdfNames
            //this.context = context
            inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        }

        @SuppressLint("ViewHolder", "InflateParams")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {

            val holder = Holder()
            val rowView = inflater!!.inflate(R.layout.pdf_template_item, null)
            holder.tvPdfName = rowView.findViewById(R.id.tvPdfName)
            holder.tvPdfName!!.text = pdfNames[position]

            return rowView
        }

        override fun getItem(position: Int): Any {
            return pdfNames[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return pdfNames.size
        }

        private inner class Holder {
            internal var tvPdfName: TextView? = null
        }
    }

    private fun refreshMain(){
        if (mainActivity!!.updateStatus == "update"){
            mainActivity!!.refreshOnUpdate()
            finish()
        } else {
            super.onBackPressed()
        }
    }

    override fun onBackPressed() {
        refreshMain()
    }

    override fun onSupportNavigateUp(): Boolean {
        refreshMain()
        return false
    }

}
