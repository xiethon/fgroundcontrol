package com.fairland.fgroundcontrol.connection.protocol

import com.MAVLink.MAVLinkPacket

/** 接收带签名帧但不认证；CRC覆盖原始签名标志，签名尾部不作为消息负载。 */
object MavlinkDecoder {
    fun decode(bytes: ByteArray): MAVLinkPacket? {
        if (bytes.size < 8) return null
        fun u(i: Int) = bytes[i].toInt() and 255
        val v2 = u(0) == 253
        if (!v2 && u(0) != 254) return null
        val header = if (v2) 10 else 6
        if (bytes.size < header + 2) return null
        val length = u(1)
        if (v2 && u(2) and 254 != 0) return null
        val signed = v2 && u(2) and 1 != 0
        if (bytes.size != header + length + 2 + if (signed) 13 else 0) return null
        val packet = MAVLinkPacket(length,v2)
        if (v2) {
            packet.incompatFlags=u(2);packet.compatFlags=u(3);packet.seq=u(4);packet.sysid=u(5);packet.compid=u(6)
            packet.msgid=u(7) or (u(8) shl 8) or (u(9) shl 16)
        } else {
            packet.seq=u(2);packet.sysid=u(3);packet.compid=u(4);packet.msgid=u(5)
        }
        for (i in header until header+length) packet.payload.putByte(bytes[i])
        if (!packet.generateCRC(length)) return null
        if (packet.crc.getLSB()!=u(header+length) || packet.crc.getMSB()!=u(header+length+1)) return null
        return packet
    }
}
