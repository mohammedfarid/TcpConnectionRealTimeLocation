package com.farid.taskpa

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.farid.taskpa.helper.MapInfoWindowAdapter
import com.farid.taskpa.helper.TcpClient
import com.farid.taskpa.helper.TcpEvent
import com.farid.taskpa.helper.TcpEventType
import com.farid.taskpa.model.UpdateLocation
import com.farid.taskpa.model.User
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.android.synthetic.main.activity_main.*
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity(), Observer, OnMapReadyCallback,
    EasyPermissions.PermissionCallbacks,
    EasyPermissions.RationaleCallbacks {
    var client: TcpClient? = null
    var userList: ArrayList<User>? = ArrayList()
    var geocoder: Geocoder? = null
    var addresses: List<Address>? = ArrayList()
    var map: GoogleMap? = null
    var markerList: ArrayList<Marker>? = ArrayList()

    var restart = false
    var updateMap = false
    var mapInfoWindowAdapter: MapInfoWindowAdapter? = null

    private val RC_LOCATION_PERM = 124
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        client = TcpClient("ios-test.printful.lv", 6111)
        client?.addObserver(this)
        client?.connect()
        geocoder = Geocoder(this, Locale.getDefault())
        map_view.onCreate(savedInstanceState)
        locationTask()
        btn.setOnClickListener {
            if (!restart) {
                btn.text = getText(R.string.restart_call)
                client?.sendMessage("AUTHORIZE m.farid.shawky@gmail.com")
                restart = true
            } else {
                btn.text = getText(R.string.start_tracking)
                btn.isEnabled = false
                btn.background =
                    ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_btn_disable)
                client?.disconnect()
                restart = false
                updateMap = false
                map_view?.getMapAsync(this)
                client?.connect()
            }
        }

    }


    override fun onResume() {
        super.onResume()
        map_view.onResume()
    }

    override fun onPause() {
        super.onPause()
        map_view.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        map_view.onDestroy()
        client?.disconnect()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        map_view.onLowMemory()
    }

    override fun onStop() {
        super.onStop()
        map_view.onStop()
    }

    override fun update(o: Observable?, arg: Any?) {
        val event = arg as TcpEvent

        when (event.tcpEventType) {
            TcpEventType.MESSAGE_RECEIVED -> {
                if (event.payload.toString().startsWith("USERLIST ")) {
                    val message =
                        event.payload.toString().removePrefix("USERLIST ").trim().split(";")
                    Log.d("USERLIST", " ${message[0]} \n ${message[1]} ")
                    updateMap = false
                    for (i in message) {
                        if (i != "") {
                            val obSt = i.split(",")
                            addresses =
                                geocoder?.getFromLocation(obSt[3].toDouble(), obSt[4].toDouble(), 1)
                            val user = User(
                                userId = obSt[0].toInt(),
                                name = obSt[1],
                                imageUrl = obSt[2],
                                lat = obSt[3].toDouble(),
                                lng = obSt[4].toDouble(),
                                address = addresses?.get(0)?.getAddressLine(0)
                            )

                            userList?.add(user)
                        }
                    }
                    runOnUiThread {
                        //tv.text = userList.toString()
                        map_view.getMapAsync(this)
                    }
                }
                if (event.payload.toString().startsWith("UPDATE ")) {
                    val message: String = event.payload.toString().removePrefix("UPDATE ").trim()
                    updateMap = true
                    Log.d("UPDATE", message)
                    if (message != "") {
                        val obSt = message.split(",")
                        val updateLocation =
                            UpdateLocation(
                                userId = obSt[0].toInt(),
                                lat = obSt[1].toDouble(),
                                lng = obSt[2].toDouble()
                            )
                        addresses = geocoder?.getFromLocation(
                            updateLocation.lat ?: 0.0,
                            updateLocation.lng ?: 0.0,
                            1
                        )
                        if (!userList.isNullOrEmpty()) {
                            for (i in 0 until userList?.size!!) {
                                if (updateLocation.userId == userList?.get(i)?.userId) {
                                    userList?.get(i)?.lat = updateLocation.lat
                                    userList?.get(i)?.lng = updateLocation.lng
                                    userList?.get(i)?.address = addresses?.get(0)?.getAddressLine(0)
                                }
                            }
                        }
                        runOnUiThread {
                            map_view.getMapAsync(this)
                        }
                    }
                }
            }
            TcpEventType.CONNECTION_ESTABLISHED -> {
                runOnUiThread {
                    btn.isEnabled = true
                    btn.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_btn)
                }
            }

        }
    }

    override fun onMapReady(p0: GoogleMap?) {
        map = p0
        map?.uiSettings?.isMyLocationButtonEnabled = false
        map?.isMyLocationEnabled = true
        mapInfoWindowAdapter = MapInfoWindowAdapter(this@MainActivity)
        map?.setInfoWindowAdapter(
            mapInfoWindowAdapter
        )
        if (!restart) {
            map?.clear()
            userList = ArrayList()
        } else {
            if (userList.isNullOrEmpty()) {
                return
            } else {

                val builder = LatLngBounds.Builder()
                if (!updateMap) {
                    for (user in userList!!) {
                        val marker = map?.addMarker(
                            MarkerOptions().position(
                                LatLng(
                                    user.lat ?: 0.0,
                                    user.lng ?: 0.0
                                )
                            )
                        )
                        marker?.tag = user
                        if (marker != null) {
                            markerList?.add(marker)
                        }
                        builder.include(marker?.position)
                    }
                } else {
                    for (i in 0 until userList?.size!!) {
                        val user = markerList?.get(i)?.tag as User
                        val userNew = userList?.get(i)
                        animateMarker(
                            map, i, LatLng(user.lat ?: 0.0, user.lng ?: 0.0),
                            LatLng(userNew?.lat ?: 0.0, userNew?.lng ?: 0.0)
                            , false
                        )
                        if (markerList?.get(i)?.isInfoWindowShown!!){
                            markerList?.get(i)?.hideInfoWindow()
                            markerList?.get(i)?.showInfoWindow()
                        }
                        builder.include(markerList?.get(i)?.position)
                    }
                }
                val bounds = builder.build()
                val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 50)
                map?.animateCamera(cameraUpdate)
            }
        }

    }

    //This methos is used to move the marker of each car smoothly when there are any updates of their position
    private fun animateMarker(
        mMap: GoogleMap?, position: Int?, startPosition: LatLng?, toPosition: LatLng?,
        hideMarker: Boolean
    ) {
//        val marker: Marker = mMap.addMarker(
//            MarkerOptions()
//                .position(startPosition)
//                .title(mCarParcelableListCurrentLation.get(position).mCarName)
//                .snippet(mCarParcelableListCurrentLation.get(position).mAddress)
//                .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
//        )
        val handler = Handler()
        val start = SystemClock.uptimeMillis()
        val duration: Long = 1000
        val interpolator: Interpolator = LinearInterpolator()
        handler.post(object : Runnable {
            override fun run() {
                val elapsed = SystemClock.uptimeMillis() - start
                val t: Float = interpolator.getInterpolation(
                    elapsed.toFloat()
                            / duration
                )
                val lng = t * toPosition?.longitude!! + (1 - t) * startPosition?.longitude!!
                val lat = t * toPosition.latitude + (1 - t) * startPosition.latitude
                markerList?.get(position ?: 0)?.setPosition(LatLng(lat, lng))
                if (t < 1.0) {
                    // Post again 16ms later.
                    handler.postDelayed(this, 16)
                } else {
                    markerList?.get(position ?: 0)?.isVisible = !hideMarker
                }
            }
        })
    }

    private fun hasLocationPermissions(): Boolean {
        return EasyPermissions.hasPermissions(this, ACCESS_FINE_LOCATION)
    }

    private fun locationTask() {
        if (hasLocationPermissions()) {
            map_view.getMapAsync(this)
            // Have permission, do the thing!
            Toast.makeText(this, "TODO: Location things", Toast.LENGTH_LONG).show()
        } else {
            // Ask for one permission
            EasyPermissions.requestPermissions(
                this,
                getString(R.string.rationale_location),
                RC_LOCATION_PERM,
                ACCESS_FINE_LOCATION
            )
        }
    }

    override fun onPermissionsGranted(
        requestCode: Int,
        perms: List<String?>
    ): Unit {
        Log.d(
            "TAG",
            "onPermissionsGranted:" + requestCode + ":" + perms.size
        )
    }

    override fun onPermissionsDenied(
        requestCode: Int,
        perms: List<String?>
    ) {
        Log.d(
            "TAG",
            "onPermissionsDenied:" + requestCode + ":" + perms.size
        )

        // (Optional) Check whether the user denied any permissions and checked "NEVER ASK AGAIN."
        // This will display a dialog directing them to enable the permission in app settings.
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this).build().show()
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    override fun onRationaleAccepted(requestCode: Int) {
        Log.d("TAG", "onRationaleAccepted:$requestCode")
    }

    override fun onRationaleDenied(requestCode: Int) {
        Log.d("TAG", "onRationaleDenied:$requestCode")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE) {
            val yes = getString(R.string.yes)
            val no = getString(R.string.no)

            // Do something after user returned from app settings screen, like showing a Toast.
            Toast.makeText(
                this,
                getString(
                    R.string.returned_from_app_settings_to_activity,
                    if (hasLocationPermissions()) yes else no
                ),
                Toast.LENGTH_LONG
            )
                .show()
        }
    }
}