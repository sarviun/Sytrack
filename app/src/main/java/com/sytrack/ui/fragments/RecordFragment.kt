package com.sytrack.ui.fragments

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.*
import android.view.View.VISIBLE
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin
import com.mapbox.maps.plugin.locationcomponent.location
import com.sytrack.R
import com.sytrack.databinding.FragmentRecordBinding
import com.sytrack.db.RecordPosition
import com.sytrack.services.RecordingService
import com.sytrack.ui.viewmodels.RecordViewModel
import com.sytrack.utils.Constants.ACTION_START_FOREGROUND_SERVICE
import com.sytrack.utils.Constants.ACTION_START_SERVICE
import com.sytrack.utils.Constants.ACTION_STOP_FOREGROUND_SERVICE
import com.sytrack.utils.Constants.DEFAULT_LINE_COLOR
import com.sytrack.utils.Constants.DEFAULT_LINE_WIDTH
import com.sytrack.utils.Constants.MAPBOX_DEFAULT_ZOOM
import com.sytrack.utils.DrawableUtils
import com.sytrack.utils.PermissionUtility
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class RecordFragment : Fragment(R.layout.fragment_record) {

    private val viewModel: RecordViewModel by viewModels()
    private lateinit var binding: FragmentRecordBinding
    private lateinit var snackbar: Snackbar
    private var locationComponentPlugin: LocationComponentPlugin? = null

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    private var isRecording: Boolean = false
    private var isCurrentPositionOn: Boolean = true
    private var isFABExpanded: Boolean = false

    private var points = mutableListOf<RecordPosition>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        binding = FragmentRecordBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (checkPermission())
            onMapReady()

        makeSnackBar()
        subscribeToObservers()
    }

    /**
     * Init new permission asking
     */
    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    showPermissionDialog()
                else
                    onMapReady()
            }
        }

    private val requestExtraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) onMapReady()
        }

    private fun checkPermission() =
        if (PermissionUtility.hasLocationPermission(requireContext()))
            true
        else {
            requestPermission.launch(PermissionUtility.getStandardPermission())
            false
        }

    private fun checkExtraPermission() {
        requestExtraPermission.launch(PermissionUtility.getExtraPermission())
    }

    private fun showPermissionDialog() {
        val alertDialog = AlertDialog.Builder(requireContext())
        alertDialog.apply {
            setTitle(getString(R.string.permission_dialog_title))
            setMessage(getString(R.string.permission_dialog_text))
            setPositiveButton(android.R.string.ok) { _, _ -> checkExtraPermission() }
        }.create().show()
    }


    /** map init */

    private val onMoveListener = object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) {
            stopListeningUserPosition()
        }

        override fun onMove(detector: MoveGestureDetector): Boolean {
            return false
        }

        override fun onMoveEnd(detector: MoveGestureDetector) {}
    }

    private fun stopListeningUserPosition() {
        isCurrentPositionOn = false
        binding.currentPositionFab.setImageDrawable(
            AppCompatResources.getDrawable(
                requireContext(),
                R.drawable.ic_baseline_gps_not_fixed_24,
            )
        )
    }

    private fun onMapReady() {
        binding.mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .zoom(MAPBOX_DEFAULT_ZOOM)
                .build()
        )
        binding.mapView.getMapboxMap().loadStyleUri(
            Style.MAPBOX_STREETS
        ) {
            initLocationComponent()
            setupMapRelatedFABs()
            addMarkers(points)
            sendCommandToService(ACTION_START_SERVICE)
        }
    }

    private fun makeSnackBar() {
        snackbar = Snackbar.make(
            binding.root,
            R.string.waiting_for_position,
            Snackbar.LENGTH_INDEFINITE
        )
    }

    private fun updateRecordFAB(isRecording: Boolean) {
        if (isRecording) {
            binding.recordFab.setImageDrawable(
                AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.ic_baseline_stop_24
                )
            )
        } else {
            binding.recordFab.setImageDrawable(
                AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.ic_baseline_fiber_manual_record_24
                )
            )
        }
    }

    private fun setupMapRelatedFABs() {
        binding.currentPositionFab.setOnClickListener {
            isCurrentPositionOn = true
            binding.currentPositionFab.setImageDrawable(
                AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.ic_baseline_gps_fixed_24,
                )
            )
        }

        binding.recordFab.setOnClickListener {
            updateRecordFAB(isRecording)
            if (!isRecording) {
                sendCommandToService(ACTION_START_FOREGROUND_SERVICE)
            } else {
                sendCommandToService(ACTION_STOP_FOREGROUND_SERVICE)
            }
        }

        binding.NSEWFab.setOnClickListener {
            showDirectionFABs(isFABExpanded)
            isFABExpanded = !isFABExpanded
        }

        binding.nFab.setOnClickListener {
            var mostNorthernPoint = points.maxByOrNull { point -> point.latitude }
            stopListeningUserPosition()
            moveCameraToPosition(mostNorthernPoint)
        }

        binding.sFab.setOnClickListener {
            var mostSouthernPoint = points.minByOrNull { point -> point.latitude }
            stopListeningUserPosition()
            moveCameraToPosition(mostSouthernPoint)
        }

        binding.eFab.setOnClickListener {
            var mostEasternPoint = points.maxByOrNull { point -> point.longitude }
            stopListeningUserPosition()
            moveCameraToPosition(mostEasternPoint)
        }

        binding.wFab.setOnClickListener {
            var mostWesternPoint = points.minByOrNull { point -> point.longitude }
            stopListeningUserPosition()
            moveCameraToPosition(mostWesternPoint)
        }
    }

    private fun showDirectionFABs(fabExpanded: Boolean) {
        if (fabExpanded) {
            binding.nFab.hide()
            binding.wFab.hide()
            binding.eFab.hide()
            binding.sFab.hide()
        } else {
            binding.nFab.show()
            binding.wFab.show()
            binding.eFab.show()
            binding.sFab.show()
        }
    }

    private fun initLocationComponent() {
        locationComponentPlugin = binding.mapView.location
        updatePuckAppearance(isRecording)
        binding.mapView.gestures.addOnMoveListener(onMoveListener)
    }

    private fun updatePuckAppearance(isRecording: Boolean) {

        locationComponentPlugin?.let {
            it.updateSettings {
                pulsingEnabled = !isRecording
                enabled = true
                locationPuck = if (!isRecording) {
                    LocationPuck2D(
                        bearingImage = AppCompatResources.getDrawable(
                            requireContext(),
                            R.drawable.mapbox_user_puck_icon,
                        ),
                        shadowImage = AppCompatResources.getDrawable(
                            requireContext(),
                            R.drawable.mapbox_user_icon_shadow,
                        ),
                        scaleExpression = interpolate {
                            linear()
                            zoom()
                            stop {
                                literal(0.0)
                                literal(0.6)
                            }
                            stop {
                                literal(20.0)
                                literal(1.0)
                            }
                        }.toJson()
                    )
                } else {
                    LocationPuck2D()
                }
            }
        }
    }

    private fun subscribeToObservers() {
        RecordingService.isRecordingOn.observe(viewLifecycleOwner, {
            isRecording = it
            updatePuckAppearance(isRecording)
            updateRecordFAB(isRecording)
            showDirectionFAB(isRecording)
        })

        RecordingService.currentPosition.observe(viewLifecycleOwner, {
            if (it == null)
                snackbar.show()
            else {
                snackbar.dismiss()
                binding.recordFab.show()
                binding.currentPositionFab.show()
            }

            if (isCurrentPositionOn)
                moveCameraToPosition(it)
        })

        RecordingService.recordedPoints.observe(viewLifecycleOwner, {
            points = it
            if (isCurrentPositionOn)
                moveCameraToUser()

            addMarker()
            addLine()
        })
    }

    private fun showDirectionFAB(isRecording: Boolean) {
        if (isRecording) {
            binding.NSEWFab.show()
        } else {
            binding.NSEWFab.hide()
            showDirectionFABs(true)
        }
    }

    private fun addLine() {
        if (points.size < 2)
            return

        val annotationApi = binding.mapView.annotations
        val polylineAnnotationManager = annotationApi.createPolylineAnnotationManager()

        val points = listOf(
            Point.fromLngLat(points[points.size - 2].longitude, points[points.size - 2].latitude),
            Point.fromLngLat(points.last().longitude, points.last().latitude)
        )

        val polylineAnnotationOptions: PolylineAnnotationOptions = PolylineAnnotationOptions()
            .withPoints(points)
            .withLineColor(sharedPreferences.getString(getString(R.string.line_color_key), DEFAULT_LINE_COLOR)!!)
            .withLineWidth(sharedPreferences.getString(getString(R.string.line_width_key), DEFAULT_LINE_WIDTH)!!.toDouble())

        polylineAnnotationManager.create(polylineAnnotationOptions)
    }

    private fun moveCameraToUser() {
        if (points.isNotEmpty()) {
            val point = Point.fromLngLat(points.last().longitude, points.last().latitude)
            binding.mapView.getMapboxMap().setCamera(CameraOptions.Builder().center(point).build())
            binding.mapView.gestures.focalPoint =
                binding.mapView.getMapboxMap().pixelForCoordinate(point)
        }
    }

    private fun moveCameraToPosition(position: RecordPosition?) {
        position?.let {
            val point = Point.fromLngLat(position.longitude, position.latitude)
            binding.mapView.getMapboxMap().setCamera(CameraOptions.Builder().center(point).build())
            binding.mapView.gestures.focalPoint =
                binding.mapView.getMapboxMap().pixelForCoordinate(point)
        }
    }

    private fun addMarker() {
        if (points.isEmpty())
            return

        val annotationApi = binding.mapView.annotations
        val pointAnnotationManager = annotationApi.createPointAnnotationManager()

        DrawableUtils.bitmapFromDrawableRes(
            requireContext(),
            R.drawable.ic_baseline_room_24
        )?.let {
            val recordedPoint = points.last()
            val point = Point.fromLngLat(recordedPoint.longitude, recordedPoint.latitude)
            val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(it)
            pointAnnotationManager.apply {
                create(pointAnnotationOptions)
                addClickListener(OnPointAnnotationClickListener {

                    bindToBottomSheet(recordedPoint)

                    binding.bottomSheetBehaviourLayout.visibility = VISIBLE
                    val behavior = BottomSheetBehavior.from(binding.bottomSheetBehaviourLayout)
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED

                    true
                })
            }
        }
    }

    private fun bindToBottomSheet(recordedPoint: RecordPosition) {
        binding.latitude.text = recordedPoint.latitude.toString()
        binding.longitude.text = recordedPoint.longitude.toString()
        binding.accuracy.text = recordedPoint.accuracy.toString()
        binding.provider.text = recordedPoint.provider.toString()
        binding.time.text = recordedPoint.time.convertToTime()
        binding.titleLocation.text =
            getString(
                R.string.recorded_location,
                points.indexOf(recordedPoint)
            )
    }

    //TODO: Coroutine
    private fun addMarkers(locations: List<RecordPosition>) {
        if (!isRecording)
            return

        val annotationApi = binding.mapView.annotations
        val pointAnnotationManager = annotationApi.createPointAnnotationManager()

        DrawableUtils.bitmapFromDrawableRes(
            requireContext(),
            R.drawable.ic_baseline_room_24
        )?.let {
            for (location in locations) {
                val point = Point.fromLngLat(location.longitude, location.latitude)
                val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage(it)
                pointAnnotationManager.create(pointAnnotationOptions)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.gestures.removeOnMoveListener(onMoveListener)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_recording_fragment, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    private fun sendCommandToService(action: String) {
        Intent(requireContext(), RecordingService::class.java).also {
            it.action = action
            requireContext().startService(it)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> {
                this.findNavController()
                    .navigate(RecordFragmentDirections.actionRecordFragmentToSettingsFragment())
            }
        }
        return super.onOptionsItemSelected(item)
    }


    private fun Long?.convertToTime(): String {
        this?.let {
            val date = Date(it)
            val format = SimpleDateFormat.getDateTimeInstance()
            return format.format(date)
        }
        return ""
    }
}