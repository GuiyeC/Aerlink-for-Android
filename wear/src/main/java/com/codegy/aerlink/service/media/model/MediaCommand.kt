package com.codegy.aerlink.service.media.model

import com.codegy.aerlink.connection.Command
import com.codegy.aerlink.service.media.AMSContract

enum class MediaCommand(val value: Byte) {
    Play(0),
    Pause(1),
    TogglePlayPause(2),
    NextTrack(3),
    PreviousTrack(4),
    VolumeUp(5),
    VolumeDown(6),
    AdvanceRepeatMode(7),
    AdvanceShuffleMode(8),
    SkipForward(9),
    SkipBackward(10),
    LikeTrack(11),
    DislikeTrack(12),
    BookmarkTrack(13),
    Reserved(14);

    val command: Command = Command(
            AMSContract.serviceUuid,
            AMSContract.remoteCommandCharacteristicUuid,
            byteArrayOf(value),
            Command.IMPORTANCE_MIN
    )

    companion object {
        fun fromRaw(commandId: Int): MediaCommand {
            if (commandId >= MediaCommand.values().size) {
                return MediaCommand.Reserved
            }

            return MediaCommand.values()[commandId]
        }
    }
}