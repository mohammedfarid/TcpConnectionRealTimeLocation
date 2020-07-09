package com.farid.taskpa.helper

import android.content.Context
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.farid.taskpa.MainActivity
import com.farid.taskpa.R
import com.farid.taskpa.model.User
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker


class MapInfoWindowAdapter(var context: Context?) :
    GoogleMap.InfoWindowAdapter {


    override fun getInfoContents(p0: Marker?): View? {
        // Getting view from the layout file
        val v: View? =
            (context as MainActivity).layoutInflater.inflate(R.layout.info_window_layout, null)
        val title: AppCompatTextView? = v?.findViewById(R.id.title)
        val address: AppCompatTextView? = v?.findViewById(R.id.distance)
        val iv: AppCompatImageView? = v?.findViewById(R.id.markerImage)
        val user = p0?.tag as User
        title?.text = user.name
        address?.text = user.address
        PicassoTrustAll.getInstance(context).load(user.imageUrl)
            .placeholder(R.drawable.image_placeholder).transform(CircleTransform())
            .into(iv)
        return v
    }

    override fun getInfoWindow(p0: Marker?): View? {
        return null
    }
}