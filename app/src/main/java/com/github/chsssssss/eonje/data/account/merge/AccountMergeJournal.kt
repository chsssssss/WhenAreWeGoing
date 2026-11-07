package com.github.chsssssss.eonje.data.account.merge

import android.content.Context
import com.github.chsssssss.eonje.data.local.MergeInProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class MergeStage {
    /** 스냅샷만 있다. 사용자가 아직 정하지 않았다 — 앱이 이 상태로 다시 시작하면 그냥 버린다(익명 상태 그대로라 연결을 다시 하면 된다). */
    SNAPSHOT,

    /** 사용자가 "합치기"를 골랐고 계정으로 전환하는 중이다. 전환이 끝났으면(uid가 바뀜) 이어서 합치고, 아니면 버린다. */
    SWITCHING,

    /** 계획이 계산돼 있고 쓰는 중이다. 쓰기는 멱등이라 처음부터 다시 해도 된다. */
    MERGING,
}

@Serializable
data class JournalData(
    val fromUid: String,
    val stage: MergeStage,
    val snapshot: MergeSnapshot,
    val targetUid: String? = null,
    val plan: MergePlan? = null,
)

/**
 * 합치기 저널. **캡션이 포함된 사용자 데이터가 합치는 동안만** 기기에 파일로 남는다. `noBackupFilesDir`라서 자동 백업에 들어가지 않고,
 * 합치기가 끝나거나 취소되면 지운다. 앱이 중간에 죽어도 이 파일로 이어서 합칠 수 있다.
 *
 * 저널이 있는 동안 [ThumbnailMaintenance]는 고아 판정을 하지 않는다(합치기가 쓸 썸네일을 지우지 않게).
 */
@Singleton
class AccountMergeJournal(private val file: File) : MergeInProgress {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.noBackupFilesDir, FILE_NAME))

    private val json = Json { ignoreUnknownKeys = true }

    fun exists(): Boolean = file.exists()

    override fun isActive(): Boolean = exists()

    suspend fun read(): JournalData? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            json.decodeFromString(JournalData.serializer(), file.readText())
        } catch (e: Exception) {
            // 읽을 수 없는 저널은 쓸 수 없다. 남겨두면 썸네일 정리가 영영 막히므로 지운다(익명 쪽 서버 데이터는 그대로다).
            file.delete()
            null
        }
    }

    /** 임시 파일에 쓴 뒤 바꿔치기해서, 쓰는 도중에 죽어도 반쯤 쓰인 저널이 남지 않게 한다. */
    suspend fun write(data: JournalData) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(json.encodeToString(JournalData.serializer(), data))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "저널을 저장하지 못했다" }
        }
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        file.delete()
        File(file.parentFile, "$FILE_NAME.tmp").delete()
    }

    companion object {
        const val FILE_NAME = "account_merge_journal.json"
    }
}
