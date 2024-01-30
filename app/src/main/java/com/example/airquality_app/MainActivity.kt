package com.example.airquality_app

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.airquality_app.databinding.ActivityMainBinding
import com.example.airquality_app.retrofit.AirQualityResponse
import com.example.airquality_app.retrofit.AirQualityService
import com.example.airquality_app.retrofit.RetrofitConnection
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.lang.IllegalArgumentException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    // 런타임 권한 요청 시 필요한 요청 코드
    private val PERMISSIONS_REQUEST_CODE = 100
    //요청할 권한 목록
    var REQUIRED_PERMISSIONS = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // 위치 서비스 요청 시 필요한 런처
    lateinit var getGPSPermissionLauncher: ActivityResultLauncher<Intent>

    // 위도와 경도 가져올 떄 필요
    lateinit var locationProvider: LocationProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAllPermissions()  // 권한 확인
        updateUI()

        // 새로고침 버튼 클릭 시
        setRefreshButton()
    }

    private fun setRefreshButton() {
        binding.btnRefresh.setOnClickListener {
            updateUI()
        }
    }

    private fun updateUI() {
        locationProvider = LocationProvider(this@MainActivity)

        // 위도와 경도 정보를 가져옴
        val latitude: Double = locationProvider.getLocationLatitude()
        val longitude: Double = locationProvider.getLocationLongitude()

        Log.d("MyTag", "Latitude: $latitude, Longitude: $longitude")

        if (latitude != 0.0 || longitude != 0.0) {
            // 현재 위치를 가져오기
            val address = getCurrentAddress(latitude, longitude)

            // 주소가 null이 아닐 경우 UI 업데이트
            var adminArea  : String? = address?.adminArea
            var locality  : String? = address?.locality
            var thoroughfare  : String? = address?.thoroughfare
            var subThoroughfare  : String? = address?.subThoroughfare
            var featureName  : String? = address?.featureName
            var postalCode  : String? = address?.postalCode
            Log.d("MyTag", "adminArea: $adminArea, locality: $locality, thoroughfare: $thoroughfare\nsubThoroughfare: $subThoroughfare, featureName: $featureName, postalCode $postalCode")
            address?.let {
                if (it.thoroughfare == null) {
                    binding.tvLocationTitle.text = "${it.locality} 내위치"
                } else {
                    binding.tvLocationTitle.text = "${it.thoroughfare}"
                }
                binding.tvLocationSubtitle.text = "${it.countryName} ${it.adminArea}"
            }
            // 현재 미세먼지 농도 가져오고 UI 업데이트
            getAirQualityData(latitude, longitude)
        } else {
            Toast.makeText(this@MainActivity, "위도, 경도 정보를 가져올 수 없습니다. 새로고침을 눌러주세요.", Toast.LENGTH_LONG).show()
        }
    }

    private fun getAirQualityData(latitude: Double, longitude: Double) {
        // 레트로핏 객체를 이용해 AirQualityService 인터페이스 구현체를 가져올 수 있음
        val retrofitAPI = RetrofitConnection.getInstance().create(AirQualityService::class.java)

        // retrofitAPI를 이용해 Call 객체를 만든 후 enqueue() 함수를 실행하여 서버에 API 요청을 보냄
        retrofitAPI.getAirQualityData(
            latitude.toString(),
            longitude.toString(),
            "21537d86-a4d2-4690-81a3-d1feed52ed5b"  // API key
        ).enqueue(object : Callback<AirQualityResponse> {  // Callback<AirQualityResponse> : 함수의 반환값
            override fun onResponse(call: Call<AirQualityResponse>, response: Response<AirQualityResponse>) {
                // 정상적인 Response가 왔다면 UI 업데이트
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "최신 정보 업데이트 완료!", Toast.LENGTH_SHORT).show()
                    // response.body()가 null이 아니면 updateAirUI()
                    response.body()?.let {
                        updateAirUI(it) }
                } else {
                    Toast.makeText(this@MainActivity, "업데이트에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<AirQualityResponse>, t: Throwable) {
                t.printStackTrace()
                Log.e("MyTag", "API 요청 실패: ${t.message}")
                Toast.makeText(this@MainActivity, "서버로부터 응답을 받아오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // 가져온 데이터 정보를 바탕으로 UI update
    private fun updateAirUI(airQualityResponse: AirQualityResponse) {
        val pollutionData = airQualityResponse.data.current.pollution

        // 수치 UI 지정
        // aqius: 미국 기준 Air Quality Index 값(대기 지수)
        binding.tvCount.text = pollutionData.aqius.toString()

        // 측정된 날짜 UI 지정
        // ts: 현재 응답으로 오는 시간 데이터 => 2024-01-30T13:00:00.000Z 형식
        val dateTime = ZonedDateTime.parse(pollutionData.ts).withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime()  // ZonedDateTime 클래스 => 서울 시간대 적용
        val dateFormatter : DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")  // DateTimeFormatter.ofPattern() 함수 => 2023-01-30 23:00 형식으로 변환

        binding.tvCheckTime.text = dateTime.format(dateFormatter).toString()

        // 지수값을 기준으로 범위 나누어 대기 농도 평가 텍스트와 배경 이미지 변경
        when (pollutionData.aqius) {
            in 0..50 -> {
                binding.tvTitle.text = "좋음"
                binding.imgBg.setImageResource(R.drawable.bg_good)
            }
            in 51..150 -> {
                binding.tvTitle.text = "보통"
                binding.imgBg.setImageResource(R.drawable.bg_soso)
            }
            in 151..200 -> {
                binding.tvTitle.text = "나쁨"
                binding.imgBg.setImageResource(R.drawable.bg_bad)
            }
            else -> {
                binding.tvTitle.text = "매우나쁨"
                binding.imgBg.setImageResource(R.drawable.bg_worst)
            }
        }
    }

    private fun checkAllPermissions() {
        // 1. 위치 서비스가 켜져 있는지 확인
        if (!isLocationServicesAvailable()) {
            showDialogForLocationServiceSetting()
        } else {  // 2. 런타임 앱 권한이 모두 허용되어 있는지 확인
            isRuntimePermissionsGranted()
        }
    }

    // 위치 서비스 켜져 있는지 확인
    fun isLocationServicesAvailable(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }

    // 위치 퍼미션을 가지고 있는지 확인
    fun isRuntimePermissionsGranted() {
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            this@MainActivity, android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        val hasCoarseLicationPermission = ContextCompat.checkSelfPermission(
            this@MainActivity, android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (hasFineLocationPermission != PackageManager.PERMISSION_GRANTED || hasCoarseLicationPermission != PackageManager.PERMISSION_GRANTED) {
            // 권한이 한 개라도 없다면 퍼미션 요청
            ActivityCompat.requestPermissions(this@MainActivity, REQUIRED_PERMISSIONS,PERMISSIONS_REQUEST_CODE)
        }
    }

    // 권한 요청 후 결괏값을 처리 => 모든 퍼미션이 허용되었는지 확인 (허용되지 않은 권한이 있다면 앱 종료)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE && grantResults.size == REQUIRED_PERMISSIONS.size) {
            // 요청 코드가 PERMISSIONS_REQUEST_CODE 이고, 요청한 퍼미션 개수만큼 수신되었다면
            var checkResult = true

            // 모든 퍼미션이 허용되었는지 확인
            for (result in grantResults) {
                if(result != PackageManager.PERMISSION_GRANTED) {
                    checkResult = false
                    break
                }
            }
            if (checkResult) {
                // 위칫값 가져오기
                updateUI()
            } else {
                // 퍼미션 거부되면 앱 종료
                Toast.makeText(this@MainActivity, "권한이 거부되었습니다. 앱을 다시 실행하여 권한을 허용해주세요.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // 위치 서비스가 꺼져있다면 다이얼로그를 사용해 위치 서비스 설정
    private fun showDialogForLocationServiceSetting() {
        // getGPSPermissionLauncher => 결괏값을 반환해야 하는 인텐트 실행해줌
        getGPSPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // 결괏값을 받았을 때
            result -> if (result.resultCode == Activity.RESULT_OK) {
                // 사용자가 GPS를 활성화시켰는지 확인
                if (isLocationServicesAvailable()) {
                    // 런타임 권한 확인
                    isRuntimePermissionsGranted()
                } else {
                    // 위치 서비스가 허용되지 않았다면 앱 종료
                    Toast.makeText(this@MainActivity, "위치 서비스를 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }

        // 사용자에게 의사를 물어보는 AlertDialog 생성
        val builder: AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("위치 서비스 비활성화")  // 제목
        builder.setMessage("위치 서비스가 꺼져 있습니다. 설정해야 앱을 사용할 수 있습니다.")  // 내용
        builder.setCancelable(true)  // 다이얼로그 창 바깥 터치 시 창 닫힘
        builder.setPositiveButton("설정", DialogInterface.OnClickListener {  // 확인 버튼 설정
            dialog, id ->  val callGPSSettingIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            getGPSPermissionLauncher.launch(callGPSSettingIntent)
        })
        builder.setNegativeButton("취소", DialogInterface.OnClickListener{  // 취소 버튼 설정
            dialog, id ->  dialog.cancel()
            Toast.makeText(this@MainActivity, "기기에서 위치서비스(GPS) 설정 후 사용해주세요.", Toast.LENGTH_SHORT).show()
            finish()
        })
        // 다이얼로그 생성 및 출력
        builder.create().show()
    }

    // 지오코딩 함수
    fun getCurrentAddress(latitude: Double, longitude: Double) : Address? {
        val geocoder = Geocoder(this, Locale.getDefault())
        // Address 객체는 주소와 관련된 여러 정보를 갖고 있음 => android.location.Address 참고
        val addresses: List<Address>?

        addresses = try {
            // Geocoder 객체를 이용하여 위도와 경도로부터 리스트를 가져옴
            geocoder.getFromLocation(latitude, longitude, 7)
        } catch (ioException: IOException) {
            Toast.makeText(this@MainActivity, "지오코더 서비스 사용 불가합니다.", Toast.LENGTH_SHORT).show()
            return null
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e("MyTag", "IllegalArgumentException: ${illegalArgumentException.message}")
            Toast.makeText(this@MainActivity, "잘못된 경도, 위도 입니다.", Toast.LENGTH_SHORT).show()
            return null
        }

        // 에러는 아니지만 주소가 발견되지 않은 경우
        if (addresses == null || addresses.size == 0) {
            Toast.makeText(this@MainActivity, "주소가 발견되지 않았습니다.", Toast.LENGTH_SHORT).show()
            return null
        }

        val address: Address = addresses[0]
        return address
    }
}