package com.yesue2.airquality_app

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

class LocationProvider(val context: Context) {
   private var location: Location? = null
    private var locationManager: LocationManager? = null

    init {
        getLocation()
    }

    private fun getLocation(): Location? {
        try {
            // 위치 시스템 서비스 가져오기
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var gpsLocation: Location? = null
            var networkLocation: Location? = null

            // GPS Provider와 Network Provider가 활성화 되어 있는지 확인
            val isGPSEnabled: Boolean = locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled: Boolean = locationManager!!.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if (!isGPSEnabled && !isNetworkEnabled) {
                // GPS, Network Provider 둘 다 사용 불가능한 상황이면 null 반환
                return null
            } else {
                // ACCESS_COARSE_LOCATION 보다 더 정밀한 위치 정보 얻기
                val hasFineLocationPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                // 도시 block 단위 정밀도의 위치 정보 얻기
                val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)

                // 위의 두 개 권한 없다면 null 반환
                if (hasFineLocationPermission != PackageManager.PERMISSION_GRANTED || hasCoarseLocationPermission != PackageManager.PERMISSION_GRANTED)
                    return null

                // GPS를 통한 위치 파악이 가능한 경우에 위치를 가져옴
                if (isGPSEnabled) {
                    gpsLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                }
                // 네트워크를 통한 위치 파악이 가능한 경우에 위치를 가져옴
                if (isNetworkEnabled) {
                    networkLocation = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }

                if (gpsLocation != null && networkLocation != null) {
                    // 두 개 위치가 있다면 정확도 높은 것으로 선택
                    if (gpsLocation.accuracy > networkLocation.accuracy) {
                        location = gpsLocation
                        return gpsLocation
                    } else {
                        location = networkLocation
                        return networkLocation
                    }
                } else {
                    // 가능한 위치 정보가 한 개만 있는 경우
                    if (gpsLocation != null) {
                        location = gpsLocation
                    }

                    if (networkLocation != null) {
                        location = networkLocation
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()  // 에러 출력
        }
        return location
    }

    // 위도 정보 가져오는 함수
    fun getLocationLatitude(): Double {
        return location?.latitude ?: 0.0  // null이면 0.0 반환
    }

    // 경도 정보 가져오는 함수
    fun getLocationLongitude(): Double {
        return location?.longitude ?: 0.0  // null이면 0.0 반환
    }
}