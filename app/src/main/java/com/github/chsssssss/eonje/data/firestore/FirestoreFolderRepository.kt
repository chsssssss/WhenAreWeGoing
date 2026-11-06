package com.github.chsssssss.eonje.data.firestore

import com.github.chsssssss.eonje.di.ApplicationScope
import com.github.chsssssss.eonje.domain.model.Folder
import com.github.chsssssss.eonje.domain.model.FolderName
import com.github.chsssssss.eonje.domain.repository.FolderRepository
import com.github.chsssssss.eonje.domain.repository.NotSignedInException
import com.github.chsssssss.eonje.domain.repository.PlacesChangedNotifier
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 폴더 저장소(Firestore). users/{uid}/folders/{folderId}. 쓰기는 서버 응답을 기다리지 않는다. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreFolderRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: AuthSession,
    private val placesChangedNotifier: PlacesChangedNotifier,
    @ApplicationScope private val scope: CoroutineScope,
) : FolderRepository {

    private val allFolders: Flow<List<Folder>> = auth.uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                firestore.folders(uid).orderBy(Fields.NAME).snapshots()
                    .map { snapshot -> snapshot.documents.mapNotNull { it.toFolder() } }
            }
        }
        .catch { logListenerError("폴더", it); emit(emptyList()) }
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    override fun observeAll(): Flow<List<Folder>> = allFolders

    override suspend fun loadCachedAll(): List<Folder> {
        val uid = auth.currentUid() ?: return emptyList()
        return try {
            firestore.folders(uid).orderBy(Fields.NAME).get(Source.CACHE).await().documents.mapNotNull { it.toFolder() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun findOrCreateByName(name: String): Folder {
        val trimmed = FolderName.clamp(name.trim())
        findByName(trimmed)?.let { return it }
        return create(Folder(id = UUID.randomUUID().toString(), name = trimmed, createdAt = System.currentTimeMillis()))
    }

    override suspend fun createWithAppearance(name: String, color: Int, iconKey: String): Folder {
        val trimmed = FolderName.clamp(name.trim())
        val existing = findByName(trimmed)
        if (existing != null) {
            val uid = requireUid()
            firestore.folders(uid).document(existing.id)
                .update(mapOf(Fields.COLOR to color, Fields.ICON_KEY to iconKey)).logFailure("폴더 외형 변경")
            return existing.copy(color = color, iconKey = iconKey)
        }
        return create(
            Folder(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                createdAt = System.currentTimeMillis(),
                color = color,
                iconKey = iconKey,
            ),
        )
    }

    override suspend fun delete(folderId: String) {
        val uid = requireUid()
        // 이 폴더의 장소를 먼저 전부 미분류(folderId = null)로 돌리고, 마지막에 폴더 문서를 지운다.
        // 중간에 끊겨도 "폴더는 남고 장소는 이미 미분류"인 쪽이 "삭제된 폴더를 가리키는 장소"보다 낫다.
        val placesInFolder = firestore.places(uid).whereEqualTo(Fields.FOLDER_ID, folderId)
            .getCacheFirst()?.documents.orEmpty()
        val ops = buildList<(WriteBatch) -> Unit> {
            placesInFolder.forEach { doc -> add { batch -> batch.update(doc.reference, Fields.FOLDER_ID, null) } }
            add { batch -> batch.delete(firestore.folders(uid).document(folderId)) }
        }
        firestore.commitInChunks("폴더 삭제", ops)
        // 이 폴더에 있던 장소는 미분류가 된다 — 위젯의 폴더 줄도 바뀌어야 한다.
        placesChangedNotifier.notifyPlacesChanged()
    }

    private suspend fun findByName(name: String): Folder? {
        val uid = requireUid()
        return firestore.folders(uid).whereEqualTo(Fields.NAME, name).limit(1)
            .getCacheFirst()?.documents?.firstOrNull()?.toFolder()
    }

    private suspend fun create(folder: Folder): Folder {
        val uid = requireUid()
        firestore.folders(uid).document(folder.id).set(folder.toFields()).logFailure("폴더 생성")
        return folder
    }

    private suspend fun requireUid(): String =
        auth.awaitUid(UID_WAIT_TIMEOUT_MILLIS) ?: throw NotSignedInException()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val UID_WAIT_TIMEOUT_MILLIS = 2_000L
    }
}
