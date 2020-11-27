# HealthMonitoringAppWearOS

This application collects health information in real-time and sends it over to AWS to analyze for abnormalities. 

<img src="https://github.com/UBC-CIC/HealthMonitoringAppWearOS/blob/main/docs/images/wearossc1.png"  width="300"/>

## Overview 
This app is capable of collecting health information such as heart rate and the location of the user. It has a thread that always runs in the foreground which sends the health data over to AWS servers. You can adjust the sampling rate for both location and heart rate to improve battery life. 

## Setup

You should familiarize yourself with Cloudformation, IoTCore before following the instructions below.

### Stack input
Use the cloudformation template in the Cloudformation folder to setup a Cloudformation Stack on IoT Core. Make sure you are in the correct region as this will affect the geographical location of where the data will be stored. The parameters to the stack are the Kinesis Data Stream ARN, the logical name of the Stream and the name of the topic your wearable is subscribed to. Use the stream to which you want to redirect the output of the wearable. 

### Stack output
Use the output of the stack to setup the Android Studio project. Once the stack is set up it will output : IdentityPoolId, IoTPolicyName, Region. Change the constants inside AWSViewModel class to the generated values. You can lookup IOTEndpoint address in IoTCore in AWS console (Go to IoTCore => Settings). 

## AWS Architecture

<img src="https://github.com/UBC-CIC/HealthMonitoringAppWearOS/blob/main/docs/images/architecture.png"  width="500"/>

## Useful links

[Foreground service](https://developer.android.com/guide/components/foreground-services)

[Live data](https://developer.android.com/topic/libraries/architecture/livedata)

[ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel)

[Debug on a physical device](https://developer.android.com/training/wearables/apps/creating)

[Android AWS PubSub example](https://github.com/felipemeriga/aws-sdk-android-samples/tree/master/AndroidPubSub)


## Related Projects

[The Mobile Health Monitoring Platform](https://github.com/UBC-CIC/Mobile_Health_Monitoring_Platform)

[HealthMonitoringAppWatchOS](https://github.com/UBC-CIC/HealthMonitoringAppWatchOS)

## License
This project is distributed under MIT license. 
