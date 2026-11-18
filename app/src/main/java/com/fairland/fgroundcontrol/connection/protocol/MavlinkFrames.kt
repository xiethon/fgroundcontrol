package com.fairland.fgroundcontrol.connection.protocol

/** 仅分帧；CRC与消息解码仍交给XML生成库。每个UDP来源必须持有独立实例。 */
class MavlinkFrames(private val signedFrame: (ByteArray) -> Unit, private val unsignedFrame: (ByteArray) -> Unit) {
    private val frame = ByteArray(280)
    private var count = 0
    private var expected = 0
    private var signed = false

    fun reset() { count = 0; expected = 0; signed = false }

    fun accept(bytes: ByteArray, length: Int = bytes.size) {
        require(length in 0..bytes.size)
        for (index in 0 until length) {
            val value = bytes[index].toInt() and 255
            if (count == 0 && value != 0xfe && value != 0xfd) continue
            frame[count++] = bytes[index]
            val v2 = frame[0].toInt() and 255 == 0xfd
            if (count == 2) expected = value + if (v2) 12 else 8
            if (v2 && count == 3) {
                signed = value and 1 != 0
                if (signed) expected += 13
            }
            if (expected != 0 && count == expected) {
                val packet = frame.copyOf(count)
                val unsupportedFlags = v2 && (frame[2].toInt() and 254 != 0)
                val hasSignature = signed
                reset()
                // 消耗完整签名尾部后再继续，避免其中伪造的STX被当作未签名包。
                if (hasSignature) signedFrame(packet)
                else if (!unsupportedFlags) unsignedFrame(packet)
            }
        }
    }
}
