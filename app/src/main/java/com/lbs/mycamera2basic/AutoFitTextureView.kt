package com.lbs.mycamera2basic

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView

class AutoFitTextureView(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    TextureView(context, attrs, defStyleAttr) {

    private var mRatioWidth = 0
    private var mRatioHeight = 0

    fun setRatio(width:Int, height:Int){
        if(width<0||height<0){
            throw java.lang.IllegalArgumentException("Size cannot be negative")
        }

        mRatioWidth = width
        mRatioHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        if(mRatioHeight == 0 || mRatioWidth == 0){
            setMeasuredDimension(width, height)
        }else{
            // LBSTag1
            // 这里是保持小的边不变，收缩另一边
            // 感觉怪怪的，后期优化一下保持宽度不变
            if(width * mRatioHeight < height * mRatioWidth){
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth)
            }else{
                setMeasuredDimension(height*mRatioWidth/mRatioHeight, height)
            }
        }
    }
}