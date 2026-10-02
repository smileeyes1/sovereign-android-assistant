package ps.hakim.phoneagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VPN مقسّم للمحلل DNS فقط.
 *
 * يوجّه DNS النظامي إلى عنوان افتراضي داخل TUN، ثم يمرر حمولة DNS فقط إلى
 * CleanBrowsing Family Filter. بقية حركة الإنترنت لا تدخل النفق.
 *
 * هذه الطبقة لا تدّعي منع DoH/VPN المخصص؛ ذلك يبقى بوابة تحقق مستقلة.
 */
class HakimFamilyDnsVpnService : VpnService() {
    companion object {
        const val ACTION_START = "ps.hakim.stable.FAMILY_DNS_VPN_START"
        const val ACTION_STOP = "ps.hakim.stable.FAMILY_DNS_VPN_STOP"

        private const val VPN_ADDRESS = "10.236.0.1"
        private const val VPN_DNS = "10.236.0.2"
        private const val FAMILY_DNS_1 = "185.228.168.168"
        private const val FAMILY_DNS_2 = "185.228.169.168"
        private const val FAMILY_DNS_1_V6 = "2a0d:2a00:1::"
        private const val FAMILY_DNS_2_V6 = "2a0d:2a00:2::"
        private const val DNS_PORT = 53
        private const val NOTIFICATION_ID = 17326
        private const val CHANNEL_ID = "hakim_family_dns_vpn"
        private const val MAX_DNS_PAYLOAD = 4096
    }

    private val running = AtomicBoolean(false)
    @Volatile private var tun: ParcelFileDescriptor? = null
    @Volatile private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel("STOPPED")
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        if (running.compareAndSet(false, true)) {
            startTunnel()
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        HakimDeviceProtection.markActive(this, false, "CONSENT_REVOKED")
        stopTunnel("CONSENT_REVOKED")
        stopSelf()
        super.onRevoke()
    }

    private fun startTunnel() {
        val established = runCatching {
            Builder()
                .setSession("حكيم — حماية DNS العائلية")
                .setMtu(1500)
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer(VPN_DNS)
                .addRoute(VPN_DNS, 32)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setMetered(false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setBlocking(true)
                }
                .establish()
        }.getOrNull()

        if (established == null) {
            running.set(false)
            HakimDeviceProtection.failOpen(this, "establish_failed")
            stopSelf()
            return
        }

        tun = established
        HakimDeviceProtection.markActive(this, true, "ACTIVE_STARTING")

        worker = Thread({
            val input = FileInputStream(established.fileDescriptor)
            val output = FileOutputStream(established.fileDescriptor)
            val buffer = ByteArray(8192)
            try {
                while (running.get()) {
                    val n = input.read(buffer)
                    if (n <= 0) continue
                    val response = handleIpv4UdpDns(buffer, n) ?: continue
                    output.write(response)
                    output.flush()
                }
            } catch (e: Exception) {
                if (running.get()) {
                    HakimDeviceProtection.noteFailure(this, "tun_" + e.javaClass.simpleName)
                }
            } finally {
                runCatching { input.close() }
                .onFailure { }
                .also { runCatching { output.close() } }
                .also { runCatching { established.close() } }
                .also { tun = null }
                .also { running.set(false) }
                .also {
                    if (!HakimDeviceProtection.failOpenActive(this)) {
                        HakimDeviceProtection.markActive(this, false, "STOPPED")
                    }
                }
            }
        }, "hakim-family-dns-vpn").apply {
            isDaemon = true
            start()
        }

        // يولّد استعلام DNS نظاميًا بعد إنشاء النفق ليتحقق ميدانيًا من مسار TUN نفسه.
        Thread({
            runCatching {
                Thread.sleep(450L)
                // اسم فريد لتجنب نجاح كاذب من DNS cache؛ حتى NXDOMAIN يولّد رد DNS حقيقيًا.
                InetAddress.getByName("hakim-" + System.currentTimeMillis() + ".cleanbrowsing.org")
            }.onFailure {
                failOpenAndStop("selfcheck_" + it.javaClass.simpleName)
            }
        }, "hakim-family-dns-selfcheck").apply {
            isDaemon = true
            start()
        }
    }

