package world.phantasmal.web.assetsGeneration.psov2

import world.phantasmal.psolib.Endianness
import world.phantasmal.psolib.buffer.Buffer
import world.phantasmal.psolib.compression.prs.prsDecompress
import world.phantasmal.psolib.cursor.cursor

/**
 * Reads a psov2 GSL archive: a flat, uncompressed container used to bundle e.g. an enemy's BML
 * file together with its texture pack. Format (ported from psov2's NinjaFile.js `api_gsl`):
 * a sequence of 0x30-byte records (0x20-byte name, u32 sector offset, u32 length, 8 bytes
 * padding) terminated by a record with an empty name. Entry offsets are in 2048-byte sectors.
 */
fun readGsl(bytes: ByteArray): Map<String, ByteArray> {
    val cursor = Buffer.fromByteArray(bytes, Endianness.Little).cursor()
    val entries = mutableListOf<Triple<String, Int, Int>>()

    while (true) {
        val name = cursor.stringAscii(0x20)
        val offset = cursor.uInt().toInt() * 2048
        val length = cursor.uInt().toInt()
        cursor.seek(8)

        if (name.isEmpty()) break

        entries.add(Triple(name, offset, length))
    }

    val result = mutableMapOf<String, ByteArray>()

    for ((name, offset, length) in entries) {
        cursor.seekStart(offset)
        result[name] = cursor.buffer(length).byteArray
    }

    return result
}

/**
 * Reads a psov2 BML archive: SEGA's real archive format for e.g. player and enemy models,
 * PRS-compressed per entry. Format (ported from psov2's NinjaFile.js `api_bml`):
 *
 * - u32 at offset 4: entry count.
 * - Entry table starting at 0x40, 0x40 bytes per entry: 0x20-byte name, u32 compressed length,
 *   u32 type, u32 unpacked length, u32 embedded-texture-pack (pvm) length, 16 bytes padding.
 * - Entry data starts at the first 0x800-aligned offset after the table. Each entry's compressed
 *   bytes are PRS-compressed; entries are back-to-back, each followed by zero padding up to the
 *   next non-zero byte (mirrors the reference implementation's `seekNext`).
 * - If an entry's pvm length is non-zero, a second PRS-compressed blob directly follows the main
 *   entry's data (also zero-padded the same way), stored here under the entry's name with its
 *   extension replaced by ".pvm".
 */
fun readBml(bytes: ByteArray): Map<String, ByteArray> {
    val cursor = Buffer.fromByteArray(bytes, Endianness.Little).cursor()
    cursor.seekStart(4)
    val count = cursor.uInt().toInt()

    cursor.seekStart(0x40)

    data class Entry(val name: String, val len: Int, val pvm: Int)

    val entries = (0 until count).map {
        val name = cursor.stringAscii(0x20)
        val len = cursor.uInt().toInt()
        cursor.seek(4) // type
        cursor.seek(4) // unpacked length
        val pvm = cursor.uInt().toInt()
        cursor.seek(0x10)
        Entry(name, len, pvm)
    }

    var pos = cursor.position
    pos = if (pos < 0x800) 0x800 else (pos and 0xFFFFF800.toInt()) + 0x800
    cursor.seekStart(pos)

    fun seekNext() {
        while (cursor.position < cursor.size) {
            val peekPos = cursor.position
            if (cursor.uByte().toInt() != 0) {
                cursor.seekStart(peekPos)
                return
            }
        }
    }

    val result = mutableMapOf<String, ByteArray>()

    for (entry in entries) {
        val raw = cursor.buffer(entry.len)
        result[entry.name] = prsDecompress(raw.cursor()).unwrap().buffer().byteArray
        seekNext()

        if (entry.pvm != 0) {
            val pvmRaw = cursor.buffer(entry.pvm)
            val pvmName = entry.name.substringBeforeLast('.') + ".pvm"
            result[pvmName] = prsDecompress(pvmRaw.cursor()).unwrap().buffer().byteArray
            seekNext()
        }
    }

    return result
}
