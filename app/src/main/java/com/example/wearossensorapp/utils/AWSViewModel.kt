package com.example.wearossensorapp.utils

import android.content.Context
import android.content.SharedPreferences
import android.support.wearable.activity.WearableActivity
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.mobileconnectors.iot.*
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.iot.AWSIotClient
import com.amazonaws.services.iot.model.AttachPrincipalPolicyRequest
import com.amazonaws.services.iot.model.CreateKeysAndCertificateRequest
import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult
import com.example.wearossensorapp.MainActivity
import java.io.File
import java.security.KeyStore
import java.util.*

class AWSViewModel : ViewModel(){
    
    var mIotAndroidClient: AWSIotClient? = null
    var mqttManager: AWSIotMqttManager? = null
    var keystorePath: String? = null
    var keystoreName: String? = null
    var keystorePassword: String? = null
    var certificateId: String? = null
    var credentialsProvider: CognitoCachingCredentialsProvider? = null
    var activity : MainActivity? = null
    private var mPreferences: SharedPreferences? = null

    private val _connectionStatus = MutableLiveData<String>()
    val connectionStatus: LiveData<String>
        get() = _connectionStatus

    private val _clientID = MutableLiveData<String>()
    val clientID : LiveData<String>
        get() = _clientID

    private val _clientKeyStore = MutableLiveData<KeyStore>()
    val clientKeyStore: LiveData<KeyStore>
        get() = _clientKeyStore

    companion object {
        val TAG = AWSViewModel::class.java.canonicalName

        // --- Constants to modify per your configuration ---
        // IoT endpoint
        // AWS Iot CLI describe-endpoint call returns: XXXXXXXXXX.iot.<region>.amazonaws.com
        private const val CUSTOMER_SPECIFIC_ENDPOINT = ""

        // Cognito pool ID. For this app, pool needs to be unauthenticated pool with
        // AWS IoT permissions.
        private const val COGNITO_POOL_ID = ""

        // Name of the AWS IoT policy to attach to a newly created certificate
        private const val AWS_IOT_POLICY_NAME = ""

        // Region of AWS IoT
        private val MY_REGION = Regions.CA_CENTRAL_1

        // Filename of KeyStore file on the filesystem
        private const val KEYSTORE_NAME = "iot_keystore"

        // Password for the private key in the KeyStore
        private const val KEYSTORE_PASSWORD = "password"

        // Certificate and key aliases in the KeyStore
        private const val CERTIFICATE_ID = "default"

        val TOPIC_NAME = "topic1"

        val sharedPrefFileName = "com.example.android.hellosharedprefs"

    }

    init {
        _connectionStatus.value = "Connecting..."
        _clientID.value = ""
        _clientKeyStore.value = null
    }

