package com.example.airquality_app

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.airquality_app.databinding.ActivityMainBinding

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAllPermissions()  // 권한 확인
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
}