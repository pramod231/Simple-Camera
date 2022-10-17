package com.simplemobiletools.camera.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.transition.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.tabs.TabLayout
import com.simplemobiletools.camera.BuildConfig
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.extensions.fadeIn
import com.simplemobiletools.camera.extensions.fadeOut
import com.simplemobiletools.camera.extensions.setShadowIcon
import com.simplemobiletools.camera.extensions.toFlashModeId
import com.simplemobiletools.camera.helpers.*
import com.simplemobiletools.camera.implementations.CameraXInitializer
import com.simplemobiletools.camera.implementations.CameraXPreviewListener
import com.simplemobiletools.camera.interfaces.MyPreview
import com.simplemobiletools.camera.models.ResolutionOption
import com.simplemobiletools.camera.views.FocusCircleView
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.Release
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_flash.*
import kotlinx.android.synthetic.main.layout_top.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : SimpleActivity(), PhotoProcessor.MediaSavedListener, CameraXPreviewListener, OnMapReadyCallback {

    private companion object {
        const val CAPTURE_ANIMATION_DURATION = 500L
        const val PHOTO_MODE_INDEX = 1
        const val VIDEO_MODE_INDEX = 0
        const val PERMISSION_REQUEST_ACCESS_LOCATION = 100
    }

// location on camera
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var tvAddressTitle: TextView
    private lateinit var tvFullAddress: TextView
    private lateinit var tvLat: TextView
    private lateinit var tvLong: TextView
    private lateinit var tvDate: TextView

    var addresses: List<Address>? = null
   private lateinit var  geocoder: Geocoder
   private lateinit var  mMap: GoogleMap

    private var village: String = ""
    private var state: String = ""
    private var district: String = ""
    private var country: String = ""
    private var area: String = ""
    private var lati: Double = 0.0
    private var longi: Double = 0.0

//location on camera


    lateinit var mTimerHandler: Handler
    private lateinit var defaultScene: Scene
    private lateinit var flashModeScene: Scene
    private lateinit var mOrientationEventListener: OrientationEventListener
    private lateinit var mFocusCircleView: FocusCircleView
    private var mPreview: MyPreview? = null
    private var mediaSizeToggleGroup: MaterialButtonToggleGroup? = null
    private var mPreviewUri: Uri? = null
    private var mIsInPhotoMode = true
    private var mIsCameraAvailable = false
    private var mIsHardwareShutterHandled = false
    private var mCurrVideoRecTimer = 0
    var mLastHandledOrientation = 0

    private val tabSelectedListener = object : TabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) {
            handleTogglePhotoVideo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {


        useDynamicTheme = false
        super.onCreate(savedInstanceState)
        appLaunched(BuildConfig.APPLICATION_ID)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        initVariables()
        tryInitCamera()
        supportActionBar?.hide()
        checkWhatsNewDialog()
        setupOrientationEventListener()

        val windowInsetsController = ViewCompat.getWindowInsetsController(window.decorView)
        windowInsetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController?.hide(WindowInsetsCompat.Type.statusBars())

        if (isOreoMr1Plus()) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
    }

    private fun getCurrentLocation() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {

           requestPermission()
        }

        val task = fusedLocationProviderClient.lastLocation
        task.addOnSuccessListener { location ->
            if (location != null) {
                mapFragment.getMapAsync(OnMapReadyCallback {
                    var latLng = LatLng(location.latitude, location.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18f))


                    lati = latLng.latitude
                    longi = latLng.longitude

                    addresses = geocoder.getFromLocation(lati, longi, 1)
                    state = addresses!![0].adminArea
                    district = addresses!![0].locality
                    country = addresses!![0].countryName
                    area =  addresses!![0].getAddressLine(0)

                    val sdf = SimpleDateFormat("dd/M/yyyy")
                    val currentDate = sdf.format(Date())



                 tvDate.text = currentDate
                    tvFullAddress.text = area
                    tvAddressTitle.text = "$district, $state, $country"
                    tvLat.text = "Lat ${lati}"
                    tvLong.text = "Lat ${longi}"

                    Log.e("getCurrentLocation", latLng.latitude.toString() + "-" + latLng.longitude)
                })
            }
        }





//        addresses = geocoder.getFromLocation(lati)



