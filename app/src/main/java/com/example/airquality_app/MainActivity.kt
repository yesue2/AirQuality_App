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
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.airquality_app.databinding.ActivityMainBinding
import com.example.airquality_app.retrofit.AirQualityResponse
import com.example.airquality_app.retrofit.AirQualityService
import com.example.airquality_app.retrofit.RetrofitConnection
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
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

    // 위도, 경도 저장
    var latitude: Double = 0.0
    var longitude: Double = 0.0

    // 결과를 받아와야 하는 액티비티를 실행할 때 사용하는 변수 선언
    // registerForActivityResult(: 다른 액티비티의 실행 결과를 콜백에 등록) 객체 생성
    // 콜백은 해당 액티비티가 결과를 반환할 때 실행
    val startMapActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), object : ActivityResultCallback<ActivityResult> {
        override fun onActivityResult(result: ActivityResult) {
            if (result?.resultCode ?: 0 == Activity.RESULT_OK) {
                // 지도 페이지에서 위도와 경도 반환
                latitude = result?.data?.getDoubleExtra("latitude", 0.0) ?: 0.0
                longitude = result?.data?.getDoubleExtra("longitude", 0.0) ?: 0.0
                updateUI()
            }
        }
    })

    // 전면 광고 설정 변수
    var mInterstitialAd : InterstitialAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAllPermissions()  // 권한 확인
        updateUI()

        // 새로고침 버튼 클릭 시
        setRefreshButton()

        // 플로팅 액션 버튼 클릭 시
        setFab()

        // 배너 실행 함수
        setBannerAds()
    }

    // 전면 광고 다시 로드하는 함수
    override fun onResume() {
        super.onResume()
        setInterstitialAds()
    }

    private fun setFab() {
        binding.fab.setOnClickListener {
            if (mInterstitialAd != null) {  // 변수에 fullScreenContentCallback 인터페이스 등록 => 전면 광고가 닫히고 열렸을 때, 실패했을 때를 콜백 함수로 확인할 수 있음
                mInterstitialAd!!.fullScreenContentCallback = object: FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d("ads log", "전면 광고가 닫혔습니다.")

                        val intent = Intent(this@MainActivity, MapActivity::class.java)
                        intent.putExtra("currentLat", latitude)
                        intent.putExtra("currntLng", longitude)
                        // startMapActivityResult.launch() : 지도 페이지로 이동하고, 등록해둔 onActivityResult 콜백에 보낸 값이 전달
                        startMapActivityResult.launch(intent)
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.d("ads log", "전면 광고가 열리는 데 실패 ${adError.message}")
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d("ads log", "전면 광고가 성공적으로 열렸습니다.")
                        mInterstitialAd = null  // 전면 광고는 재사용이 어렵기 때문에 null로 만들어주기
                    }
                }
                mInterstitialAd!!.show(this@MainActivity)
            } else {
                Log.d("InterstitialAd", "전면 광고가 로딩되지 않았습니다.")
                Toast.makeText(this@MainActivity, "잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setRefreshButton() {
        binding.btnRefresh.setOnClickListener {
            updateUI()
        }
    }

    private fun updateUI() {
        locationProvider = LocationProvider(this@MainActivity)
        if (latitude == 0.0 || longitude == 0.0) {
            // 위도와 경도 정보를 가져옴
            latitude = locationProvider.getLocationLatitude()
            longitude = locationProvider.getLocationLongitude()
            Log.d("MyTag", "Latitude: $latitude, Longitude: $longitude")
        }

        if (latitude != 0.0 || longitude != 0.0) {
            // 현재 위치를 가져오기
            val address = getCurrentAddress(latitude, longitude)

            // 주소가 null이 아닐 경우 UI 업데이트
            var adminArea  : String? = address?.adminArea
            var locality  : String? = address?.locality
            var thoroughfare  : String? = address?.thoroughfare
            var subLocality  : String? = address?.subLocality
            var subThoroughfare : String? = address?.subThoroughfare
            Log.d("MyTag", "adminArea: $adminArea, locality: $locality, subLocality: $subLocality, thoroughfare: $thoroughfare, subThoroughfare: $subThoroughfare")
            address?.let {
                if (it.thoroughfare == null) {
                    binding.tvLocationSubtitle.text = "${it.adminArea} ${it.locality}"
                    binding.tvLocationTitle.text = "${it.subLocality}"
                } else if (it.subLocality == null) {
                    binding.tvLocationSubtitle.text = "${it.adminArea} ${it.locality}"
                    binding.tvLocationTitle.text = "${it.thoroughfare}"
                } else if (it.locality == null) {
                    binding.tvLocationSubtitle.text = "${it.adminArea} ${it.subLocality}"
                    binding.tvLocationTitle.text = "${it.thoroughfare}"
                } else {
                    binding.tvLocationSubtitle.text = "${it.adminArea} ${it.locality} ${it.subLocality}"
                    binding.tvLocationTitle.text = "${it.thoroughfare}"
                }
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

    // 배너 광고 설정 함수
    private fun setBannerAds() {
        MobileAds.initialize(this) // 구글 모바일 광고 SDK 초기화
        val adRequest = AdRequest.Builder().build()  // AdRequest 객체 생성 => 광고 요청에 대한 타깃팅 정보 있음
        binding.adView.loadAd(adRequest) // 애드뷰에 광고 로드

        // 애드뷰 리스너
        binding.adView.adListener = object  : AdListener() {
            override fun onAdLoaded() {
                Log.d("ads log", "배너 광고가 로드되었습니다.")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("ads log", "배너 광고가 로드 실패했습니다. ${adError.responseInfo}")
            }

            override fun onAdOpened() {
                Log.d("ads log", "배너 광고를 열었습니다.")
            }

            override fun onAdClicked() {
                Log.d("ads log", "배너 광고를 클릭했습니다.")
            }

            override fun onAdClosed() {
                Log.d("ads log", "배너 광고를 닫았습니다.")
            }
        }
    }

    // 전면 광고 설정 함수
    private fun setInterstitialAds() {
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(this, "ca-app-pub-3940256099942544/1033173712", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("ads log", "전면 광고가 로드 실패했습니다. ${adError.responseInfo}")
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d("ads log", "전면 광고가 로드되었습니다.")
                mInterstitialAd = interstitialAd
            }
        })
    }
}