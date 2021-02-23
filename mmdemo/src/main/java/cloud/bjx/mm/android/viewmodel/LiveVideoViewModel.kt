package cloud.bjx.mm.android.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cloud.bjx.mm.android.RtcEngineProxy
import cloud.bjx.mm.android.UserSettings
import cloud.bjx.mm.android.bean.VideoConfigBean
import cloud.bjx.mm.android.repo.ServiceFactory
import cloud.bjx.mm.android.utils.*
import cloud.bjx.mm.sdk.Constants.*
import cloud.bjx.mm.sdk.IRtcEngineEventHandler
import cloud.bjx.mm.sdk.VideoCanvas
import cloud.bjx.mm.sdk.VideoConfiguration
import com.faceunity.FURenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.NV21Buffer
import org.webrtc.VideoFrame
import java.nio.ByteBuffer


class LiveVideoViewModelFactory(
    private val config: VideoConfigBean,
    private val mFURenderer: FURenderer
) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return LiveVideoViewModel(config, mFURenderer) as T
    }
}

class LiveVideoViewModel(private val config: VideoConfigBean, mFURenderer: FURenderer) :
    ViewModel() {

    private val rtcEngine by lazy { RtcEngineProxy.get() }

    val channelName by lazy { "频道ID: ${config.channelId}" }

    val channelJoinLiveData by lazy { MutableLiveData<Outcome<Boolean>>() }
    val channelLeaveLiveData by lazy { MutableLiveData<Boolean>() }
    val memberJoinLiveData by lazy { MutableLiveData<String>() }
    val memberLeaveLiveData by lazy { MutableLiveData<String>() }
    val videoPublishStateLiveData by lazy { MutableLiveData<Int>() }
    val remoteVideoAddLiveData by lazy { MutableLiveData<String>() }
    val audioRouteLiveData by lazy { MutableLiveData<Int>() }


    private val mEventHandler = object : IRtcEngineEventHandler() {
        override fun onError(err: Int) {
            LogUtil.e(" ---> RtcEvent, onError: ${err.errorMessage()}")
            when (err) {
                ERR_INVALID_TOKEN -> {
                    channelJoinLiveData.value = Outcome.Failure("invalid token")
                }
                ERR_CHANNEL_JOIN -> {
                    channelJoinLiveData.value = Outcome.Failure("joinChannel fail")
                }
            }
        }

        override fun onJoinChannelSuccess(channel: String?, uid: String?) {
            LogUtil.i(" ---> RtcEvent, onJoinChannelSuccess, channel=$channel, uid=$uid")
            channelJoinLiveData.value = Outcome.Success(true)
        }

        override fun onLeaveChannel() {
            LogUtil.i(" ---> RtcEvent, onLeaveChannel")
            channelLeaveLiveData.value = true
        }

        override fun onUserJoined(uid: String?) {
            LogUtil.i(" ---> RtcEvent, onUserJoined, uid=$uid")
            uid?.let { memberJoinLiveData.value = it }
        }

        override fun onUserOffline(uid: String?, reason: Int) {
            LogUtil.i(" ---> RtcEvent, onUserOffline, uid=$uid")
            uid?.let { memberLeaveLiveData.value = it }
        }

        override fun onAudioPublishStateChanged(channel: String?, oldState: Int, newState: Int) {
            LogUtil.i(
                " ---> RtcEvent, onAudioPublishStateChanged, from ${oldState.pubState()} to ${newState.pubState()}"
            )
        }

        override fun onVideoPublishStateChanged(channel: String?, oldState: Int, newState: Int) {
            LogUtil.i(
                " ---> RtcEvent, onVideoPublishStateChanged, from ${oldState.pubState()} to ${newState.pubState()}"
            )
            if (PUB_STATE_PUBLISHED == newState) {
                rtcEngine.setVideoFilterEnable(true)
            }
            videoPublishStateLiveData.value = newState
        }

        override fun onRemoteVideoAdd(uid: String?) {
            LogUtil.i(" ---> RtcEvent, onRemoteVideoAdd, uid=$uid")
            uid?.let { remoteVideoAddLiveData.value = it }
        }

        override fun onAudioRouteChanged(audioRoute: Int) {
            LogUtil.d(" ---> RtcEvent, onAudioRouteChanged, ${audioRoute.audioRoute()}")
            audioRouteLiveData.value = audioRoute
        }

        override fun videoFilterProcess(videoFrame: VideoFrame): VideoFrame {
            LogUtil.i("videoFrame=====${videoFrame.buffer.toI420()},${videoFrame.buffer.width},${videoFrame.buffer.height}")
            var frame = videoFrame
            frame.buffer
            try {
                val bufferWidth = frame.buffer.width
                val bufferHeight = frame.buffer.height
                //从报像头采集的videoFrame里获取NV21数据，FU美颜需要,如需要其他格式，可自行改写
                val data = createNV21Data(frame.buffer.toI420())

                mFURenderer.onDrawFrame(
                    data,
                    bufferWidth,
                    bufferHeight,
                    data,
                    bufferWidth,
                    bufferHeight
                )


                //转回VideoFrame格式
                frame = VideoFrame(
                    NV21Buffer(data, bufferWidth, bufferHeight, null),
                    videoFrame.rotation,
                    videoFrame.timestampNs
                )
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }

            //回写到Rtc模块里
            // mVideoSink.onFrame(frame)

            return frame
        }
    }

    private fun createNV21Data(i420Buffer: VideoFrame.I420Buffer): ByteArray? {
        val width = i420Buffer.width
        val height = i420Buffer.height
        val chromaWidth = (width + 1) / 2
        val chromaHeight = (height + 1) / 2
        val ySize = width * height
        LogUtil.v("MJH width = $width height: $height")
        val nv21Buffer: ByteBuffer = ByteBuffer.allocateDirect(ySize + width * chromaHeight)
        val nv21Data: ByteArray = nv21Buffer.array()
        var ni420BufferWidth = i420Buffer.strideY
        var nOffset = 0
        var nOffset2 = 0
        val bufferY: ByteBuffer = i420Buffer.dataY
        val bufferU: ByteBuffer = i420Buffer.dataU
        val bufferV: ByteBuffer = i420Buffer.dataV
        for (y in 0 until height) {
            for (x in 0 until width) {
                nv21Data[nOffset2 + x] = bufferY.get(nOffset + x)
            }
            nOffset += ni420BufferWidth
            nOffset2 += width
        }
        ni420BufferWidth = i420Buffer.strideU
        nOffset = 0
        nOffset2 = 0
        for (y in 0 until chromaHeight) {
            for (x in 0 until chromaWidth) {
                nv21Data[ySize + nOffset2 + (x shl 1)] = bufferV.get(nOffset + x)
                nv21Data[ySize + nOffset2 + (x shl 1) + 1] = bufferU.get(nOffset + x)
            }
            nOffset += ni420BufferWidth
            nOffset2 += width
        }
        i420Buffer.release()
        return nv21Data
    }

    /** Create an NV21Buffer with the same pixel content as the given I420 buffer.  */
    /* fun createNV21Data(i420Buffer: VideoFrame.I420Buffer): ByteArray? {
         val width = i420Buffer.width
         val height = i420Buffer.height
         val chromaWidth = (width + 1) / 2
         val chromaHeight = (height + 1) / 2
         val ySize = width * height
         val nv21Buffer = ByteBuffer.allocateDirect(ySize + width * chromaHeight)
         // We don't care what the array offset is since we only want an array that is direct.
         val nv21Data = nv21Buffer.array()
         for (y in 0 until height) {
             for (x in 0 until width) {
                 val yValue = i420Buffer.dataY[y * i420Buffer.strideY + x]
                 nv21Data[y * width + x] = yValue
             }
         }
         for (y in 0 until chromaHeight) {
             for (x in 0 until chromaWidth) {
                 val uValue = i420Buffer.dataU[y * i420Buffer.strideU + x]
                 val vValue = i420Buffer.dataV[y * i420Buffer.strideV + x]
                 nv21Data[ySize + y * width + 2 * x + 0] = vValue
                 nv21Data[ySize + y * width + 2 * x + 1] = uValue
             }
         }
         return nv21Data
     }*/

    /** Convert a byte array to a direct ByteBuffer.  */
    private fun toByteBuffer(array: IntArray): ByteBuffer? {
        val buffer = ByteBuffer.allocateDirect(array.size)
        buffer.put(toByteArray(array))
        buffer.rewind()
        return buffer
    }


    /**
     * Convert an int array to a byte array and make sure the values are within the range [0, 255].
     */
    private fun toByteArray(array: IntArray): ByteArray? {
        val res = ByteArray(array.size)
        for (i in array.indices) {
            val value = array[i]
            res[i] = value.toByte()
        }
        return res
    }

    init {
        rtcEngine.setChannelProfile(CHANNEL_PROFILE_COMMUNICATION)
        rtcEngine.addHandler(mEventHandler)
        rtcEngine.setDefaultAudioRouteToSpeakerphone(config.useSpeaker)
        rtcEngine.enableVideo()
        val videoDimen = VideoConfiguration.VideoDimensions(config.videoWidth, config.videoHeight)
        val videoConfig = VideoConfiguration(videoDimen, config.frameRate)
        LogUtil.d("----> VideoConfiguration: $videoConfig")
        rtcEngine.setVideoEncoderConfiguration(videoConfig)
        joinChannel()
    }

    override fun onCleared() {
        super.onCleared()
        rtcEngine.removeHandler(mEventHandler)
    }

    private fun joinChannel() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val uid = UserSettings.getUserId() ?: ""
                val bean = ServiceFactory.apiService.fetchToken(uid)
                LogUtil.i("fetchToken result: $bean")

                if (rtcEngine.joinChannel(bean.token, config.channelId, uid) != 0) {
                    channelJoinLiveData.value = Outcome.Failure("joinChannel fail")
                }
            } catch (e: Exception) {
                LogUtil.e("fetchToken error", e)
                channelJoinLiveData.value = Outcome.Failure("fetch token error")
            }
        }
    }

    fun leaveChannel() {
        if (rtcEngine.leaveChannel() != 0) {
            channelLeaveLiveData.value = false
        }
    }

    fun setLocalVideoView(videoCanvas: VideoCanvas) {
        val res = rtcEngine.setupLocalVideo(videoCanvas)
        LogUtil.d("===> setLocalVideoView result: $res")
    }

    fun setRemoteVideoView(uid: String, videoCanvas: VideoCanvas) {
        val res = rtcEngine.setupRemoteVideo(uid, videoCanvas)
        LogUtil.d("===> setRemoteVideoView result: $res")
    }

    fun switchCamera() {
        val res = rtcEngine.switchCamera()
        LogUtil.d("===> switchCamera result: $res")
    }

    fun muteLocalAudio(muted: Boolean) {
        val res = rtcEngine.muteLocalAudioStream(muted)
        LogUtil.d("===> muteLocalAudio result: $res")
    }

    fun muteLocalVideo(muted: Boolean) {
        val res = rtcEngine.muteLocalVideoStream(muted)
        LogUtil.d("===> muteLocalVideo result: $res")
    }

    fun actionEnableSpeaker() {
        when (val route = audioRouteLiveData.value ?: -1) {
            AUDIO_ROUTE_SPEAKERPHONE -> rtcEngine.setEnableSpeakerphone(false)
            AUDIO_ROUTE_EARPIECE -> rtcEngine.setEnableSpeakerphone(true)
            else -> LogUtil.w("Current audio route ${route.audioRoute()}")
        }
    }

}