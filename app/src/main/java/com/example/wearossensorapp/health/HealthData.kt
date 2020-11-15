package com.example.wearossensorapp.health

data class HealthData (
        var longitude : Double,
        var latitude : Double,
        var heartRate : Float,
        var heartRateAccuracy : Int,
        var locationAccuracy : Float) {

        override fun toString(): String {
            return "HealthData(longitude=$longitude, latitude=$latitude, heartRate=$heartRate, heartRateAccuracy=$heartRateAccuracy, locationAccuracy=$locationAccuracy)"
        }
}