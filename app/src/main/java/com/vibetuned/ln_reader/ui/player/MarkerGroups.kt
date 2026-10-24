package com.vibetuned.ln_reader.ui.player

/** Scrubber markers drawn as one badge: every marker in the group, and where the badge sits. */
internal data class MarkerGroup<T>(val items: List<T>, val center: Float)

/**
 * Groups markers that would sit on top of each other along the scrubber. Sorted by [position], a
 * marker joins the group before it when it is closer than [minGap] to that group's last marker;
 * each group's badge goes in the middle of its span.
 *
 * Joining on the gap to the *last* marker is what keeps badges apart: a new group only starts a
 * full [minGap] after the previous one ends, and each badge sits inside its own span, so any two
 * badges are at least [minGap] apart, however the markers are spread. A run of markers each just
 * under [minGap] from the next becomes one wide group rather than a row of overlapping ones.
 *
 * Mirrors `MarkerGrouping` in the iOS app.
 */
internal fun <T> groupMarkers(items: List<T>, position: (T) -> Float, minGap: Float): List<MarkerGroup<T>> {
    val groups = mutableListOf<MutableList<T>>()
    for (item in items.sortedBy(position)) {
        val current = groups.lastOrNull()
        if (current != null && position(item) - position(current.last()) < minGap) {
            current += item
        } else {
            groups += mutableListOf(item)
        }
    }
    return groups.map { group ->
        MarkerGroup(group, (position(group.first()) + position(group.last())) / 2f)
    }
}
