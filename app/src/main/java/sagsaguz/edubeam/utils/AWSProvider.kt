package sagsaguz.edubeam.utils

import android.content.Context
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.regions.Regions

class AWSProvider {

    fun getCredentialsProvider(context: Context): CognitoCachingCredentialsProvider {
        return CognitoCachingCredentialsProvider(
                context,
                "us-east-1:05ca684d-681a-470e-9b63-8deca1436cf7",
                Regions.US_EAST_1
        )
    }

}
