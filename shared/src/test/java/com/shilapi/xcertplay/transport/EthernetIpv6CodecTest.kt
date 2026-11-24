package com.shilapi.xcertplay.transport

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EthernetIpv6CodecTest {
    @Test
    fun `maps IPv6 multicast destination to Ethernet multicast`() {
        val packet = ByteArray(40)
        packet[0] = 0x60
        val destination = byteArrayOf(
            0xff.toByte(), 0x02, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 1, 0xff.toByte(), 0, 0, 2,
        )
        destination.copyInto(packet, 24)

        assertArrayEquals(
            byteArrayOf(0x33, 0x33, 0xff.toByte(), 0, 0, 2),
            EthernetIpv6Codec.multicastDestinationMac(packet),
        )
    }

    @Test
    fun `does not map IPv6 unicast destination`() {
        val packet = ByteArray(40)
        packet[0] = 0x60
        packet[24] = 0xfe.toByte()
        packet[25] = 0x80.toByte()

        assertNull(EthernetIpv6Codec.multicastDestinationMac(packet))
    }

    @Test
    fun `rejects truncated or non IPv6 packets`() {
        assertNull(EthernetIpv6Codec.multicastDestinationMac(ByteArray(39)))
        assertNull(EthernetIpv6Codec.multicastDestinationMac(ByteArray(40)))
    }

    @Test
    fun `adds target link layer option to tun neighbor advertisement`() {
        val packet = ByteArray(64)
        packet[0] = 0x60
        packet[5] = 24
        packet[6] = 58
        byteArrayOf(0xfe.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2)
            .copyInto(packet, 8)
        byteArrayOf(0xfe.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3)
            .copyInto(packet, 24)
        packet[40] = 136.toByte()
        packet[44] = 0x60
        val mac = byteArrayOf(0x02, 0, 0, 0, 0, 2)

        val result = EthernetIpv6Codec.addNeighborAdvertisementTargetMac(packet, mac)

        assertEquals(72, result.size)
        assertEquals(32, ((result[4].toInt() and 0xff) shl 8) or (result[5].toInt() and 0xff))
        assertEquals(2, result[64].toInt() and 0xff)
        assertEquals(1, result[65].toInt() and 0xff)
        assertArrayEquals(mac, result.copyOfRange(66, 72))
        assertEquals(0xffff, ipv6IcmpChecksumSum(result))
    }

    @Test
    fun `synthesizes neighbor advertisement reply for host neighbor solicitation`() {
        val hostIp = byteArrayOf(0xfe.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2)
        val peerIp = byteArrayOf(0xfe.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
        val mac = byteArrayOf(0x02, 0x00, 0x00, 0x00, 0x00, 0x02)
        val ns = ByteArray(64)
        ns[0] = 0x60
        ns[5] = 24
        ns[6] = 58
        ns[7] = 255.toByte()
        peerIp.copyInto(ns, 8)
        byteArrayOf(0xff.toByte(), 0x02, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0xff.toByte(), 0, 0, 2)
            .copyInto(ns, 24)
        ns[40] = 135.toByte()
        hostIp.copyInto(ns, 48)

        val reply = EthernetIpv6Codec.buildNeighborAdvertisementReply(ns, 0, ns.size, hostIp, mac)
        requireNotNull(reply)
        assertEquals(72, reply.size)
        assertEquals(32, ((reply[4].toInt() and 0xff) shl 8) or (reply[5].toInt() and 0xff))
        assertEquals(58, reply[6].toInt() and 0xff)
        assertEquals(255, reply[7].toInt() and 0xff)
        assertArrayEquals(hostIp, reply.copyOfRange(8, 24))
        assertArrayEquals(peerIp, reply.copyOfRange(24, 40))
        assertEquals(136, reply[40].toInt() and 0xff)
        assertEquals(0x60, reply[44].toInt() and 0xff)
        assertArrayEquals(hostIp, reply.copyOfRange(48, 64))
        assertEquals(2, reply[64].toInt() and 0xff)
        assertEquals(1, reply[65].toInt() and 0xff)
        assertArrayEquals(mac, reply.copyOfRange(66, 72))
        assertEquals(0xffff, ipv6IcmpChecksumSum(reply))

        val otherHostIp = byteArrayOf(0xfe.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 9)
        assertNull(EthernetIpv6Codec.buildNeighborAdvertisementReply(ns, 0, ns.size, otherHostIp, mac))
    }

    private fun ipv6IcmpChecksumSum(packet: ByteArray): Int {
        val payloadBytes = ((packet[4].toInt() and 0xff) shl 8) or (packet[5].toInt() and 0xff)
        var sum = 0L
        fun words(start: Int, bytes: Int) {
            var offset = start
            val end = start + bytes
            while (offset + 1 < end) {
                sum += (((packet[offset].toInt() and 0xff) shl 8) or
                    (packet[offset + 1].toInt() and 0xff)).toLong()
                offset += 2
            }
        }
        words(8, 32)
        sum += payloadBytes
        sum += 58
        words(40, payloadBytes)
        while (sum ushr 16 != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.toInt()
    }
}
