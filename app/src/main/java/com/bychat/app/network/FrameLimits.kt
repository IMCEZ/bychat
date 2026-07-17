package com.bychat.app.network

data class FrameLimits(val maxFrameBytes: Int = MAX_FRAME_BYTES) {
    init {
        require(maxFrameBytes > 0) { "最大帧长度必须大于0" }
    }

    companion object {
        const val MAX_FRAME_BYTES = 1_048_576
    }
}
