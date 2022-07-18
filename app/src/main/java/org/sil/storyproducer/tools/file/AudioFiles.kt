package org.sil.storyproducer.tools.file


import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.sil.storyproducer.R
import org.sil.storyproducer.model.*
import org.sil.storyproducer.model.PROJECT_DIR
import org.sil.storyproducer.model.PhaseType
import org.sil.storyproducer.model.Recording
import org.sil.storyproducer.model.Workspace
import java.util.*
import kotlin.math.max

/**
 * AudioFiles represents an abstraction of the audio resources for story templates and project files.
 */

internal const val AUDIO_EXT = ".m4a"

/**
 * Creates a relative path for recorded audio based upon the phase, slide number and timestamp.
 * Records the path in the story object.
 * If there is a failure in generating the path, an empty string is returned.
 * @return the path generated, or an empty string if there is a failure.
 */

//Going to need these next functions for compatibility reasons. There will likely be problems because of the change from String to the Recording class
fun getChosenFilename(slideNum: Int = Workspace.activeSlideNum): String {
    return Story.getFilename(getChosenCombName(slideNum))
}

fun getChosenCombName(slideNum: Int = Workspace.activeSlideNum): String {
    return when (Workspace.activePhase.phaseType) {
        PhaseType.LEARN -> Workspace.activeStory.learnAudioFile!!.fileName
        PhaseType.TRANSLATE_REVISE -> Workspace.activeStory.slides[slideNum].chosenTranslateReviseFile
        PhaseType.WORD_LINKS -> Workspace.activeWordLink.chosenWordLinkFile
        PhaseType.VOICE_STUDIO -> Workspace.activeStory.slides[slideNum].chosenVoiceStudioFile
        PhaseType.BACK_T -> Workspace.activeStory.slides[slideNum].chosenBackTranslationFile
        PhaseType.WHOLE_STORY -> Workspace.activeStory.wholeStoryBackTAudioFile!!.fileName
        else -> ""
    }
}

fun getChosenDisplayName(slideNum: Int = Workspace.activeSlideNum): String {
    return Story.getDisplayName(getChosenCombName(slideNum))
}

fun getRecordedDisplayNames(slideNum:Int = Workspace.activeSlideNum) : MutableList<String>? {
    val filenames : MutableList<String> = arrayListOf()
    val combNames = Workspace.activePhase.getCombNames(slideNum) ?: return filenames
    for (n in combNames){filenames.add(Story.getDisplayName(n))}
    return filenames
}

fun setChosenFileIndex(index: Int, slideNum: Int = Workspace.activeSlideNum){
    val nameSize = Workspace.activePhase.getCombNames(slideNum)?.size ?: -1
    val combName = if(index < 0 || index >= nameSize) "" else Workspace.activePhase.getCombNames(slideNum)!![index]

    when(Workspace.activePhase.phaseType){
        PhaseType.TRANSLATE_REVISE -> Workspace.activeStory.slides[slideNum].chosenTranslateReviseFile = combName
        PhaseType.WORD_LINKS -> Workspace.activeWordLink.chosenWordLinkFile = combName
        PhaseType.VOICE_STUDIO -> Workspace.activeStory.slides[slideNum].chosenVoiceStudioFile = combName
        PhaseType.BACK_T -> Workspace.activeStory.slides[slideNum].chosenBackTranslationFile = combName
        else -> return
    }
    return
}

fun getRecordedAudioFiles(slideNum:Int = Workspace.activeSlideNum) : MutableList<String> {
    val filenames : MutableList<String> = arrayListOf()
    val combNames = Workspace.activePhase.getCombNames(slideNum) ?: return filenames
    for (n in combNames){filenames.add(Story.getFilename(n))}
    return filenames
}

fun deleteWLAudioFileFromList(context: Context, pos: Int) {
    val recordings = Workspace.activeWordLink.wordLinkRecordings
    val fileLocation = Story.getFilename(recordings[pos].audioRecordingFilename)
    val filename = recordings[pos].audioRecordingFilename

    recordings.removeAt(pos)
    if (getChosenCombName() == filename) {
        // current chosen WL has been deleted, shift the file index
        if (recordings.size == 0) {
            setChosenFileIndex(-1)
        }else {
            setChosenFileIndex(0)
        }
    }
    // delete the WL recording file
    deleteStoryFile(context, fileLocation)
}
//End of compatibility functions


