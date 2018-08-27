package sagsaguz.edubeam.utils

import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBRangeKey
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable
import java.io.Serializable

@DynamoDBTable(tableName = "edubeam-mobilehub-411476929-Agents")
class AgentsDO : Serializable {
    @get:DynamoDBHashKey(attributeName = "phone")
    @get:DynamoDBAttribute(attributeName = "phone")
    var phone: String? = null
    @get:DynamoDBRangeKey(attributeName = "emailId")
    @get:DynamoDBAttribute(attributeName = "emailId")
    var emailId: String? = null
    @get:DynamoDBAttribute(attributeName = "accessType")
    var accessType: String? = null
    @get:DynamoDBAttribute(attributeName = "name")
    var name: String? = null
    @get:DynamoDBAttribute(attributeName = "password")
    var password: String? = null

}
