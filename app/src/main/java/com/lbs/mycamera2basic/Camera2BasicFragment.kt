package com.lbs.mycamera2basic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
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
import android.view.TextureView.SurfaceTextureListener
import android.view.WindowInsets
import android.widget.Switch
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Collections
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class Camera2BasicFragment : Fragment(), View.OnClickListener {

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
    // 当前状态，用于追踪拍照进度（3A等），默认处于预览状态，这个状态下mCaptureCallback并不会执行什么额外行为
    private var mState = STATE_PREVIEW

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

        fun process(result:CaptureResult){
            when(mState){
                STATE_PREVIEW -> {}
                STATE_WAITING_LOCK ->{
                    val afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    // 对焦结束或相机不支持自带对焦
                    when(afState){
                        null ->  captureStillPicture()
                        CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED,
                        CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ->{
                            val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                            when(aeState){
                                null -> {
                                    mState = STATE_PICTURE_TAKEN
                                    captureStillPicture()
                                }
                                else -> {
                                    runPrecaptureSequence()
                                }
                            }
                        }
                    }

                }
            }
        }

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            process(partialResult)
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            process(result)
        }
    }

    private fun runPrecaptureSequence() {
        TODO("Not yet implemented")
    }

    private fun captureStillPicture() {
        TODO("Not yet implemented")
    }

    private fun createCameraPreViewSession() {
        val texture = mTextureView!!.surfaceTexture
        texture!!.setDefaultBufferSize(mPreviewSize!!.width,mPreviewSize!!.height)
        val surface = Surface(texture)
        mPreViewRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
        }
        // 会话配置。
        val sessionConfiguration = SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
            listOfNotNull(OutputConfiguration(surface), OutputConfiguration(mImageReader!!.surface)),
            requireActivity().mainExecutor,
            object:CameraCaptureSession.StateCallback(){
            override fun onConfigured(session: CameraCaptureSession) {
                mCaptureSession=session
                try {
                    // 自动对焦
                    mPreViewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE,
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
        view.findViewById<View>(R.id.take_photo).setOnClickListener(this)
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
        closeCaMera()
        stopBackgroundThread()
        super.onPause()
    }

    private fun closeCaMera() {
        try {
            mCameraOpenCloseLock.acquire()
            mCaptureSession?.close()
            mCaptureSession=null
            mCameraDevice?.close()
            mCameraDevice=null
            mImageReader?.close()
            mImageReader=null
        }catch (e:InterruptedException){
            throw java.lang.RuntimeException("Interrupted while trying to lock camera closing", e)
        }finally {
            mCameraOpenCloseLock.release()
        }
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
        setUpCameraOutputs(width, height)
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
    private fun setUpCameraOutputs(width: Int, height: Int) {
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
                // LBSTags：这部分还不太明白，只知道是要获取屏幕尺寸
                val metrics = requireActivity().windowManager.currentWindowMetrics
                val insets= metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()
                        or WindowInsets.Type.displayCutout())
                val insetsWidth = insets.right+insets.left
                val insetsHeight = insets.top+insets.bottom
                val bounds = metrics.bounds
                val displaySize = Size(bounds.width()-insetsWidth, bounds.height()-insetsHeight)

                val maxPreviewWidth = displaySize.width.coerceAtMost(MAX_DISPLAY_WIDTH)
                val maxPreviewHeight = displaySize.height.coerceAtMost(MAX_DISPLAY_HEIGHT)

                var mImageReaderSize:Size?=null
                // 这里选择符合3：4的最适合的Size
                val size = Size(3, 4)
                val sizesOfImageReaderAndDisplay= chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java),width, height, maxPreviewWidth, maxPreviewHeight, size);
                mImageReaderSize = sizesOfImageReaderAndDisplay.first
                mImageReader = ImageReader.newInstance(mImageReaderSize.width, mImageReaderSize.height,
                    ImageFormat.JPEG, 2)
                mPreviewSize = sizesOfImageReaderAndDisplay.second
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
     * @return size1 is the largest size among the options that meet the requirements (for ImageReader)
     * @return size2 is the most suitable size for display
     */
    private fun chooseOptimalSize(choices:Array<Size>, textureViewWidth:Int, textureViewHeight:Int,
                                  maxWidth:Int, maxHeight:Int, aspectRatio:Size):Pair<Size, Size>{
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

        var reverseRes1:Size? = null
        var reverseRes2:Size? = null
        if (bigEnoughList.isNotEmpty()){
            reverseRes1 = Collections.max(bigEnoughList,CompareSizesByArea());
            reverseRes2 = Collections.min(bigEnoughList,CompareSizesByArea());
        }else if(notBigEnoughList.isNotEmpty()){
            reverseRes1 = Collections.max(notBigEnoughList, CompareSizesByArea());
            reverseRes2 = reverseRes1
        }else{
            Log.d(TAGS, "could not find a Size suitable for your aspectRatio")
            reverseRes1 = choices[0];
            reverseRes2 = reverseRes1
        }
        return Pair(Size(reverseRes1!!.height, reverseRes1!!.width),Size(reverseRes2!!.height, reverseRes2!!.width))
    }

    /**
     * empty function: It is used to adapt to different screen sizes and orientations,
     *          but currently only the vertical screen of mobile phones is studied,
     *          and no conversion is required, so it is empty
     */
    private fun configureTransform(width: Int, height: Int) {

    }

    private fun takePhoto() {
        try {
            // 设置自动对焦，此处的builder是复用预览的，部分属性保留，包括为预览模式，target为texture
            // 此处将CONTROL_AF_TRIGGER设置为触发自动对焦（之前为）
            mPreViewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_START)
            // 等待对焦
            mState = STATE_WAITING_LOCK
            mCaptureSession!!.capture(mPreViewRequestBuilder!!.build(), mCaptureCallback,
                mBackgroundHandler)
        }catch (e:Exception){
            e.printStackTrace()
        }
    }

    inline fun <T> notNull(vararg args:Any?, block:()->T)=
        when (args.filterNotNull().size) {
            args.size -> block()
            else -> null
        }

    class CompareSizesByArea:Comparator<Size>{
        override fun compare(o1: Size, o2: Size): Int {
            return o1.height*o1.width -o2.height*o2.width
        }
    }

    override fun onClick(v: View?) {
        v?.apply {
            when(id){
                R.id.take_photo -> {
                    takePhoto();
                }
            }
        }
    }

    companion object{
        const val REQUEST_CAMERA_PERMISSION = 1
        const val MAX_DISPLAY_WIDTH = 1080
        const val MAX_DISPLAY_HEIGHT = 1920
        const val TAGS = "LBSTags"

        const val STATE_PREVIEW = 0
        const val STATE_WAITING_LOCK = 1
        const val STATE_WAITING_PRECAPTURE = 2
        const val STATE_WAITING_NON_PRECAPTURE = 3
        const val STATE_PICTURE_TAKEN = 4

    }
}