    private fun handleIpv4UdpDns(packet: ByteArray, length: Int): ByteArray? {
        if (length < 28) return null
        val version = (packet[0].toInt() ushr 4) and 0x0f
        if (version != 4) return null
        val ihl = (packet[0].toInt() and 0x0f) * 4
        if (ihl < 20 || length < ihl + 8) return null
        val protocol = packet[9].toInt() and 0xff
        if (protocol != 17) return null

        val udpOffset = ihl
        val srcPort = u16(packet, udpOffset)
        val dstPort = u16(packet, udpOffset + 2)
        if (dstPort != DNS_PORT) return null
        val udpLength = u16(packet, udpOffset + 4)
        if (udpLength < 8 || udpOffset + udpLength > length) return null
        val dnsLength = udpLength - 8
        if (dnsLength !in 12..MAX_DNS_PAYLOAD) return null

        val query = packet.copyOfRange(udpOffset + 8, udpOffset + 8 + dnsLength)
        val answer = resolveFamilyDns(query) ?: return null
        return buildIpv4UdpResponse(packet, ihl, srcPort, answer)
    }

    private fun resolveFamilyDns(query: ByteArray): ByteArray? {
        for (server in arrayOf(FAMILY_DNS_1, FAMILY_DNS_2, FAMILY_DNS_1_V6, FAMILY_DNS_2_V6)) {
            val response = runCatching {
                DatagramSocket().use { socket ->
                    if (!protect(socket)) throw IllegalStateException("protect_failed")
                    socket.soTimeout = 2500
                    val addr = InetAddress.getByName(server)
                    socket.send(DatagramPacket(query, query.size, addr, DNS_PORT))
                    val buf = ByteArray(MAX_DNS_PAYLOAD)
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    buf.copyOf(packet.length)
                }
            }.getOrNull()
            if (response != null && response.size >= 12) {
                HakimDeviceProtection.noteUpstreamSuccess(this)
                return response
            }
        }
        failOpenAndStop("upstream_timeout")
        return null
    }

    private fun buildIpv4UdpResponse(
        request: ByteArray,
        requestIhl: Int,
        clientPort: Int,
        dns: ByteArray
    ): ByteArray {
        val ipLen = 20
        val udpLen = 8 + dns.size
        val total = ipLen + udpLen
        val out = ByteArray(total)

        out[0] = 0x45
        out[1] = 0
        put16(out, 2, total)
        out[4] = request[4]
        out[5] = request[5]
        out[6] = 0
        out[7] = 0
        out[8] = 64
        out[9] = 17
        out[10] = 0
        out[11] = 0

        // المصدر هو DNS الافتراضي الذي استلمه العميل، والوجهة هي عنوان العميل داخل TUN.
        for (i in 0 until 4) {
            out[12 + i] = request[16 + i]
            out[16 + i] = request[12 + i]
        }

        val checksum = ipv4HeaderChecksum(out, 0, ipLen)
        put16(out, 10, checksum)

        put16(out, ipLen, DNS_PORT)
        put16(out, ipLen + 2, clientPort)
        put16(out, ipLen + 4, udpLen)
        // UDP checksum = 0 صالح في IPv4.
        out[ipLen + 6] = 0
        out[ipLen + 7] = 0
        System.arraycopy(dns, 0, out, ipLen + 8, dns.size)
        return out
    }

    private fun ipv4HeaderChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((data[i].toInt() and 0xff) shl 8) or (data[i + 1].toInt() and 0xff)
            while (sum > 0xffff) sum = (sum and 0xffff) + (sum ushr 16)
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xff) shl 8
            while (sum > 0xffff) sum = (sum and 0xffff) + (sum ushr 16)
        }
        return sum.inv().toInt() and 0xffff
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)

    private fun put16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value ushr 8) and 0xff).toByte()
        data[offset + 1] = (value and 0xff).toByte()
    }

    private fun failOpenAndStop(reason: String) {
        if (!running.getAndSet(false)) return
        HakimDeviceProtection.failOpen(this, reason)
        runCatching { tun?.close() }
        tun = null
        worker?.interrupt()
        worker = null
        stopSelf()
    }

    private fun stopTunnel(state: String) {
        running.set(false)
        runCatching { tun?.close() }
        tun = null
        worker?.interrupt()
        worker = null
        if (!HakimDeviceProtection.failOpenActive(this)) {
            HakimDeviceProtection.markActive(this, false, state)
        }
    }

    override fun onDestroy() {
        stopTunnel("STOPPED")
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "حماية DNS العائلية",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, CommandCenterActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setContentTitle("حكيم — حماية DNS العائلية")
            .setContentText("حماية DNS الجهاز تعمل على Wi-Fi وبيانات الهاتف.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }
}
