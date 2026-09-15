package com.constrakr.domain

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class JobSiteStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    val allSites: List<JobSite>
        get() = loadSites().sortedBy { it.displayTitle.lowercase() }

    val defaultSiteId: UUID?
        get() = prefs.getString(KEY_DEFAULT_ID, null)?.let(UUID::fromString)

    val defaultSite: JobSite?
        get() {
            defaultSiteId?.let { id -> site(id)?.takeIf { it.hasCoordinate } }?.let { return it }
            return allSites.firstOrNull { it.hasCoordinate }
        }

    val hasConfiguredSites: Boolean get() = allSites.any { it.hasCoordinate }

    val pendingUploadIds: Set<UUID>
        get() = prefs.getStringSet(KEY_PENDING_UPLOADS, emptySet())
            ?.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.toSet() ?: emptySet()

    val pendingDeleteIds: Set<UUID>
        get() = prefs.getStringSet(KEY_PENDING_DELETES, emptySet())
            ?.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.toSet() ?: emptySet()

    val pendingSyncCount: Int get() = pendingUploadIds.size + pendingDeleteIds.size

    fun site(id: UUID): JobSite? = allSites.firstOrNull { it.id == id }

    fun setDefaultSite(id: UUID?) {
        if (id == null) prefs.edit().remove(KEY_DEFAULT_ID).apply()
        else prefs.edit().putString(KEY_DEFAULT_ID, id.toString()).apply()
        bump()
    }

    fun upsert(site: JobSite, markPendingUpload: Boolean = true) {
        val sites = loadSites().filter { it.id != site.id } + site
        saveSites(sites)
        if (markPendingUpload) addPendingUpload(site.id)
        if (site.hasCoordinate && defaultSiteId == null) setDefaultSite(site.id)
        bump()
    }

    fun delete(id: UUID) {
        val sites = loadSites().filter { it.id != id }
        saveSites(sites)
        removePendingUpload(id)
        addPendingDelete(id)
        if (defaultSiteId == id) setDefaultSite(sites.firstOrNull { it.hasCoordinate }?.id)
        bump()
    }

    fun applyRemoteCatalog(remote: List<JobSite>) {
        val local = loadSites().associateBy { it.id }.toMutableMap()
        for (r in remote) {
            val existing = local[r.id]
            if (existing == null || r.updatedAtMillis >= existing.updatedAtMillis) {
                local[r.id] = r
            }
        }
        saveSites(local.values.toList())
        clearPendingForSynced(remote.map { it.id }.toSet())
        bump()
    }

    fun clearPendingUpload(id: UUID) {
        val set = pendingUploadIds.toMutableSet()
        set.remove(id)
        prefs.edit().putStringSet(KEY_PENDING_UPLOADS, set.map { it.toString() }.toSet()).apply()
    }

    fun clearPendingDelete(id: UUID) {
        val set = pendingDeleteIds.toMutableSet()
        set.remove(id)
        prefs.edit().putStringSet(KEY_PENDING_DELETES, set.map { it.toString() }.toSet()).apply()
    }

    fun assignedSiteLabel(id: UUID?): String {
        if (id == null) return "Unassigned"
        return site(id)?.displayTitle ?: "Unknown site"
    }

    fun distanceMeters(lat: Double, lon: Double, site: JobSite): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat, lon, site.latitude, site.longitude, results)
        return results[0].toDouble()
    }

    fun siteContaining(lat: Double, lon: Double): JobSite? =
        allSites.firstOrNull { s ->
            s.hasCoordinate && distanceMeters(lat, lon, s) <= s.radiusMeters
        }

    private fun addPendingUpload(id: UUID) {
        val set = pendingUploadIds.toMutableSet()
        set.add(id)
        prefs.edit().putStringSet(KEY_PENDING_UPLOADS, set.map { it.toString() }.toSet()).apply()
    }

    private fun addPendingDelete(id: UUID) {
        val set = pendingDeleteIds.toMutableSet()
        set.add(id)
        prefs.edit().putStringSet(KEY_PENDING_DELETES, set.map { it.toString() }.toSet()).apply()
    }

    private fun removePendingUpload(id: UUID) = clearPendingUpload(id)

    private fun clearPendingForSynced(ids: Set<UUID>) {
        val uploads = pendingUploadIds.toMutableSet()
        uploads.removeAll(ids)
        prefs.edit().putStringSet(KEY_PENDING_UPLOADS, uploads.map { it.toString() }.toSet()).apply()
    }

    private fun bump() {
        _revision.value++
    }

    private fun loadSites(): List<JobSite> {
        val raw = prefs.getString(KEY_SITES_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        JobSite(
                            id = UUID.fromString(o.getString("id")),
                            name = o.optString("name", ""),
                            locationLabel = o.optString("location_label", ""),
                            latitude = o.optDouble("latitude", 0.0),
                            longitude = o.optDouble("longitude", 0.0),
                            radiusMeters = JobSite.clampRadius(o.optDouble("radius_meters", JobSite.DEFAULT_RADIUS)),
                            updatedAtMillis = o.optLong("updated_at", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveSites(sites: List<JobSite>) {
        val arr = JSONArray()
        for (s in sites) {
            arr.put(
                JSONObject().apply {
                    put("id", s.id.toString())
                    put("name", s.name)
                    put("location_label", s.locationLabel)
                    put("latitude", s.latitude)
                    put("longitude", s.longitude)
                    put("radius_meters", s.radiusMeters)
                    put("updated_at", s.updatedAtMillis)
                }
            )
        }
        prefs.edit().putString(KEY_SITES_JSON, arr.toString()).apply()
    }

    companion object {
        private const val PREFS = "constrakr.job-sites"
        private const val KEY_SITES_JSON = "jobSitesJSON"
        private const val KEY_DEFAULT_ID = "defaultJobSiteId"
        private const val KEY_PENDING_UPLOADS = "pendingJobSiteUploads"
        private const val KEY_PENDING_DELETES = "pendingJobSiteDeletions"
    }
}
