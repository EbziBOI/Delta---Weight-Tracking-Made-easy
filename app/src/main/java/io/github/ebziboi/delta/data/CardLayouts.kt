package io.github.ebziboi.delta.data

object CardLayouts {
    val home = listOf("crash","connect","progress","goal_setup","rate","goal","average","streak","trend","energy","bmi","body_fat","insights","recent")

    /** Cards added in later releases slot in after the card they follow instead of landing at the bottom. */
    private val homeAdditions = listOf("trend" to listOf("energy","bmi"))
    fun homeOrder(saved: List<String>): List<String> {
        val clean=saved.filter {it.isNotBlank()}
        if(clean.isEmpty()) return home
        var order=clean.toMutableList()
        for((after,added) in homeAdditions) {
            val missing=added.filterNot(order::contains)
            val at=order.indexOf(after)
            if(missing.isNotEmpty() && at>=0) order.addAll(at+1,missing)
        }
        return normalized(order,home)
    }
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