fun getChosenRecording(phaseType: PhaseType, slideNumber: Int): Recording? {
    return when (phaseType) {
        PhaseType.LEARN -> Workspace.activeStory.learnAudioFile
        PhaseType.TRANSLATE_REVISE -> Workspace.activeStory.slides[slideNumber].draftRecordings.selectedFile
        PhaseType.VOICE_STUDIO -> Workspace.activeStory.slides[slideNumber].dramatizationRecordings.selectedFile
        PhaseType.BACK_T -> Workspace.activeStory.slides[slideNumber].backTranslationRecordings.selectedFile
        PhaseType.COMMUNITY_WORK -> Workspace.activeStory.slides[slideNumber].backTranslationRecordings.selectedFile
        PhaseType.WHOLE_STORY -> Workspace.activeStory.wholeStoryBackTAudioFile
        else -> throw Exception("Unsupported stage to get the audio file for")
    }
}

fun assignNewAudioRelPath(): String {
    val recording = createRecording()
    addRecording(recording)
    return recording.fileName
}

fun updateDisplayName(position: Int, newName: String) {
    //make sure to update the actual list, not a copy.
    val recordings = Workspace.activeStory.lastPhaseType.getRecordings().getFiles()
    recordings[position].displayName = newName
}

fun deleteAudioFileFromList(context: Context, pos: Int) {
    //make sure to update the actual list, not a copy.
    val filenames = Workspace.activeStory.lastPhaseType.getRecordings()
    val filename = filenames.getFiles()[pos].fileName
    filenames.removeAt(pos)
    deleteStoryFile(context, filename)
}

fun createRecording(): Recording {
    //Example: project/communityCheck_3_2018-03-17T11:14;31.542.md4
    //This is the file name generator for all audio files for the app.

    //the extension is added in the "when" statement because wav files are easier to concatenate, so
    //they are used for the stages that do that.
    return when (Workspace.activeStory.lastPhaseType) {
        //just one file.  Overwrite when you re-record.
        PhaseType.LEARN, PhaseType.WHOLE_STORY -> Recording(
            "$PROJECT_DIR/${Workspace.activeStory.lastPhaseType.getShortName()}$AUDIO_EXT",
            Workspace.activeStory.lastPhaseType.getDisplayName())
        //Make new files every time.  Don't append.
        PhaseType.TRANSLATE_REVISE, PhaseType.COMMUNITY_WORK,
        PhaseType.VOICE_STUDIO, PhaseType.REMOTE_CHECK,
        PhaseType.BACK_T -> {
            //find the next number that is available for saving files at.
            val names = Workspace.activeStory.lastPhaseType.getRecordings().getFiles().map { it.displayName }
            val rNameNum = "${Workspace.activeStory.lastPhaseType.getDisplayName()} ([0-9]+)".toRegex()
            var maxNum = 0
            for (n in names) {
                try {
                    val num = rNameNum.find(n)
                    if (num != null)
                        maxNum = max(maxNum, num.groupValues[1].toInt())
                } catch (e: Exception) {
                    //If there is a crash (such as a bad int parse) just keep going.
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
            val displayName = "${Workspace.activeStory.lastPhaseType.getDisplayName()} ${maxNum + 1}"
            val fileName = "${Workspace.activeDir}/${Workspace.activeFilenameRoot}_${Date().time}$AUDIO_EXT"
            Recording(fileName, displayName)
        }
        else -> throw Exception("Unsupported phase to create recordings for")
    }
}

fun addRecording(recording: Recording) {
    //register it in the story data structure.
    when (Workspace.activeStory.lastPhaseType) {
        PhaseType.LEARN -> Workspace.activeStory.learnAudioFile = recording
        PhaseType.WHOLE_STORY -> Workspace.activeStory.wholeStoryBackTAudioFile = recording
        PhaseType.COMMUNITY_WORK -> Workspace.activeSlide!!.communityCheckRecordings.add(recording)
        PhaseType.REMOTE_CHECK -> Workspace.activeSlide!!.consultantCheckRecordings.add(recording)
        PhaseType.TRANSLATE_REVISE -> Workspace.activeSlide!!.draftRecordings.add(recording)
        PhaseType.VOICE_STUDIO -> Workspace.activeSlide!!.dramatizationRecordings.add(recording)
        PhaseType.BACK_T -> Workspace.activeSlide!!.backTranslationRecordings.add(recording)
        else -> throw Exception("Unsupported phase to add an audio file to")
    }
}

fun getTempAppendAudioRelPath(): String {
    return "$PROJECT_DIR/temp$AUDIO_EXT"
}

