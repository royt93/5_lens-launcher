package com.mckimquyen.util

import com.mckimquyen.model.LayoutBackupLens
import com.mckimquyen.model.LensWorkspace
import java.util.Locale

data class LensGroupSection(
    val groupName: String?,
    val lenses: List<LensWorkspace>
)

object LensGroupPolicy {
    private val roomLensOrder = compareBy<LensWorkspace> { it.orderIndex }.thenBy { it.createdAt }

    fun normalizeName(raw: String): String? = raw.trim().takeIf(String::isNotEmpty)

    fun canonicalKey(name: String): String = name.trim().lowercase(Locale.ROOT)

    fun canonicalize(lenses: List<LensWorkspace>): List<LensWorkspace> {
        val canonicalNames = mutableMapOf<String, String>()
        val labelsById = lenses.sortedWith(roomLensOrder).mapNotNull { lens ->
            val normalized = lens.groupName?.let(::normalizeName) ?: return@mapNotNull null
            val canonical = canonicalNames.getOrPut(canonicalKey(normalized)) { normalized }
            lens.id to canonical
        }.toMap()
        return lenses.map { lens ->
            lens.copy(groupName = labelsById[lens.id])
        }
    }

    fun canonicalizeBackup(lenses: List<LayoutBackupLens>): List<LayoutBackupLens> {
        val canonicalNames = mutableMapOf<String, String>()
        val labelsByIndex = lenses.indices.sortedWith(
            compareBy<Int> { lenses[it].orderIndex }.thenBy { it }
        ).associateWithTo(mutableMapOf()) { index ->
            val normalized = lenses[index].groupName?.let(::normalizeName) ?: return@associateWithTo null
            canonicalNames.getOrPut(canonicalKey(normalized)) { normalized }
        }
        return lenses.mapIndexed { index, lens ->
            lens.copy(groupName = labelsByIndex[index])
        }
    }

    fun sections(lenses: List<LensWorkspace>): List<LensGroupSection> {
        val canonical = canonicalize(lenses).sortedWith(roomLensOrder)
        val sections = mutableListOf<LensGroupSection>()
        val ungrouped = canonical.filter { it.groupName == null }
        if (ungrouped.isNotEmpty()) sections += LensGroupSection(null, ungrouped)
        val grouped = canonical.filter { it.groupName != null }.groupBy { it.groupName }
        val groupOrder = canonical.mapNotNull { it.groupName }.distinct()
        groupOrder.forEach { name ->
            grouped[name]?.let { sections += LensGroupSection(name, it) }
        }
        return sections
    }

    fun canonicalizeCollapsedNames(names: Set<String>, lenses: List<LensWorkspace>): Set<String> {
        val canonical = canonicalize(lenses).mapNotNull { lens ->
            lens.groupName?.let { canonicalKey(it) to it }
        }.toMap()
        return names.mapNotNull { name -> canonical[canonicalKey(name)] }.toSet()
    }
}