    /**
     * Setup MQTT client for connection. 
     */
    fun setupIOTCore(applicationContext : Context, filesDir : File, activity: MainActivity){
        Log.d(TAG, "setupIOTCore: ")
        this.activity = activity
        mPreferences = applicationContext.getSharedPreferences(sharedPrefFileName, WearableActivity.MODE_PRIVATE);
        // MQTT client IDs are required to be unique per AWS IoT account.
        // This UUID is "practically unique" but does not _guarantee_
        // uniqueness.
        _clientID.value = mPreferences?.getString("Device ID", UUID.randomUUID().toString())

        // Initialize the AWS Cognito credentials provider
        credentialsProvider = CognitoCachingCredentialsProvider(
                applicationContext,  // context
                COGNITO_POOL_ID,  // Identity Pool ID
                MY_REGION // Region
        )
        val region = Region.getRegion(MY_REGION)

        // MQTT Client
        mqttManager = AWSIotMqttManager(_clientID.value, CUSTOMER_SPECIFIC_ENDPOINT)

        // Set keepalive to 10 seconds.  Will recognize disconnects more quickly but will also send
        // MQTT pings every 10 seconds.
        mqttManager!!.keepAlive = 10

        // Set Last Will and Testament for MQTT.  On an unclean disconnect (loss of connection)
        // AWS IoT will publish this message to alert other clients.
        val lwt = AWSIotMqttLastWillAndTestament("my/lwt/topic",
                "Android client lost connection", AWSIotMqttQos.QOS0)
        mqttManager!!.mqttLastWillAndTestament = lwt

        // IoT Client (for creation of certificate if needed)
        mIotAndroidClient = AWSIotClient(credentialsProvider)
        mIotAndroidClient!!.setRegion(region)
        keystorePath = filesDir.path
        keystoreName = KEYSTORE_NAME
        keystorePassword = KEYSTORE_PASSWORD
        certificateId = CERTIFICATE_ID
        // To load cert/key from keystore on filesystem
        try {
            if (AWSIotKeystoreHelper.isKeystorePresent(keystorePath, keystoreName)) {
                if (AWSIotKeystoreHelper.keystoreContainsAlias(certificateId, keystorePath,
                                keystoreName, keystorePassword)) {
                    Log.i(TAG, "Certificate " + certificateId
                            + " found in keystore - using for MQTT.")
                    // load keystore from file into memory to pass on connection
                    _clientKeyStore.value = AWSIotKeystoreHelper.getIotKeystore(certificateId,
                            keystorePath, keystoreName, keystorePassword)
                    Log.d(TAG, "onCreate: " + _clientKeyStore.value.toString())
                } else {
                    Log.i(TAG, "Key/cert $certificateId not found in keystore.")
                }
            } else {
                Log.i(TAG, "Keystore $keystorePath/$keystoreName not found.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "An error occurred retrieving cert/key from keystore.", e)
        }
        if (_clientKeyStore.value == null) {
            Log.i(TAG, "Cert/key was not found in keystore - creating new key and certificate.")
            Thread(Runnable {
                try {
                    // Create a new private key and certificate. This call
                    // creates both on the server and returns them to the
                    // device.
                    val createKeysAndCertificateRequest = CreateKeysAndCertificateRequest()
                    createKeysAndCertificateRequest.isSetAsActive = true
                    val createKeysAndCertificateResult: CreateKeysAndCertificateResult
                    createKeysAndCertificateResult = mIotAndroidClient!!.createKeysAndCertificate(createKeysAndCertificateRequest)
                    Log.i(TAG,
                            "Cert ID: " +
                                    createKeysAndCertificateResult.certificateId +
                                    " created.")

                    // store in keystore for use in MQTT client
                    // saved as alias "default" so a new certificate isn't
                    // generated each run of this application
                    AWSIotKeystoreHelper.saveCertificateAndPrivateKey(certificateId,
                            createKeysAndCertificateResult.certificatePem,
                            createKeysAndCertificateResult.keyPair.privateKey,
                            keystorePath, keystoreName, keystorePassword)

                    // load keystore from file into memory to pass on
                    // connection
                    _clientKeyStore.postValue(AWSIotKeystoreHelper.getIotKeystore(certificateId,
                            keystorePath, keystoreName, keystorePassword))

                    Log.d(TAG, "onCreate: " + _clientKeyStore.value.toString())
                    // Attach a policy to the newly created certificate.
                    // This flow assumes the policy was already created in
                    // AWS IoT and we are now just attaching it to the
                    // certificate.
                    val policyAttachRequest = AttachPrincipalPolicyRequest()
                    policyAttachRequest.policyName = AWS_IOT_POLICY_NAME
                    policyAttachRequest.principal = createKeysAndCertificateResult
                            .certificateArn
                    mIotAndroidClient!!.attachPrincipalPolicy(policyAttachRequest)
                    activity.runOnUiThread{}
                } catch (e: Exception) {
                    Log.e(TAG,
                            "Exception occurred when generating new private key and certificate.",
                            e)
                }
            }).start()
        }
    }

    /**
     * Connects to MQTT endpoint. Tracks status of connection.
     */
    fun connect() {
        Log.d(MainActivity.TAG, "clientID = ${_clientID.value}")
        try {
            mqttManager!!.connect(_clientKeyStore.value) { status, throwable ->
                Log.d(MainActivity.TAG, "Status = $status")
                this.activity?.runOnUiThread {
                    if (status == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connecting) {
                        _connectionStatus.value = "Connecting..."
                    } else if (status == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected) {
                        _connectionStatus.value = "Connected"
                    } else if (status == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Reconnecting) {
                        if (throwable != null) {
                            Log.e(MainActivity.TAG, "Connection error.", throwable)
                        }
                        _connectionStatus.value = "Reconnecting"
                    } else if (status == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.ConnectionLost) {
                        if (throwable != null) {
                            Log.e(MainActivity.TAG, "Connection error.", throwable)
                        }
                        _connectionStatus.value = "Disconnected"
                    } else {
                        _connectionStatus.value = "Disconnected"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(MainActivity.TAG, "Connection error.", e)
            _connectionStatus.value = "Error! " + e.message
        }
    }

    /**
     * Publishes a message to an MQTT topic
     */
    fun publish(msg : String?, topic : String?){
        try {
            mqttManager!!.publishString(msg, topic, AWSIotMqttQos.QOS0)
        } catch (e: Exception) {
            Log.e(MainActivity.TAG, "Publish error.", e)
        }
    }

}