//        fusedLocationProviderClient.lastLocation.addOnCompleteListener(this) { task ->
//
//            val location: Location? = task.result
//            if (location == null) {
//                Toast.makeText(this, "Failed to access location", Toast.LENGTH_LONG).show()
//
//              requestPermission()
//
//            } else {
//
//
//
//                val lat = location.latitude
//                val long = location.longitude
//
//                lati = location.latitude
//                longi = location.longitude
//
//                Log.i("OLDI","Lati is $lati & Longi is $longi")
//                Log.i("OLDI","Lat is $lat & long is $long")
//
//                if ( lat.toInt() != 0 && long.toInt() != 0){
//                    addresses = geocoder.getFromLocation(lat.toDouble(), long.toDouble(), 1)
//                    state = addresses!![0].adminArea
//                    district = addresses!![0].locality
//                    country = addresses!![0].countryName
//                    area =  addresses!![0].getAddressLine(0)
//
//                    val date = Calendar.getInstance().time
//                    val formatter = SimpleDateFormat.getDateTimeInstance() //or use getDateInstance()
//                    val formatedDate = formatter.format(date)
//
//
////                tvDateTime.text = formatedDate
//                    tvFullAddress.text = area
//                    tvAddressTitle.text = "$district, $state, $country"
//                    tvLat.text = "Lat ${location.latitude}"
//                    tvLong.text = "Lat ${location.longitude}"
//                }
//
//
//
//
//            }
//        }


    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION),
            PERMISSION_REQUEST_ACCESS_LOCATION
        )
    }

    private fun selectPhotoTab(triggerListener: Boolean = false) {
        if (!triggerListener) {
            removeTabListener()
        }
        camera_mode_tab.getTabAt(PHOTO_MODE_INDEX)?.select()
        setTabListener()
    }

    private fun selectVideoTab(triggerListener: Boolean = false) {
        if (!triggerListener) {
            removeTabListener()
        }
        camera_mode_tab.getTabAt(VIDEO_MODE_INDEX)?.select()
        setTabListener()
    }

    private fun setTabListener() {
        camera_mode_tab.addOnTabSelectedListener(tabSelectedListener)
    }

    private fun removeTabListener() {
        camera_mode_tab.removeOnTabSelectedListener(tabSelectedListener)
    }

    override fun onResume() {
        super.onResume()
        if (hasStorageAndCameraPermissions()) {
            resumeCameraItems()
            setupPreviewImage(mIsInPhotoMode)
            mFocusCircleView.setStrokeColor(getProperPrimaryColor())

            if (isVideoCaptureIntent() && mIsInPhotoMode) {
                handleTogglePhotoVideo()
                checkButtons()
            }
            toggleBottomButtons(enabled = true)
            mOrientationEventListener.enable()
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ensureTransparentNavigationBar()
    }

    private fun ensureTransparentNavigationBar() {
        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.transparent)
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!hasStorageAndCameraPermissions() || isAskingPermissions) {
            return
        }

        hideTimer()
        mOrientationEventListener.disable()
    }

    override fun onDestroy() {
        super.onDestroy()
        mPreview = null
    }

    override fun onBackPressed() {
        if (!closeOptions()) {
            super.onBackPressed()
        }
    }

    private fun initVariables() {
        mIsInPhotoMode = if (isVideoCaptureIntent()) {
            false
        } else if (isImageCaptureIntent()) {
            true
        } else {
            config.initPhotoMode
        }
        mIsCameraAvailable = false
        mIsHardwareShutterHandled = false
        mCurrVideoRecTimer = 0
        mLastHandledOrientation = 0
        config.lastUsedCamera = CameraCharacteristics.LENS_FACING_BACK.toString()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_CAMERA && !mIsHardwareShutterHandled) {
            mIsHardwareShutterHandled = true
            shutterPressed()
            true
        } else if (!mIsHardwareShutterHandled && config.volumeButtonsAsShutter && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            mIsHardwareShutterHandled = true
            shutterPressed()
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_CAMERA || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            mIsHardwareShutterHandled = false
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun hideIntentButtons() {
        camera_mode_tab.beGone()
        settings.beGone()
        last_photo_video_preview.beInvisible()
    }

    private fun tryInitCamera() {
        handlePermission(PERMISSION_CAMERA) { grantedCameraPermission ->
            if (grantedCameraPermission) {
                handleStoragePermission { grantedStoragePermission ->
                    if (grantedStoragePermission) {
                        if (mIsInPhotoMode) {
                            initializeCamera()
                        } else {
                            handlePermission(PERMISSION_RECORD_AUDIO) { grantedRecordAudioPermission ->
                                if (grantedRecordAudioPermission) {
                                    initializeCamera()
                                } else {
                                    toast(R.string.no_audio_permissions)
                                    togglePhotoVideoMode()
                                    tryInitCamera()
                                }
                            }
                        }
                    } else {
                        toast(R.string.no_storage_permissions)
                        finish()
                    }
                }
            } else {
                toast(R.string.no_camera_permissions)
                finish()
            }
        }
    }

    private fun handleStoragePermission(callback: (granted: Boolean) -> Unit) {
        if (isTiramisuPlus()) {
            handlePermission(PERMISSION_READ_MEDIA_IMAGES) { grantedReadImages ->
                if (grantedReadImages) {
                    handlePermission(PERMISSION_READ_MEDIA_VIDEO, callback)
                } else {
                    callback.invoke(false)
                }
            }
        } else {
            handlePermission(PERMISSION_WRITE_STORAGE, callback)
        }
    }

    private fun is3rdPartyIntent() = isVideoCaptureIntent() || isImageCaptureIntent()

    private fun isImageCaptureIntent(): Boolean = intent?.action == MediaStore.ACTION_IMAGE_CAPTURE || intent?.action == MediaStore.ACTION_IMAGE_CAPTURE_SECURE

    private fun isVideoCaptureIntent(): Boolean = intent?.action == MediaStore.ACTION_VIDEO_CAPTURE

    private fun checkImageCaptureIntent() {
        if (isImageCaptureIntent()) {
            hideIntentButtons()
            val output = intent.extras?.get(MediaStore.EXTRA_OUTPUT)
            if (output != null && output is Uri) {
                mPreview?.setTargetUri(output)
            }
        }
    }

    private fun checkVideoCaptureIntent() {
        if (isVideoCaptureIntent()) {
            mIsInPhotoMode = false
            hideIntentButtons()
            shutter.setImageResource(R.drawable.ic_video_rec_vector)
        }
    }

    private fun createToggleGroup(): MaterialButtonToggleGroup {
        return MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun initializeCamera() {
        setContentView(R.layout.activity_main)
        initButtons()
        requestPermission()

        //        Initialization
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        tvAddressTitle = findViewById(R.id.tvAddressTitle)
        tvFullAddress = findViewById(R.id.tvFullAddress)
        tvLat = findViewById(R.id.tvLat)
        tvLong = findViewById(R.id.tvLong)
        tvDate = findViewById(R.id.tvDate)

        mapFragment = supportFragmentManager.findFragmentById(R.id.googleMapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        getCurrentLocation()
        geocoder = Geocoder(this@MainActivity)





//        Initialization

        defaultScene = Scene(top_options, default_icons)
        flashModeScene = Scene(top_options, flash_toggle_group)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(view_holder) { _, windowInsets ->
            val safeInsetBottom = windowInsets.displayCutout?.safeInsetBottom ?: 0
            val safeInsetTop = windowInsets.displayCutout?.safeInsetTop ?: 0

            top_options.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = safeInsetTop
            }

            val marginBottom = safeInsetBottom + navigationBarHeight + resources.getDimensionPixelSize(R.dimen.bigger_margin)

            shutter.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = marginBottom
            }

            WindowInsetsCompat.CONSUMED
        }

        checkVideoCaptureIntent()
        if (mIsInPhotoMode) {
            selectPhotoTab()
        } else {
            selectVideoTab()
        }

        val outputUri = intent.extras?.get(MediaStore.EXTRA_OUTPUT) as? Uri
        val is3rdPartyIntent = is3rdPartyIntent()
        mPreview = CameraXInitializer(this).createCameraXPreview(
            preview_view,
            listener = this,
            outputUri = outputUri,
            is3rdPartyIntent = is3rdPartyIntent,
            initInPhotoMode = mIsInPhotoMode,
        )
        checkImageCaptureIntent()
        mPreview?.setIsImageCaptureIntent(isImageCaptureIntent())

        val imageDrawable = if (config.lastUsedCamera == CameraCharacteristics.LENS_FACING_BACK.toString()) {
            R.drawable.ic_camera_front_vector
        } else {
            R.drawable.ic_camera_rear_vector
        }

        toggle_camera.setImageResource(imageDrawable)

        mFocusCircleView = FocusCircleView(applicationContext)
        view_holder.addView(mFocusCircleView)

        mTimerHandler = Handler(Looper.getMainLooper())
        setupPreviewImage(true)

        val initialFlashlightState = if (mIsInPhotoMode) config.flashlightState else FLASH_OFF
        mPreview!!.setFlashlightState(initialFlashlightState)
        updateFlashlightState(initialFlashlightState)
        initFlashModeTransitionNames()
    }

    private fun initFlashModeTransitionNames() {
        val baseName = getString(R.string.toggle_flash)
        flash_auto.transitionName = "$baseName$FLASH_AUTO"
        flash_off.transitionName = "$baseName$FLASH_OFF"
        flash_on.transitionName = "$baseName$FLASH_ON"
    }

    private fun initButtons() {
        toggle_camera.setOnClickListener { toggleCamera() }
        last_photo_video_preview.setOnClickListener { showLastMediaPreview() }
        toggle_flash.setOnClickListener { toggleFlash() }
        shutter.setOnClickListener { shutterPressed() }

        settings.setShadowIcon(R.drawable.ic_settings_vector)
        settings.setOnClickListener { launchSettings() }

        change_resolution.setOnClickListener { mPreview?.showChangeResolution() }

        flash_on.setShadowIcon(R.drawable.ic_flash_on_vector)
        flash_on.setOnClickListener { selectFlashMode(FLASH_ON) }

        flash_off.setShadowIcon(R.drawable.ic_flash_off_vector)
        flash_off.setOnClickListener { selectFlashMode(FLASH_OFF) }

        flash_auto.setShadowIcon(R.drawable.ic_flash_auto_vector)
        flash_auto.setOnClickListener { selectFlashMode(FLASH_AUTO) }
    }

    private fun selectFlashMode(flashMode: Int) {
        closeOptions()
        mPreview?.setFlashlightState(flashMode)
    }

    private fun toggleCamera() {
        if (checkCameraAvailable()) {
            mPreview!!.toggleFrontBackCamera()
        }
    }

    private fun showLastMediaPreview() {
        if (mPreviewUri != null) {
            val path = applicationContext.getRealPathFromURI(mPreviewUri!!) ?: mPreviewUri!!.toString()
            openPathIntent(path, false, BuildConfig.APPLICATION_ID)
        }
    }

    private fun toggleFlash() {
        if (checkCameraAvailable()) {
            if (mIsInPhotoMode) {
                showFlashOptions(true)
            } else {
                mPreview?.toggleFlashlight()
            }
        }
    }

    private fun updateFlashlightState(state: Int) {
        config.flashlightState = state
        val flashDrawable = when (state) {
            FLASH_OFF -> R.drawable.ic_flash_off_vector
            FLASH_ON -> R.drawable.ic_flash_on_vector
            else -> R.drawable.ic_flash_auto_vector
        }
        toggle_flash.setShadowIcon(flashDrawable)
        toggle_flash.transitionName = "${getString(R.string.toggle_flash)}$state"
    }

    private fun shutterPressed() {
        if (checkCameraAvailable()) {
            handleShutter()
        }
    }

    private fun handleShutter() {
        if (mIsInPhotoMode) {
            toggleBottomButtons(enabled = false)
            change_resolution.isEnabled = true
            mPreview?.tryTakePicture()
        } else {
            mPreview?.toggleRecording()
        }
    }

    private fun launchSettings() {
        val intent = Intent(applicationContext, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun handleTogglePhotoVideo() {
        handlePermission(PERMISSION_RECORD_AUDIO) {
            if (it) {
                togglePhotoVideo()
            } else {
                toast(R.string.no_audio_permissions)
                selectPhotoTab()
                if (isVideoCaptureIntent()) {
                    finish()
                }
            }
        }
    }

    private fun togglePhotoVideo() {
        if (!checkCameraAvailable()) {
            return
        }

        if (isVideoCaptureIntent()) {
            mPreview?.initVideoMode()
        }

        mPreview?.setFlashlightState(FLASH_OFF)
        hideTimer()
        togglePhotoVideoMode()
        checkButtons()
        toggleBottomButtons(enabled = true)
    }

    private fun togglePhotoVideoMode() {
        mIsInPhotoMode = !mIsInPhotoMode
        config.initPhotoMode = mIsInPhotoMode
    }

    private fun checkButtons() {
        if (mIsInPhotoMode) {
            initPhotoMode()
        } else {
            tryInitVideoMode()
        }
    }

    private fun initPhotoMode() {
        shutter.setImageResource(R.drawable.ic_shutter_animated)
        mPreview?.initPhotoMode()
        setupPreviewImage(true)
        selectPhotoTab()
    }

    private fun tryInitVideoMode() {
        try {
            mPreview?.initVideoMode()
            initVideoButtons()
        } catch (e: Exception) {
            if (!isVideoCaptureIntent()) {
                toast(R.string.video_mode_error)
            }
        }
    }

    private fun initVideoButtons() {
        shutter.setImageResource(R.drawable.ic_video_rec_animated)
        setupPreviewImage(false)
        mPreview?.checkFlashlight()
        selectVideoTab()
    }

    private fun setupPreviewImage(isPhoto: Boolean) {
        val uri = if (isPhoto) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val lastMediaId = getLatestMediaId(uri)
        if (lastMediaId == 0L) {
            return
        }

        mPreviewUri = Uri.withAppendedPath(uri, lastMediaId.toString())

        loadLastTakenMedia(mPreviewUri)
    }

    private fun loadLastTakenMedia(uri: Uri?) {
        mPreviewUri = uri
        runOnUiThread {
            if (!isDestroyed) {
                val options = RequestOptions()
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)

                Glide.with(this)
                    .load(uri)
                    .apply(options)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(last_photo_video_preview)
            }
        }
    }

    private fun hideTimer() {
        video_rec_curr_timer.text = 0.getFormattedDuration()
        video_rec_curr_timer.beGone()
        mCurrVideoRecTimer = 0
        mTimerHandler.removeCallbacksAndMessages(null)
    }

    private fun resumeCameraItems() {
        if (!mIsInPhotoMode) {
            initVideoButtons()
        }
    }

    private fun hasStorageAndCameraPermissions(): Boolean {
        return if (mIsInPhotoMode) hasPhotoModePermissions() else hasVideoModePermissions()
    }

    private fun hasPhotoModePermissions(): Boolean {
        return if (isTiramisuPlus()) {
            hasPermission(PERMISSION_READ_MEDIA_IMAGES) && hasPermission(PERMISSION_READ_MEDIA_VIDEO) && hasPermission(PERMISSION_CAMERA)
        } else {
            hasPermission(PERMISSION_WRITE_STORAGE) && hasPermission(PERMISSION_CAMERA)
        }
    }

    private fun hasVideoModePermissions(): Boolean {
        return if (isTiramisuPlus()) {
            hasPermission(PERMISSION_READ_MEDIA_VIDEO) && hasPermission(PERMISSION_CAMERA) && hasPermission(PERMISSION_RECORD_AUDIO)
        } else {
            hasPermission(PERMISSION_WRITE_STORAGE) && hasPermission(PERMISSION_CAMERA) && hasPermission(PERMISSION_RECORD_AUDIO)
        }
    }

    private fun setupOrientationEventListener() {
        mOrientationEventListener = object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                if (isDestroyed) {
                    mOrientationEventListener.disable()
                    return
                }

                val currOrient = when (orientation) {
                    in 75..134 -> ORIENT_LANDSCAPE_RIGHT
                    in 225..289 -> ORIENT_LANDSCAPE_LEFT
                    else -> ORIENT_PORTRAIT
                }

                if (currOrient != mLastHandledOrientation) {
                    val degrees = when (currOrient) {
                        ORIENT_LANDSCAPE_LEFT -> 90
                        ORIENT_LANDSCAPE_RIGHT -> -90
                        else -> 0
                    }

                    animateViews(degrees)
                    mLastHandledOrientation = currOrient
                }
            }
        }
    }

    private fun animateViews(degrees: Int) {
        val views = arrayOf<View>(toggle_camera, toggle_flash, change_resolution, shutter, settings, last_photo_video_preview)
        for (view in views) {
            rotate(view, degrees)
        }
    }

    private fun rotate(view: View, degrees: Int) = view.animate().rotation(degrees.toFloat()).start()

    private fun checkCameraAvailable(): Boolean {
        if (!mIsCameraAvailable) {
            toast(R.string.camera_unavailable)
        }
        return mIsCameraAvailable
    }

    override fun setCameraAvailable(available: Boolean) {
        mIsCameraAvailable = available
    }

    override fun setHasFrontAndBackCamera(hasFrontAndBack: Boolean) {
        toggle_camera?.beVisibleIf(hasFrontAndBack)
    }

    override fun setFlashAvailable(available: Boolean) {
        if (available) {
            toggle_flash.beVisible()
        } else {
            toggle_flash.beInvisible()
            toggle_flash.setShadowIcon(R.drawable.ic_flash_off_vector)
            mPreview?.setFlashlightState(FLASH_OFF)
        }
    }

    override fun onChangeCamera(frontCamera: Boolean) {
        toggle_camera.setImageResource(if (frontCamera) R.drawable.ic_camera_rear_vector else R.drawable.ic_camera_front_vector)
    }

    override fun toggleBottomButtons(enabled: Boolean) {
        runOnUiThread {
            shutter.isClickable = enabled
            preview_view.isEnabled = enabled
            toggle_camera.isClickable = enabled
            toggle_flash.isClickable = enabled
        }
    }

    override fun shutterAnimation() {
        shutter_animation.alpha = 1.0f
        shutter_animation.animate().alpha(0f).setDuration(CAPTURE_ANIMATION_DURATION).start()
    }

    override fun onMediaSaved(uri: Uri) {
        change_resolution.isEnabled = true
        loadLastTakenMedia(uri)
        if (isImageCaptureIntent()) {
            Intent().apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                setResult(Activity.RESULT_OK, this)
            }
            finish()
        } else if (isVideoCaptureIntent()) {
            Intent().apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                setResult(Activity.RESULT_OK, this)
            }
            finish()
        }
    }

    override fun onImageCaptured(bitmap: Bitmap) {
        if (isImageCaptureIntent()) {
            Intent().apply {
                putExtra("data", bitmap)
                setResult(Activity.RESULT_OK, this)
            }
            finish()
        }
    }

    override fun onChangeFlashMode(flashMode: Int) {
        updateFlashlightState(flashMode)
    }

    override fun onVideoRecordingStarted() {
        camera_mode_tab.beInvisible()
        video_rec_curr_timer.beVisible()

        toggle_camera.fadeOut()
        last_photo_video_preview.fadeOut()

        change_resolution.isEnabled = false
        settings.isEnabled = false
        shutter.isSelected = true
    }

    override fun onVideoRecordingStopped() {
        camera_mode_tab.beVisible()

        toggle_camera.fadeIn()
        last_photo_video_preview.fadeIn()

        video_rec_curr_timer.text = 0.getFormattedDuration()
        video_rec_curr_timer.beGone()

        shutter.isSelected = false
        change_resolution.isEnabled = true
        settings.isEnabled = true
    }

    override fun onVideoDurationChanged(durationNanos: Long) {
        val seconds = TimeUnit.NANOSECONDS.toSeconds(durationNanos).toInt()
        video_rec_curr_timer.text = seconds.getFormattedDuration()
    }

    override fun onFocusCamera(xPos: Float, yPos: Float) {
        mFocusCircleView.drawFocusCircle(xPos, yPos)
    }

    override fun onSwipeLeft() {
        if (!is3rdPartyIntent() && camera_mode_tab.isVisible()) {
            selectPhotoTab(triggerListener = true)
        }
    }

    override fun onSwipeRight() {
        if (!is3rdPartyIntent() && camera_mode_tab.isVisible()) {
            selectVideoTab(triggerListener = true)
        }
    }

    override fun onTouchPreview() {
        closeOptions()
    }

    private fun closeOptions(): Boolean {
        if (mediaSizeToggleGroup?.isVisible() == true ||
            flash_toggle_group.isVisible()
        ) {
            val transitionSet = createTransition()
            TransitionManager.go(defaultScene, transitionSet)
            mediaSizeToggleGroup?.beGone()
            flash_toggle_group.beGone()
            default_icons.beVisible()
            return true
        }

        return false
    }

    override fun displaySelectedResolution(resolutionOption: ResolutionOption) {
        val imageRes = resolutionOption.imageDrawableResId
        change_resolution.setShadowIcon(imageRes)
        change_resolution.transitionName = "${resolutionOption.buttonViewId}"
    }

    override fun showImageSizes(
        selectedResolution: ResolutionOption,
        resolutions: List<ResolutionOption>,
        isPhotoCapture: Boolean,
        isFrontCamera: Boolean,
        onSelect: (index: Int, changed: Boolean) -> Unit
    ) {

        top_options.removeView(mediaSizeToggleGroup)
        val mediaSizeToggleGroup = createToggleGroup().apply {
            mediaSizeToggleGroup = this
        }
        top_options.addView(mediaSizeToggleGroup)

        val onItemClick = { clickedViewId: Int ->
            closeOptions()
            val index = resolutions.indexOfFirst { it.buttonViewId == clickedViewId }
            onSelect.invoke(index, selectedResolution.buttonViewId != clickedViewId)
        }

        resolutions.forEach {
            val button = createButton(it, onItemClick)
            mediaSizeToggleGroup.addView(button)
        }

        mediaSizeToggleGroup.check(selectedResolution.buttonViewId)

        val transitionSet = createTransition()
        val mediaSizeScene = Scene(top_options, mediaSizeToggleGroup)
        TransitionManager.go(mediaSizeScene, transitionSet)
        default_icons.beGone()
        mediaSizeToggleGroup.beVisible()
        mediaSizeToggleGroup.children.map { it as MaterialButton }.forEach(::setButtonColors)
    }

    private fun createButton(resolutionOption: ResolutionOption, onClick: (clickedViewId: Int) -> Unit): MaterialButton {
        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            weight = 1f
        }
        return (layoutInflater.inflate(R.layout.layout_button, null) as MaterialButton).apply {
            layoutParams = params
            setShadowIcon(resolutionOption.imageDrawableResId)
            id = resolutionOption.buttonViewId
            transitionName = "${resolutionOption.buttonViewId}"
            setOnClickListener {
                onClick.invoke(id)
            }
        }
    }

    private fun createTransition(): Transition {
        val fadeTransition = Fade()
        return TransitionSet().apply {
            addTransition(fadeTransition)
            this.duration = resources.getInteger(R.integer.icon_anim_duration).toLong()
        }
    }

    override fun showFlashOptions(photoCapture: Boolean) {
        val transitionSet = createTransition()
        TransitionManager.go(flashModeScene, transitionSet)
        flash_auto.beVisibleIf(photoCapture)
        flash_toggle_group.check(config.flashlightState.toFlashModeId())

        flash_toggle_group.beVisible()
        flash_toggle_group.children.forEach { setButtonColors(it as MaterialButton) }
    }

    private fun setButtonColors(button: MaterialButton) {
        val primaryColor = getProperPrimaryColor()
        val states = arrayOf(intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked))
        val iconColors = intArrayOf(ContextCompat.getColor(this, R.color.md_grey_white), primaryColor)
        button.iconTint = ColorStateList(states, iconColors)
    }

    override fun mediaSaved(path: String) {
        rescanPaths(arrayListOf(path)) {
            setupPreviewImage(true)
            Intent(BROADCAST_REFRESH_MEDIA).apply {
                putExtra(REFRESH_PATH, path)
                `package` = "com.simplemobiletools.gallery"
                sendBroadcast(this)
            }
        }

        if (isImageCaptureIntent()) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            add(Release(33, R.string.release_33))
            add(Release(35, R.string.release_35))
            add(Release(39, R.string.release_39))
            add(Release(44, R.string.release_44))
            add(Release(46, R.string.release_46))
            add(Release(52, R.string.release_52))
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
       mMap = googleMap
        mMap.uiSettings.isMapToolbarEnabled = false
        mMap.uiSettings.setAllGesturesEnabled(false)
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        mMap.isMyLocationEnabled = true

        val currentLocation = LatLng(lati, longi)
        Log.i("LATI", "latlong is $lati & Longi is $longi")

        mMap.addMarker(MarkerOptions().position(currentLocation).visible(true))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 10f))
        mMap.moveCamera(CameraUpdateFactory.zoomIn())
        mMap.animateCamera(CameraUpdateFactory.zoomTo(17f), 2000, null)


    }
}
