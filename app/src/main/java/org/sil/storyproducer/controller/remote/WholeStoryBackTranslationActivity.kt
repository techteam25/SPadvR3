package org.sil.storyproducer.controller.remote

import android.media.MediaPlayer
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import org.sil.storyproducer.R
import org.sil.storyproducer.controller.phase.PhaseBaseActivity
import org.sil.storyproducer.model.SLIDE_NUM
import org.sil.storyproducer.model.SlideType
import org.sil.storyproducer.model.Story
import org.sil.storyproducer.model.Workspace
import org.sil.storyproducer.model.logging.saveLearnLog
import org.sil.storyproducer.tools.file.getChosenFilename
import org.sil.storyproducer.tools.file.getStoryUri
import org.sil.storyproducer.tools.file.storyRelPathExists
import org.sil.storyproducer.tools.media.AudioPlayer
import org.sil.storyproducer.tools.media.MediaHelper
import org.sil.storyproducer.tools.toolbar.PlayBackRecordingToolbar
import java.util.*
import kotlin.math.min

/**
 * Created by annmcostantino on 1/14/2018.
 */

class WholeStoryBackTranslationActivity : PhaseBaseActivity(), PlayBackRecordingToolbar.ToolbarMediaListener {
    private var learnImageView: ImageView? = null
    private var playButton: ImageButton? = null
    private var videoSeekBar: SeekBar? = null
    private var mSeekBarTimer = Timer()

    private var narrationPlayer: AudioPlayer = AudioPlayer()

    private var isVolumeOn = true
    private var isWatchedOnce = false

    private var recordingToolbar: PlayBackRecordingToolbar = PlayBackRecordingToolbar()

    private var numOfSlides: Int = 0
    private var seekbarStartTime: Long = -1
    private var logStartTime: Long = -1
    private var curPos: Int = -1 //set to -1 so that the first slide will register as "different"
    private val slideDurations: MutableList<Int> = java.util.ArrayList()
    private val slideStartTimes: MutableList<Int> = java.util.ArrayList()

