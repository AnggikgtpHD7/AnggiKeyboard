package com.anggik.keyboard.engine

import java.util.Locale

class LocalWordPredictor {

    private val enLexicon = arrayOf(
        "about", "after", "again", "all", "also", "always", "and", "android", "any", "are",
        "back", "because", "been", "before", "being", "best", "better", "between", "both", "build",
        "can", "change", "check", "code", "come", "could", "create", "data", "device", "different",
        "does", "done", "down", "during", "each", "enable", "error", "even", "every", "file",
        "find", "first", "for", "from", "get", "give", "good", "great", "have", "help",
        "here", "high", "home", "how", "into", "just", "keep", "keyboard", "know", "last",
        "like", "line", "list", "little", "long", "look", "make", "many", "more", "most",
        "much", "must", "need", "never", "new", "next", "not", "now", "only", "open",
        "other", "our", "out", "over", "part", "people", "place", "please", "point", "project",
        "right", "run", "same", "see", "service", "set", "should", "show", "since", "some",
        "something", "still", "such", "system", "take", "test", "than", "thank", "thanks", "that",
        "the", "their", "them", "then", "there", "these", "they", "thing", "think", "this",
        "those", "through", "time", "today", "together", "under", "until", "update", "use", "used",
        "using", "very", "want", "way", "well", "were", "what", "when", "where", "which",
        "while", "who", "why", "will", "with", "work", "world", "would", "write", "year", "your"
    )

    private val idLexicon = arrayOf(
        "ada", "adalah", "agar", "akan", "akses", "aktif", "aku", "ambil", "anda", "antar",
        "antara", "apa", "apakah", "aplikasi", "atau", "awal", "bagaimana", "bagian", "bagus", "bahwa",
        "baik", "banyak", "baru", "bawah", "beberapa", "belum", "benar", "berhasil", "berikut", "bersama",
        "besar", "bisa", "buat", "bukan", "buka", "cara", "cek", "coba", "cukup", "dalam",
        "dan", "dapat", "dari", "data", "dengan", "depan", "dia", "dibuat", "digunakan", "dilakukan",
        "dimana", "diri", "dua", "dulu", "file", "fitur", "gunakan", "hanya", "hari", "harus",
        "hasil", "hingga", "ini", "ingin", "itu", "jadi", "jalan", "jangan", "jauh", "jelas",
        "jika", "juga", "jumlah", "kalau", "kalian", "kami", "kamu", "karena", "kata", "ke",
        "kecil", "keluar", "kembali", "kemudian", "kepada", "kerja", "ketik", "keyboard", "kita", "kode",
        "kondisi", "kurang", "lagi", "lain", "lakukan", "lalu", "lama", "langsung", "lanjut", "lebih",
        "lewat", "lihat", "luar", "maaf", "maka", "makan", "malam", "mana", "masih", "masuk",
        "mau", "melakukan", "memang", "membuat", "memiliki", "menjadi", "menuju", "mereka", "meski", "mulai",
        "mungkin", "namun", "nanti", "nya", "oleh", "orang", "pada", "pagi", "pakai", "paling",
        "panjang", "pasang", "pasti", "perangkat", "perlu", "pertama", "pesan", "pilih", "punya", "saat",
        "saja", "salah", "sama", "sampai", "sangat", "satu", "saya", "sebagai", "sebelum", "sebuah",
        "secara", "sedang", "segera", "sehingga", "sejak", "sekarang", "selalu", "selama", "selanjutnya", "semua",
        "sendiri", "seperti", "sering", "sesuai", "setelah", "setiap", "siap", "silakan", "sistem", "sudah",
        "supaya", "tadi", "tahu", "tahun", "tambah", "tampilan", "tanpa", "tapi", "telah", "tempat",
        "tentang", "tentu", "terjadi", "terakhir", "terlalu", "tersebut", "terus", "tetap", "tidak", "tinggi",
        "tolong", "tugas", "tujuan", "tulis", "untuk", "utama", "waktu", "walaupun", "yang"
    )

    private val learnedWords = ArrayList<String>(64)

    fun getLexicon(isIndonesian: Boolean): Array<String> = if (isIndonesian) idLexicon else enLexicon

    fun getLearnedWords(): List<String> = learnedWords

    fun learnWord(rawWord: String, allowLearn: Boolean) {
        if (!allowLearn) return
        val clean = rawWord.trim().lowercase(Locale.ROOT)
        if (clean.length !in 3..24) return
        if (!clean.all { it.isLetter() }) return
        if (learnedWords.contains(clean)) return
        if (learnedWords.size >= 64) {
            learnedWords.removeAt(0)
        }
        learnedWords.add(clean)
    }

    fun suggest(prefix: String, isIndonesian: Boolean, out: MutableList<String>) {
        out.clear()
        if (prefix.length < 2) return
        val lower = prefix.lowercase(Locale.ROOT)
        val isAllCaps = prefix.length > 1 && prefix.all { it.isUpperCase() }
        val isTitleCase = prefix[0].isUpperCase()

        for (i in learnedWords.indices.reversed()) {
            val w = learnedWords[i]
            if (w.startsWith(lower) && w != lower) {
                out.add(applyCasing(w, isAllCaps, isTitleCase))
                if (out.size >= 3) return
            }
        }

        val dict = getLexicon(isIndonesian)
        var lo = 0
        var hi = dict.size - 1
        var firstIdx = dict.size
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (dict[mid] >= lower) {
                firstIdx = mid
                hi = mid - 1
            } else {
                lo = mid + 1
            }
        }

        var idx = firstIdx
        while (idx < dict.size && out.size < 3) {
            val candidate = dict[idx]
            if (!candidate.startsWith(lower)) break
            if (candidate != lower && !out.contains(candidate)) {
                out.add(applyCasing(candidate, isAllCaps, isTitleCase))
            }
            idx++
        }
    }

    private fun applyCasing(word: String, allCaps: Boolean, titleCase: Boolean): String = when {
        allCaps -> word.uppercase(Locale.ROOT)
        titleCase -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }
}
