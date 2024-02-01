package com.example.airquality_app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.airquality_app.databinding.ActivityMapBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MapActivity : AppCompatActivity(), OnMapReadyCallback {
    lateinit var binding: ActivityMapBinding

    private var mMap: GoogleMap? = null
    var currentLat: Double = 0.0
    var currentLng: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // MainActivity에서 전달된 값 가져옴
        currentLat = intent.getDoubleExtra("currentLat", 0.0)
        currentLng = intent.getDoubleExtra("currentLng", 0.0)
        // SupportMapFragment 객체를 mapFragment에 저장
        // SupportMapFragment: 구글 맵 객체의 생명주기를 관리하는 객체
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        // getMapAsync(): mapFragment에 OnMapReadyCallback 인터페이스를 등록해줌 => 지도가 준비되면 onMapReady()함수 자동 실행
        mapFragment?.getMapAsync(this)

        binding.btnCheckHere.setOnClickListener {
            mMap?.let {
                val intent = Intent()
                // 버튼이 눌린 시점의 카메라 포지션 가져옴(보이는 지도의 중앙지점 좌푯값 가져옴)
                intent.putExtra("latitude", it.cameraPosition.target.latitude)
                intent.putExtra("longitude", it.cameraPosition.target.longitude)
                // setResult() 함수: MainActivity.kt에서 정의해두었던 onActivityResult() 함수 실행
                setResult(Activity.RESULT_OK, intent)
                finish()  // 지도 액티비티 종료
            }
        }
    }

    // 지도가 준비되었을 때 실행되는 콜백
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap?.let {
            val currentLocation = LatLng(currentLat, currentLng)
            it.setMaxZoomPreference(20.0f)  // 줌 최댓값 설정
            it.setMinZoomPreference(12.0f)  // 줌 최솟값 설정
            it.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16f))
        }

        setMarker()

        // 플로팅 버튼이 눌렸을 때 현재 위도, 경도 정보를 가져와 지도의 위치를 움직임
        binding.fabCurrentLocation.setOnClickListener {
            val locationProvider = LocationProvider(this@MapActivity)
            // 위도와 경도 정보 가져옴
            val latitude = locationProvider.getLocationLatitude()
            val lontitude = locationProvider.getLocationLongitude()
            mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, lontitude), 16f))
            setMarker()
        }
    }

    // 마커 설정 함수
    private fun setMarker() {
        mMap?.let {
            it.clear()  // 지도에 있는 마커 먼저 삭제
            val markerOptions = MarkerOptions()
            markerOptions.position(it.cameraPosition.target)  // 마커의 위치 설정
            markerOptions.title("마커 위치") // 마커의 이름 설정
            val marker = it.addMarker(markerOptions) // 지도에 마커를 추가하고, 마커 객체를 반환

            // setPosition() 함수: 마커를 지도에 추가
            // setOnCameraMoveListener() 함수: 지도가 움직일 때 마커도 함께 움직임
            it.setOnCameraMoveListener {
                marker?.let {
                    marker -> marker.setPosition(it.cameraPosition.target)
                }
            }
        }
    }
}