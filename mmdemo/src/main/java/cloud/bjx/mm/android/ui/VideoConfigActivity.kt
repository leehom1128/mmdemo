package cloud.bjx.mm.android.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import cloud.bjx.mm.android.bean.VideoConfigBean
import cloud.bjx.mm.android.databinding.ActivityVideoConfigBinding
import cloud.bjx.mm.android.utils.LogUtil
import cloud.bjx.mm.android.utils.ToastUtil

class VideoConfigActivity : AppCompatActivity() {

    private val mVideoFpsList by lazy {
        listOf(
            "1fps", "7fps", "10fps", "15fps", "20fps", "24fps", "30fps"
        )
    }

    private val mVideoResolutionList by lazy {
        listOf(
            "180x180", "180x240", "240x240", "240x320", "320x320", "360x480", "480x480",
            "360x640", "480x640", "540x960", "720x1280", "1080x1920"
        )
    }

    private var mSelectFpsIndex = 3 // 15fps
    private var mSelectDimenIndex = 5 // 360x480

    private lateinit var mBinding: ActivityVideoConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "视频配置"

        mBinding = ActivityVideoConfigBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        mBinding.chipGroupFps.setChipItems(mVideoFpsList, mSelectFpsIndex)
        mBinding.chipGroupResolution.setChipItems(mVideoResolutionList, mSelectDimenIndex)

        mBinding.chipGroupFps.setListener { mSelectFpsIndex = it }
        mBinding.chipGroupResolution.setListener { mSelectDimenIndex = it }
        mBinding.btnOk.setOnClickListener { actionJoinChannel() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun actionJoinChannel() {
        val channelId = mBinding.editChannelId.text.toString()
        LogUtil.d("channelId: $channelId")
        if (channelId.isEmpty()) {
            ToastUtil.showShortToast("ChannelId is empty")
            return
        }

        val useSpeaker = mBinding.switchUseSpeaker.isChecked

        val frameRate = when (mSelectFpsIndex) {
            0 -> 1
            1 -> 7
            2 -> 10
            3 -> 15
            4 -> 20
            5 -> 24
            6 -> 30
            else -> throw IllegalArgumentException("invalid fps index: $mSelectFpsIndex")
        }
        // video width: pair.first; video height: pair.second
        val pair = when (mSelectDimenIndex) {
            0 -> Pair(180, 180)
            1 -> Pair(180, 240)
            2 -> Pair(240, 240)
            3 -> Pair(240, 320)
            4 -> Pair(320, 320)
            5 -> Pair(360, 480)
            6 -> Pair(480, 480)
            7 -> Pair(360, 640)
            8 -> Pair(480, 640)
            9 -> Pair(540, 960)
            10 -> Pair(720, 1280)
            11 -> Pair(1080, 1920)
            else -> throw IllegalArgumentException("invalid dimen index: $mSelectDimenIndex")
        }

        val bean = VideoConfigBean(channelId, frameRate, pair.first, pair.second, useSpeaker)
        LiveVideoActivity.start(this, bean)
    }

}