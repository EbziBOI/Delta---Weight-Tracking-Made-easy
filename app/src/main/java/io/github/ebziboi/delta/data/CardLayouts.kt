package io.github.ebziboi.delta.data

object CardLayouts {
    val home = listOf("crash","connect","progress","goal_setup","rate","goal","average","streak","trend","body_fat","insights","recent")
    val measurement = listOf("calendar","readings","graph","insight-average","insight-change","insight-comparison",
        "insight-coverage","insight-pace","insight-range","insight-variation","insight-consistency","insight-latest","empty")

    fun normalized(saved: List<String>, defaults: List<String>): List<String> =
        saved.filter {it in defaults}.distinct().let {it + defaults.filterNot(it::contains)}

    /** Hidden cards retain their places while the visible cards are rearranged. */
    fun mergeVisible(saved: List<String>, reordered: List<String>, defaults: List<String>): List<String> {
        val base=normalized(saved,defaults)
        val visible=reordered.filter {it in defaults}.distinct()
        val iterator=visible.iterator()
        return base.map {if(it in visible) iterator.next() else it}
    }
}
