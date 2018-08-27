package sagsaguz.edubeam.utils

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable
import java.io.Serializable

@DynamoDBTable(tableName = "edubeam-mobilehub-411476929-Leads")
class LeadsDO : Serializable {
    @get:DynamoDBHashKey(attributeName = "phone")
    @get:DynamoDBAttribute(attributeName = "phone")
    var phone: String? = null
    @get:DynamoDBRangeKey(attributeName = "emailId")
    @get:DynamoDBAttribute(attributeName = "emailId")
    var emailId: String? = null
    @get:DynamoDBAttribute(attributeName = "NFD")
    var nfd: Map<String, String>? = null
    @get:DynamoDBAttribute(attributeName = "assignedTo")
    var assignedTo: String? = null
    @get:DynamoDBAttribute(attributeName = "createdDate")
    var createdDate: String? = null
    @get:DynamoDBAttribute(attributeName = "leadScore")
    var leadScore: String? = null
    @get:DynamoDBAttribute(attributeName = "name")
    var name: String? = null
    @get:DynamoDBAttribute(attributeName = "state")
    var state: String? = null
    @get:DynamoDBAttribute(attributeName = "status")
    var status: String? = null
    @get:DynamoDBAttribute(attributeName = "rating")
    var rating: String? = null
    @get:DynamoDBAttribute(attributeName = "leadId")
    var leadId: String? = null
    @get:DynamoDBAttribute(attributeName = "leadType")
    var leadType: String? = null



}