    private var isLogging = false
    private var startPos = -1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learn)

        setToolbar()

        learnImageView = findViewById(R.id.fragment_image_view)
        playButton = findViewById(R.id.fragment_reference_audio_button)

        //setup seek bar listenters
        videoSeekBar = findViewById(R.id.videoSeekBar)
        videoSeekBar!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(sBar: SeekBar) {}
            override fun onStartTrackingTouch(sBar: SeekBar) {}
            override fun onProgressChanged(sBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    if (recordingToolbar.isRecording || recordingToolbar.isAudioPlaying) {
                        //When recording, update the picture to the accurate location, preserving
                        seekbarStartTime = System.currentTimeMillis() - videoSeekBar!!.progress
                        setSlideFromSeekbar()
                    } else {
                        if (narrationPlayer.isAudioPlaying) {
                            pauseStoryAudio()
                            playStoryAudio()
                        } else {
                            setSlideFromSeekbar()
                        }
                        //always start at the beginning of the slide.
                        if (slideStartTimes.size > curPos)
                            videoSeekBar!!.progress = slideStartTimes[curPos]
                    }
                }
            }
        })

        //setup volume switch callbacks
        val volumeSwitch = findViewById<Switch>(R.id.volumeSwitch)
        //set the volume switch change listener
        volumeSwitch.isChecked = true
        volumeSwitch.setOnCheckedChangeListener { _, isChecked ->
            isVolumeOn = if (isChecked) {
                narrationPlayer.setVolume(1.0f)
                true
            } else {
                narrationPlayer.setVolume(0.0f)
                false
            }
        }

        //has learn already been watched?
        isWatchedOnce = storyRelPathExists(this, Workspace.activeStory.wholeStoryBackTAudioFile)

        //get story audio duration
        numOfSlides = 0
        slideStartTimes.add(0)
        for (s in story.slides) {
            //don't play the copyright slides.
            if (s.slideType in arrayOf(SlideType.FRONTCOVER, SlideType.NUMBEREDPAGE)) {
                numOfSlides++
                slideDurations.add((MediaHelper.getAudioDuration(this,
                    getStoryUri(Story.getFilename(s.narrationFile))!!) / 1000).toInt())
                slideStartTimes.add(slideStartTimes.last() + slideDurations.last())
            } else {
                break
            }
        }
        videoSeekBar?.max = slideStartTimes.last()

        invalidateOptionsMenu()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val item = menu.getItem(0)
        item.setIcon(R.drawable.ic_school_white_48dp)
        return true
    }

    public override fun onResume() {
        super.onResume()

        narrationPlayer = AudioPlayer()
        narrationPlayer.onPlayBackStop(MediaPlayer.OnCompletionListener {
            if(narrationPlayer.isAudioPrepared){
                if(curPos >= numOfSlides-1){ //is it the last slide?
                    //at the end of video so special case
                    pauseStoryAudio()
                } else {
                    //just play the next slide!
                    videoSeekBar?.progress = slideStartTimes[curPos+1]
                    playStoryAudio()
                }
            }
        })

        mSeekBarTimer = Timer()
        mSeekBarTimer.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread{
                    if(recordingToolbar.isRecording || recordingToolbar.isAudioPlaying){
                        videoSeekBar?.progress = min((System.currentTimeMillis() - seekbarStartTime).toInt(),videoSeekBar!!.max)
                        setSlideFromSeekbar()
                    }else{
                        if(curPos >= 0) videoSeekBar?.progress = slideStartTimes[curPos] + narrationPlayer.currentPosition
                    }
                }
            }
        },0,33)

        setSlideFromSeekbar()
    }

    private fun setSlideFromSeekbar() {
        val time = videoSeekBar!!.progress
        var i = 0
        for (d in slideStartTimes) {
            if (time < d) {
                if(i-1 != curPos){
                    curPos = i-1
                    setPic(learnImageView!!, curPos)
                    var path = Workspace.activeStory.slides[curPos].chosenTranslateReviseFile
                    val split: Array<String> = path.split("|").toTypedArray()
                    val filename = split[1]
                    narrationPlayer.setStorySource(this, filename)
                }
                break
            }
            i++
        }
    }
    /**
     * Plays the audio
     */
    internal fun playStoryAudio() {
        recordingToolbar.stopToolbarMedia()
        setSlideFromSeekbar()
        narrationPlayer.pauseAudio()
        markLogStart()
        seekbarStartTime = System.currentTimeMillis()
        narrationPlayer.setVolume(if (isVolumeOn) 1.0f else 0.0f) //set the volume on or off based on the boolean
        narrationPlayer.playAudio()
        playButton!!.setImageResource(R.drawable.ic_pause_white_48dp)
    }

    /**
     * helper function for pausing the video
     */
    private fun pauseStoryAudio() {
        makeLogIfNecessary()
        narrationPlayer.pauseAudio()
        playButton!!.setImageResource(R.drawable.ic_play_arrow_white_48dp)
    }

    private fun markLogStart() {
        if(!isLogging) {
            startPos = curPos
            logStartTime = System.currentTimeMillis()
        }
        isLogging = true
    }

    private fun makeLogIfNecessary(isRecording: Boolean = false) {
        if (isLogging) {
            if (startPos != -1) {
                val duration: Long = System.currentTimeMillis() - logStartTime
                if(duration > 2000){ //you need 2 seconds to listen to anything
                    saveLearnLog(this, startPos,curPos, duration, isRecording)
                }
                startPos = -1
            }
        }
        isLogging = false
    }

    private fun setToolbar(){
        val bundle = Bundle()
        bundle.putInt(SLIDE_NUM, 0)
        recordingToolbar.arguments = bundle
        supportFragmentManager.beginTransaction().replace(R.id.toolbar_for_recording_toolbar, recordingToolbar).commit()

        recordingToolbar.keepToolbarVisible()
    }

    /**
     * Button action for playing/pausing the audio
     * @param view button to set listeners for
     */
    fun onClickPlayPauseButton(@Suppress("UNUSED_PARAMETER") view: View) {
        if (narrationPlayer.isAudioPlaying) {
            pauseStoryAudio()
        } else {
            if (videoSeekBar!!.progress >= videoSeekBar!!.max-100) {
                //reset the video to the beginning because they already finished it (within 100 ms)
                videoSeekBar!!.progress = 0
            }
            playStoryAudio()
        }
    }
}