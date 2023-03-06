package com.lbs.mycamera2basic

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.view.WindowInsets
import androidx.core.content.ContextCompat

class Camera2BasicFragment : Fragment() {

    private var mBackgroundThread:HandlerThread? = null
    private var mBackgroundHandler:Handler? = null
    private var mTextureView:TextureView? = null
    private var mImageReader:ImageReader ? = null
    private var mCameraId:String ? = null
    private var mCameraDevice:CameraDevice? = null
    private var mPreviewSize:Size? = null
    private val mSurfaceTextureListener = object:SurfaceTextureListener{
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height);
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    }
    private val mCameraDeviceStateCallback = object:CameraDevice.StateCallback(){
        override fun onOpened(camera: CameraDevice) {
            mCameraDevice = camera
            createCameraPreViewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {

        }

        override fun onError(camera: CameraDevice, error: Int) {}

    }

    private fun createCameraPreViewSession() {

    }


    fun newInstance() = Camera2BasicFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera2_basic, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mTextureView = view.findViewById(R.id.texture)
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        mTextureView?.apply {
            if(isAvailable) openCamera(width,height) else { surfaceTextureListener = mSurfaceTextureListener}
        }
    }

    override fun onPause() {

        stopBackgroundThread()
        super.onPause()
    }

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground").apply {
            start()
            mBackgroundHandler=Handler(looper)
        }
    }

    private fun stopBackgroundThread() {
        mBackgroundThread?.quitSafely()
        try {
            mBackgroundThread?.join()
            mBackgroundThread=null
            mBackgroundHandler=null
        }catch (e:InterruptedException){
            e.printStackTrace()
        }
    }

    private fun openCamera(width:Int, height:Int) {
        requestCameraPermission()
        setupCameraOutputs(width, height)
        openCameraById()
    }

    private fun requestCameraPermission(){
        // 发现没有权限
        if(ContextCompat.checkSelfPermission(requireActivity(), android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_DENIED){
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }
    }

    // 简化，得不到权限直接销毁activity
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(requestCode == REQUEST_CAMERA_PERMISSION){
            if(grantResults.size!=1 || grantResults[0]!=PackageManager.PERMISSION_GRANTED){
                requireActivity().finish()
            }
        }else{
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    // 选择摄像头并设置
    private fun setupCameraOutputs(width: Int, height: Int) {
        val manager:CameraManager= requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for(cameraId in manager.cameraIdList){
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if(facing!=null && facing==CameraCharacteristics.LENS_FACING_FRONT){
                    continue
                }

                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: continue

                // 目前只适配手机且只有一个方向！！！
                val metrics = requireActivity().windowManager.currentWindowMetrics
                val insets= metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()
                        or WindowInsets.Type.displayCutout())
                val insetsWidth = insets.right+insets.left
                val insetsHeight = insets.top+insets.bottom
                val bounds = metrics.bounds
                val displaySize = Size(bounds.width()-insetsWidth, bounds.height()-insetsHeight)

                val rotatedPreviewWidth = width
                val rotatedPreviewHeight = height
                val maxPreviewWidth = displaySize.width
                val maxPreviewHeight = displaySize.height

                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture),rotatedPreviewWidth, rotatedPreviewHeight, )
            }
        }catch (e:Exception){
            e.printStackTrace()
        }
    }

    private fun openCameraById() {

    }

    private fun chooseOptimalSize(choices:Array<Size>, textureViewWidth:Int, textureViewHeight:Int,
                                  maxWidth:Int, maxHeight:Int, aspectRatio:Size):Size{

    }

    // 暂放
    private fun configureTransform(width: Int, height: Int) {

    }

    companion object{
        const val REQUEST_CAMERA_PERMISSION = 1
    }
}