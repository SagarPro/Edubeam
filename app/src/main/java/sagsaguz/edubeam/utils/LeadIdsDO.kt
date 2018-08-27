package sagsaguz.edubeam.utils

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIndexHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBIndexRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable
import java.io.Serializable

@DynamoDBTable(tableName = "edubeam-mobilehub-411476929-LeadIds")
class LeadIdsDO : Serializable {
    @get:DynamoDBHashKey(attributeName = "emailId")
    @get:DynamoDBAttribute(attributeName = "emailId")
    var emailId: String? = null
    @get:DynamoDBRangeKey(attributeName = "phone")
    @get:DynamoDBAttribute(attributeName = "phone")
    var phone: String? = null
    @get:DynamoDBAttribute(attributeName = "leadId")
    var leadId: List<String>? = null

}
