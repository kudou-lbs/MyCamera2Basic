package com.lbs.mycamera2basic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.view.WindowInsets
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Collections
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class Camera2BasicFragment : Fragment() {

    private var mBackgroundThread:HandlerThread? = null
    private var mBackgroundHandler:Handler? = null
    private var mTextureView:AutoFitTextureView? = null
    private var mImageReader:ImageReader ? = null
    private var mCameraId:String ? = null
    private var mCameraDevice:CameraDevice? = null
    private var mPreviewSize:Size? = null
    private var mPreViewRequestBuilder:CaptureRequest.Builder?=null
    private var mPreViewRequest:CaptureRequest?=null
    private var mCaptureSession:CameraCaptureSession?=null
    private val mCameraOpenCloseLock = Semaphore(1)

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
            mCameraOpenCloseLock.release()
            mCameraDevice = camera
            createCameraPreViewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            mCameraOpenCloseLock.release()
            mCameraDevice?.close()
            mCameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            mCameraOpenCloseLock.release()
            mCameraDevice?.close()
            mCameraDevice = null
            activity?.finish()
        }

    }

    // LBSTags5：还没拍照，先不写
    private val mCaptureCallback = object:CameraCaptureSession.CaptureCallback(){
        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            super.onCaptureProgressed(session, request, partialResult)
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
        }
    }

    private fun createCameraPreViewSession() {
        val texture = mTextureView!!.surfaceTexture
        texture!!.setDefaultBufferSize(mPreviewSize!!.width,mPreviewSize!!.height)
        val surface = Surface(texture)
        mPreViewRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
        }
        val sessionConfiguration = SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
            Collections.singletonList(OutputConfiguration(surface)),
            requireActivity().mainExecutor,
            object:CameraCaptureSession.StateCallback(){
            override fun onConfigured(session: CameraCaptureSession) {
                mCaptureSession=session
                try {
                    mPreViewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    //LBSTag4 闪光灯先阉割了
                    mPreViewRequest=mPreViewRequestBuilder!!.build()
                    mCaptureSession!!.setRepeatingRequest(mPreViewRequest!!,mCaptureCallback,mBackgroundHandler)
                }catch (e:Exception){
                    e.printStackTrace()
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {

            }

        })
        mCameraDevice!!.createCaptureSession(sessionConfiguration)
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

    /**
     * @param width The width of current SurfaceTexture
     * @param height The height of current SurfaceTexture
     */
    private fun openCamera(width:Int, height:Int) {
        requestCameraPermission()
        setupCameraOutputs(width, height)
        configureTransform(width,height)
        openCameraById()
    }

    private fun requestCameraPermission(){
        // 发现没有权限
        if(ContextCompat.checkSelfPermission(requireActivity(), android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
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

                val maxPreviewWidth = displaySize.width.coerceAtMost(MAX_DISPLAY_WIDTH)
                val maxPreviewHeight = displaySize.height.coerceAtMost(MAX_DISPLAY_HEIGHT)

                // 这里选择符合3：4的最适合的Size
                val size = Size(3, 4)
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java),width, height, maxPreviewWidth, maxPreviewHeight, size);
                mTextureView!!.setRatio(size.width, size.height)

                // LBSTags2：闪光灯判断
                // val available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)

                mCameraId = cameraId
                return
            }
        }catch (e:Exception){
            e.printStackTrace()
        }
    }

    private fun openCameraById() {
        val manager = requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if(!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)){
                throw java.lang.RuntimeException("Time out waiting to lock camera opening.")
            }
            if (ActivityCompat.checkSelfPermission(
                    requireActivity(),
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // 不给权限？粗暴关闭
                return
            }
            mCameraId?.let {
                manager.openCamera(it, mCameraDeviceStateCallback, mBackgroundHandler)
            };
        }catch (e:Exception){
            e.printStackTrace()
        }
    }

    /** 除了choices外，所有的widths和heights都是相对于display而言
     * @return the optimal size of display
     */
    private fun chooseOptimalSize(choices:Array<Size>, textureViewWidth:Int, textureViewHeight:Int,
                                  maxWidth:Int, maxHeight:Int, aspectRatio:Size):Size{
        if(choices.isEmpty())
            throw IllegalArgumentException("choices is empty, which means there are not suitable format for your class")

        val bigEnoughList = ArrayList<Size>()
        val notBigEnoughList = ArrayList<Size>()

        for(choice in choices){
            if(choice.width<=maxHeight && choice.height<=maxWidth
                && choice.width*aspectRatio.width==choice.height*aspectRatio.height){
                if(choice.width>=textureViewHeight && choice.height>=textureViewWidth){
                    bigEnoughList.add(choice)
                }else{
                    notBigEnoughList.add(choice)
                }
            }
        }
        var reverseRes:Size? = null
        if (bigEnoughList.isNotEmpty()){
            reverseRes = Collections.min(bigEnoughList,CompareSizesByArea());
        }else if(notBigEnoughList.isNotEmpty()){
            reverseRes = Collections.max(notBigEnoughList, CompareSizesByArea());
        }else{
            Log.d(TAGS, "could not find a Size suitable for your aspectRatio")
            reverseRes = choices[0];
        }
        return Size(reverseRes!!.height, reverseRes!!.width)
    }

    private fun configureTransform(width: Int, height: Int) {
        /*notNull(activity, mTextureView, mPreviewSize){
            // 屏幕自然方向
            val rotation = activity?.display?.rotation?:Surface.ROTATION_0
            val matrix = Matrix();
            val viewRect = RectF(0F, 0F, width.toFloat(), height.toFloat())
            val bufferRect = RectF(0F, 0F, mPreviewSize!!.height.toFloat(), mPreviewSize!!.width.toFloat())
            val centerX = viewRect.centerX()
            val centerY = viewRect.centerY()
            // LBSTag3 这部分，感觉没必要啊，相机不转啊，先放放
            when(rotation){
                Surface.ROTATION_90, Surface.ROTATION_270 -> {

                }
            }
            mTextureView?.setTransform(matrix)
        }*/

        val rotation = requireActivity().display!!.rotation

    }

    inline fun <T> notNull(vararg args:Any?, block:()->T)=
        when (args.filterNotNull().size) {
            args.size -> block()
            else -> null
        }

    companion object{
        const val REQUEST_CAMERA_PERMISSION = 1
        const val MAX_DISPLAY_WIDTH = 1080
        const val MAX_DISPLAY_HEIGHT = 1920
        const val TAGS = "LBSTags"
    }

    class CompareSizesByArea:Comparator<Size>{
        override fun compare(o1: Size, o2: Size): Int {
            return o1.height*o1.width -o2.height*o2.width
        }
    }
}