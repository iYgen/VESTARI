package app.vestari.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryTreeTest {
    private fun c(id: String, parent: String? = null) = Category(id, id, "folder", parent = parent)

    private val cats = listOf(
        c("work"), c("home"),
        c("projects", "work"), c("y2024", "projects"), c("q1", "y2024"), c("jan", "q1"),
        c("bills", "home"), c("orphan", "missing"),
    )

    @Test
    fun fiveLevelsInOrder() {
        val tree = categoryTree(cats)
        assertEquals(listOf("work", "projects", "y2024", "q1", "jan", "home", "bills", "orphan"), tree.map { it.category.id })
        assertEquals(listOf(0, 1, 2, 3, 4, 0, 1, 0), tree.map { it.depth })
        assertEquals(true, tree.first { it.category.id == "q1" }.hasChildren)
        assertEquals(false, tree.first { it.category.id == "jan" }.hasChildren)
    }

    @Test
    fun collapsedBranchHidesDescendants() {
        val tree = categoryTree(cats, collapsed = setOf("projects"))
        assertEquals(listOf("work", "projects", "home", "bills", "orphan"), tree.map { it.category.id })
    }

    @Test
    fun pathAndSubtree() {
        assertEquals(listOf("work", "projects", "y2024", "q1", "jan"), cats.pathTo("jan").map { it.id })
        assertEquals(setOf("y2024", "q1", "jan"), cats.subtreeIds("y2024"))
        assertEquals(listOf("projects"), cats.childrenOf("work").map { it.id })
        assertEquals(true, cats.isTopLevel(cats.first { it.id == "orphan" }))
    }

    @Test
    fun cycleDoesNotHang() {
        val loop = listOf(c("a", "b"), c("b", "a"))
        assertEquals(2, loop.pathTo("a").size)
        assertEquals(setOf("a", "b"), loop.subtreeIds("a"))
    }
}
