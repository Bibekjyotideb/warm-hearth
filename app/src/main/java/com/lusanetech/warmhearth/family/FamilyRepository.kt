package com.lusanetech.warmhearth.family

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.lusanetech.warmhearth.data.AppDatabase
import com.lusanetech.warmhearth.data.FavoriteContact
import com.lusanetech.warmhearth.data.Medicine
import com.lusanetech.warmhearth.data.Prefs
import com.lusanetech.warmhearth.meds.MedicineScheduler
import com.lusanetech.warmhearth.util.PhotoStore
import com.lusanetech.warmhearth.util.Telemetry
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom

data class RemoteDevice(val id: String, val name: String, val premium: Boolean)
data class RemoteEvent(val id: String, val type: String, val message: String, val lat: Double?, val lng: Double?, val createdAt: Long)

/**
 * Everything that talks to Firebase.
 *
 * Firestore layout (see firestore.rules):
 *   devices/{deviceId}                 ownerUid, name, premium, family:[uid], lastPairCode
 *   devices/{deviceId}/contacts/{id}   name, phone, position, isEmergency, updatedAt, deleted
 *   devices/{deviceId}/medicines/{id}  name, dose, times, active, updatedAt, deleted
 *   devices/{deviceId}/events/{id}     type (sos | missed_dose), message, lat, lng, createdAt
 *   pairCodes/{code}                   deviceId, ownerUid, expiresAt
 *   users/{uid}                        fcmTokens:[token]
 *
 * Deletes are "tombstones" (deleted = true) so a phone that was offline can't resurrect them.
 * Photos stay on the senior's phone; the family sees initials.
 */
class FamilyRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val prefs: Prefs,
    private val scheduler: MedicineScheduler,
    private val scope: CoroutineScope,
) {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val fs by lazy { FirebaseFirestore.getInstance() }
    private var seniorSyncStarted = false

    companion object { private const val TAG = "Family" }

    suspend fun uid(): String? = runCatching {
        auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid
    }.onFailure { Log.w(TAG, "Sign-in failed", it) }.getOrNull()

    private fun device(id: String) = fs.collection("devices").document(id)

    // ------------------------------------------------------------------ senior side

    private suspend fun ensureDeviceDoc(): String? {
        val uid = uid() ?: return null
        val id = prefs.ensureDeviceId()
        val s = prefs.current()
        val doc = device(id)
        runCatching {
            val snap = doc.get().await()
            if (!snap.exists()) {
                doc.set(mapOf("ownerUid" to uid, "name" to s.seniorName, "family" to emptyList<String>(), "createdAt" to FieldValue.serverTimestamp())).await()
            } else {
                doc.set(mapOf("name" to s.seniorName), SetOptions.merge())
            }
        }.onFailure { Log.w(TAG, "device doc", it); return null }
        return id
    }

    /** Short code the family member types on their phone. Valid for 30 minutes. */
    suspend fun createPairCode(): String? {
        val id = ensureDeviceDoc() ?: return null
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O, 1/I confusion
        val rnd = SecureRandom()
        val code = (1..8).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
        return runCatching {
            fs.collection("pairCodes").document(code).set(
                mapOf("deviceId" to id, "ownerUid" to uid(), "expiresAt" to Timestamp(java.util.Date(System.currentTimeMillis() + 30 * 60_000))),
            ).await()
            code
        }.getOrNull()
    }

    /** Mirrors family edits into the local database and pushes local-only items up. */
    fun startSeniorSync() {
        if (seniorSyncStarted) return
        seniorSyncStarted = true
        scope.launch {
            val id = ensureDeviceDoc() ?: run { seniorSyncStarted = false; return@launch }
            val doc = device(id)

            doc.addSnapshotListener { snap, _ ->
                snap ?: return@addSnapshotListener
                val family = (snap.get("family") as? List<*>)?.size ?: 0
                val remotePremium = snap.getBoolean("premium") == true
                scope.launch {
                    prefs.setPaired(family > 0)
                    if (family > 0) Telemetry.milestone(context, id, Telemetry.Step.LINKED_FAMILY)
                    // The server is the source of truth for premium (granted by verifyPurchase, revoked on refund).
                    if (remotePremium != prefs.current().premium) prefs.setPremium(remotePremium)
                }
            }

            doc.collection("contacts").addSnapshotListener { snap, _ ->
                snap ?: return@addSnapshotListener
                scope.launch {
                    val remoteIds = HashSet<String>()
                    for (d in snap.documents) {
                        remoteIds += d.id
                        val local = db.contacts().getAll().firstOrNull { it.id == d.id }
                        if (d.getBoolean("deleted") == true) {
                            if (local != null) { db.contacts().delete(d.id); PhotoStore.delete(local.photoPath) }
                            continue
                        }
                        val remote = d.toContact(keepPhoto = local?.photoPath) ?: continue
                        if (local == null || remote.updatedAt > local.updatedAt) db.contacts().upsert(remote)
                    }
                    // Anything created before linking (or while offline) goes up.
                    db.contacts().getAll().filter { it.id !in remoteIds }.forEach { pushContact(id, it) }
                }
            }

            doc.collection("medicines").addSnapshotListener { snap, _ ->
                snap ?: return@addSnapshotListener
                scope.launch {
                    val remoteIds = HashSet<String>()
                    for (d in snap.documents) {
                        remoteIds += d.id
                        val local = db.medicines().get(d.id)
                        if (d.getBoolean("deleted") == true) {
                            if (local != null) { scheduler.cancel(local); db.medicines().delete(d.id); PhotoStore.delete(local.photoPath) }
                            continue
                        }
                        val remote = d.toMedicine(keepPhoto = local?.photoPath) ?: continue
                        if (local == null || remote.updatedAt > local.updatedAt) {
                            local?.let { scheduler.cancel(it) }
                            db.medicines().upsert(remote)
                            scheduler.schedule(remote)
                        }
                    }
                    db.medicines().allIds().filter { it !in remoteIds }.forEach { mid ->
                        db.medicines().get(mid)?.let { pushMedicine(id, it) }
                    }
                }
            }
        }
    }

    suspend fun saveContact(c: FavoriteContact) {
        db.contacts().upsert(c)
        Telemetry.milestone(context, prefs.current().deviceId, Telemetry.Step.ADDED_CONTACT)
        prefs.current().deviceId.takeIf { it.isNotBlank() }?.let { pushContact(it, c) }
    }

    suspend fun deleteContact(c: FavoriteContact) {
        db.contacts().delete(c.id)
        PhotoStore.delete(c.photoPath)
        prefs.current().deviceId.takeIf { it.isNotBlank() }?.let { tombstone(it, "contacts", c.id) }
    }

    suspend fun saveMedicine(m: Medicine) {
        db.medicines().get(m.id)?.let { scheduler.cancel(it) }
        db.medicines().upsert(m)
        scheduler.schedule(m)
        Telemetry.milestone(context, prefs.current().deviceId, Telemetry.Step.ADDED_MEDICINE)
        prefs.current().deviceId.takeIf { it.isNotBlank() }?.let { pushMedicine(it, m) }
    }

    suspend fun deleteMedicine(m: Medicine) {
        scheduler.cancel(m)
        db.medicines().delete(m.id)
        PhotoStore.delete(m.photoPath)
        prefs.current().deviceId.takeIf { it.isNotBlank() }?.let { tombstone(it, "medicines", m.id) }
    }

    /** Alert for the family (SOS, missed dose). Returns true if the phone is linked to family. */
    suspend fun sendEvent(type: String, message: String, lat: Double? = null, lng: Double? = null): Boolean {
        val s = prefs.current()
        if (!s.paired || s.deviceId.isBlank()) return false
        if (uid() == null) return false
        val data = hashMapOf<String, Any?>(
            "type" to type, "message" to message, "lat" to lat, "lng" to lng,
            "createdAt" to FieldValue.serverTimestamp(),
            // Firestore TTL deletes alerts after 90 days (see firestore.indexes.json / privacy policy).
            "expireAt" to Timestamp(java.util.Date(System.currentTimeMillis() + 90L * 86_400_000)),
        )
        // Firestore queues the write while offline and sends it when the network returns.
        val task = device(s.deviceId).collection("events").add(data)
        withTimeoutOrNull(5_000) { runCatching { task.await() } }
        return true
    }

    private fun pushContact(deviceId: String, c: FavoriteContact) {
        device(deviceId).collection("contacts").document(c.id).set(c.toMap())
    }

    private fun pushMedicine(deviceId: String, m: Medicine) {
        device(deviceId).collection("medicines").document(m.id).set(m.toMap())
    }

    private fun tombstone(deviceId: String, collection: String, id: String) {
        device(deviceId).collection(collection).document(id)
            .set(mapOf("deleted" to true, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
    }

    // ------------------------------------------------------------------ family side

    suspend fun registerFamilyToken() {
        val user = auth.currentUser?.takeIf { !it.isAnonymous } ?: return
        val ref = fs.collection("users").document(user.uid)
        val profile = hashMapOf<String, Any>("role" to "family")
        user.email?.let { profile["email"] = it.lowercase() }
        user.displayName?.let { profile["displayName"] = it }
        runCatching { if (!ref.get().await().exists()) profile["createdAt"] = FieldValue.serverTimestamp() }
        runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()?.let { profile["fcmTokens"] = FieldValue.arrayUnion(it) }
        runCatching { ref.set(profile, SetOptions.merge()).await() }
        Telemetry.heartbeat(context, "users", user.uid)
    }

    /** Family side: premium comes from users/{uid}.premium, set by the server after a verified purchase. */
    fun watchFamilyPremium() {
        val uid = auth.currentUser?.takeIf { !it.isAnonymous }?.uid ?: return
        fs.collection("users").document(uid).addSnapshotListener { snap, _ ->
            val premium = snap?.getBoolean("premium") == true
            scope.launch { if (premium != prefs.current().premium) prefs.setPremium(premium) }
        }
    }

    /** Deletes the account and all data the caller owns on the server (Play's account-deletion requirement). */
    suspend fun deleteMyAccount(): Boolean = runCatching {
        FirebaseFunctions.getInstance("asia-south1").getHttpsCallable("deleteMyAccount").call().await()
        auth.signOut()
        true
    }.getOrElse { Log.w(TAG, "delete", it); false }

    sealed interface LinkResult {
        data class Linked(val deviceId: String) : LinkResult
        data object BadCode : LinkResult
        data object Error : LinkResult
    }

    suspend fun linkWithCode(raw: String): LinkResult {
        val code = raw.uppercase().filter { it.isLetterOrDigit() }
        if (code.length != 8) return LinkResult.BadCode
        val uid = uid() ?: return LinkResult.Error
        return runCatching {
            val pc = fs.collection("pairCodes").document(code).get().await()
            val deviceId = pc.getString("deviceId")
            val expires = pc.getTimestamp("expiresAt")
            if (!pc.exists() || deviceId == null || expires == null || expires.toDate().time < System.currentTimeMillis()) {
                return LinkResult.BadCode
            }
            device(deviceId).update(mapOf("family" to FieldValue.arrayUnion(uid), "lastPairCode" to code)).await()
            registerFamilyToken()
            LinkResult.Linked(deviceId) as LinkResult
        }.getOrElse { Log.w(TAG, "link", it); LinkResult.Error }
    }

    fun observeMyDevices(): Flow<List<RemoteDevice>> = callbackFlow {
        val uid = uid()
        if (uid == null) { trySend(emptyList()); close(); return@callbackFlow }
        val reg = fs.collection("devices").whereArrayContains("family", uid).addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.map { RemoteDevice(it.id, it.getString("name") ?: "", it.getBoolean("premium") == true) } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    fun observeContacts(deviceId: String): Flow<List<FavoriteContact>> = callbackFlow {
        val reg = device(deviceId).collection("contacts").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.filter { it.getBoolean("deleted") != true }?.mapNotNull { it.toContact(null) }?.sortedBy { it.position } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    fun observeMedicines(deviceId: String): Flow<List<Medicine>> = callbackFlow {
        val reg = device(deviceId).collection("medicines").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.filter { it.getBoolean("deleted") != true }?.mapNotNull { it.toMedicine(null) }?.sortedBy { it.name } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    fun observeEvents(deviceId: String): Flow<List<RemoteEvent>> = callbackFlow {
        val reg = device(deviceId).collection("events").orderBy("createdAt", Query.Direction.DESCENDING).limit(20)
            .addSnapshotListener { snap, _ ->
                trySend(
                    snap?.documents?.map {
                        RemoteEvent(
                            it.id, it.getString("type") ?: "", it.getString("message") ?: "",
                            it.getDouble("lat"), it.getDouble("lng"), it.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                        )
                    } ?: emptyList(),
                )
            }
        awaitClose { reg.remove() }
    }

    fun remoteSaveContact(deviceId: String, c: FavoriteContact) = pushContact(deviceId, c.copy(updatedAt = System.currentTimeMillis()))
    fun remoteDeleteContact(deviceId: String, id: String) = tombstone(deviceId, "contacts", id)
    fun remoteSaveMedicine(deviceId: String, m: Medicine) = pushMedicine(deviceId, m.copy(updatedAt = System.currentTimeMillis()))
    fun remoteDeleteMedicine(deviceId: String, id: String) = tombstone(deviceId, "medicines", id)

    // ------------------------------------------------------------------ mapping

    private fun FavoriteContact.toMap() = mapOf(
        "name" to name, "phone" to phone, "position" to position, "isEmergency" to isEmergency,
        "updatedAt" to updatedAt, "deleted" to false,
    )

    private fun Medicine.toMap() = mapOf(
        "name" to name, "dose" to dose, "times" to times, "active" to active, "updatedAt" to updatedAt, "deleted" to false,
    )

    private fun DocumentSnapshot.toContact(keepPhoto: String?): FavoriteContact? {
        val name = getString("name") ?: return null
        return FavoriteContact(
            id = id, name = name, phone = getString("phone") ?: "", photoPath = keepPhoto,
            position = getLong("position")?.toInt() ?: 0, isEmergency = getBoolean("isEmergency") == true,
            updatedAt = getLong("updatedAt") ?: 0L,
        )
    }

    private fun DocumentSnapshot.toMedicine(keepPhoto: String?): Medicine? {
        val name = getString("name") ?: return null
        return Medicine(
            id = id, name = name, dose = getString("dose") ?: "", photoPath = keepPhoto,
            times = getString("times") ?: return null, active = getBoolean("active") != false,
            updatedAt = getLong("updatedAt") ?: 0L,
        )
    }
}
