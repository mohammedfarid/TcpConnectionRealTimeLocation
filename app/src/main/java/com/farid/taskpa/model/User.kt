package com.farid.taskpa.model

data class User(
    var userId: Int? = null,
    var name: String? = null,
    var imageUrl: String? = null,
    var lat: Double? = null,
    var lng: Double? = null,
    var address: String? = null
)