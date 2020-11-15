package com.example.wearossensorapp

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.support.wearable.activity.WearableActivity
import android.util.Log
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.wear.ambient.AmbientModeSupport
import com.example.wearossensorapp.health.HealthService
import com.example.wearossensorapp.utils.AWSViewModel

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {

  //Unique ID for this app. Stored in persistent memory. Created when app is just installed.
  private var clientId: String? = null

  //Use this to see if the app is connected to IoT Core
  private var connectionStatus = "Connecting..."

  private var mPreferences: SharedPreferences? = null

  //Permissions related
  private var mGpsPermissionNeededMessage: String? = null
  private var mGpsPermissionApproved = false
  private var mAcquiringGpsMessage: String? = null

  //TextViews for the app
  lateinit var deviceIDTextView : TextView
  lateinit var statusTextView : TextView

  //View model related variables
  private lateinit var AWSviewModel: AWSViewModel

  //Foreground service
  var mService: HealthService? = null

  companion object {
    val TAG = MainActivity::class.java.canonicalName

    private val MY_PERMISSIONS_REQUEST_POWER: Int = 0
    private val MY_PERMISSIONS_REQUEST_BODY_SENSORS: Int = 1

    // Id to identify Location permission request.
    private val REQUEST_GPS_PERMISSION = 1

  }


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    deviceIDTextView = findViewById(R.id.deviceID)
    statusTextView = findViewById(R.id.connectivityStatus)

    setupAWSViewModel()
    requestPermissions()
    bindService()
  }

  private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(className: ComponentName, iBinder: IBinder) {
      // We've bound to MyService, cast the IBinder and get MyBinder instance
      Log.d(TAG, "onServiceConnected: ")
      val binder = iBinder as HealthService.ServiceBinder
      mService = binder.service
      mService?.AWSviewModel = AWSviewModel
      mService?.clientID = clientId!!
    }

    override fun onServiceDisconnected(arg0: ComponentName) {
      Log.d(TAG, "onServiceDisconnected: ")
    }
  }

  /**
   * Sets up and starts the MqttClient.
   * Creates observers for AWS related variables.
   */
  private fun setupAWSViewModel() {
    AWSviewModel = ViewModelProvider(this).get(AWSViewModel::class.java)
    AWSviewModel.setupIOTCore(applicationContext, filesDir, this)
    AWSviewModel.connectionStatus.observe(this, androidx.lifecycle.Observer {newStatus ->
      Log.d(TAG, "onCreate: " + newStatus)
      connectionStatus = newStatus
      statusTextView.setText(newStatus)
    })
    AWSviewModel.clientID.observe(this, androidx.lifecycle.Observer {newClientID ->
      Log.d(TAG, "onCreate: " + newClientID)
      clientId = newClientID
      deviceIDTextView.setText(newClientID)
    })
    //to avoid a race condition on client key store
    AWSviewModel.clientKeyStore.observe(this, androidx.lifecycle.Observer {newClientKeyStore ->
      Log.d(TAG, "onCreate: clientkeystore" + newClientKeyStore)
      if(newClientKeyStore != null){
        Log.d(TAG, "onCreate: found a key - connecting")
        AWSviewModel.connect()
      }
    })
  }

  /**
   * Binds foreground service to this activity.
   */
  private fun bindService() {
    Intent(applicationContext, HealthService::class.java).also { intent ->
      bindService(intent, this.serviceConnection, Context.BIND_AUTO_CREATE)
    }
  }

  override fun onPause() {
    mPreferences = applicationContext.getSharedPreferences(AWSViewModel.sharedPrefFileName, WearableActivity.MODE_PRIVATE);
    if(!mPreferences?.contains("Device ID")!!){
      Log.d(TAG, "onPause: need to insert device id")
      val preferencesEditor = mPreferences?.edit()
      preferencesEditor?.putString("Device ID", clientId)
      preferencesEditor?.apply();
    }
    super.onPause()
  }

  /**
   * Requests all the permissions needed by the app.
   */
  private fun requestPermissions() {
    setupGPSPermission()
    requestPermission(Manifest.permission.BODY_SENSORS, MY_PERMISSIONS_REQUEST_BODY_SENSORS)
    requestPermission(Manifest.permission.WAKE_LOCK, MY_PERMISSIONS_REQUEST_POWER)
  }

  /**
   * Convenience function for requesting permissions.
   */
  private fun requestPermission(permission: String, id: Int) {
    if (ContextCompat.checkSelfPermission(this, permission)
      != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this,
        arrayOf(permission), id)
    }
  }

  /**
   * Use this to request GPS permission.
   */
  private fun setupGPSPermission() {
    /*
     * If this hardware doesn't support GPS, we warn the user. Note that when such device is
     * connected to a phone with GPS capabilities, the framework automatically routes the
     * location requests from the phone. However, if the phone becomes disconnected and the
     * wearable doesn't support GPS, no location is recorded until the phone is reconnected.
  */
    if (!hasGps()) {
      Log.w(TAG, "This hardware doesn't have GPS, so we warn user.")
      AlertDialog.Builder(this)
        .setMessage("GPS not available")
        .setPositiveButton("OK", DialogInterface.OnClickListener { dialog, id -> dialog.cancel() })
        .setOnDismissListener(DialogInterface.OnDismissListener { dialog -> dialog.cancel() })
        .setCancelable(false)
        .create()
        .show()
    }

    // Enables app to handle 23+ (M+) style permissions.
    mGpsPermissionApproved = (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            === PackageManager.PERMISSION_GRANTED)

    if (!mGpsPermissionApproved) {
      Log.i(TAG, "Location permission has NOT been granted. Requesting permission.")

      // On 23+ (M+) devices, GPS permission not granted. Request permission.
      ActivityCompat.requestPermissions(
        this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
        REQUEST_GPS_PERMISSION)
    }

    mGpsPermissionNeededMessage = "App requires location permission to function, tap GPS icon"
    mAcquiringGpsMessage = "Acquiring GPS Fix ..."
  }

  /**
   * Returns `true` if this device has the GPS capabilities.
   */
  private fun hasGps(): Boolean {
    return packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
  }

  /**
   * Callback received when a permissions request has been completed.
   */
  override fun onRequestPermissionsResult(
          requestCode: Int, @NonNull permissions: Array<String?>, @NonNull grantResults: IntArray) {
    Log.d(TAG, "onRequestPermissionsResult(): ")
    if (requestCode == REQUEST_GPS_PERMISSION) {
      Log.i(TAG, "Received response for GPS permission request.")
      if (grantResults.size == 1
              && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        Log.i(TAG, "GPS permission granted.")
        mGpsPermissionApproved = true
      } else {
        Log.i(TAG, "GPS permission NOT granted.")
        mGpsPermissionApproved = false
      }
    }
  }


  /**
   * Here this is not used. But it might be useful if battery drains too fast.
   */
  override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback {
    return MyAmbientCallback();
  }

  /**
   * Here this is not used. But it might be useful if battery drains too fast.
   */
  private class MyAmbientCallback : AmbientModeSupport.AmbientCallback() {

  }


}
