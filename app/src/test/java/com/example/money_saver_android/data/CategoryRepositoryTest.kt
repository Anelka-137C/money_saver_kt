package com.example.money_saver_android.data

import android.content.Context
import androidx.room.Room
import com.example.money_saver_android.data.local.AppDatabase
import com.example.money_saver_android.data.repository.RoomCategoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CategoryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomCategoryRepository

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCategoryRepository(db.categoryDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun categories_initialPreload_containsDefaultCategoriesWithColors() = runBlocking {
        repository.preloadDefaultCategoriesIfNeeded()
        val categories = repository.getAllCategories().first()

        assertTrue(categories.isNotEmpty())
        val employment = categories.find { it.name == "Employment" }
        assertTrue(employment != null)
        assertTrue(employment!!.color.isNotBlank())
        assertTrue(employment.subcategories.isNotEmpty())
    }

    @Test
    fun saveCategory_insertsNewCategoryWithColor() = runBlocking {
        val newCategory = Category(
            name = "Inversiones",
            icon = "📈",
            color = "#6F42C1",
            subcategories = emptyList()
        )

        repository.insertCategory(newCategory)

        val categories = repository.getAllCategories().first()
        val found = categories.find { it.name == "Inversiones" }
        assertTrue(found != null)
        assertEquals("#6F42C1", found!!.color)
        assertEquals("📈", found.icon)
    }

    @Test
    fun saveSubcategory_appendsSubcategoryToExistingCategory() = runBlocking {
        val initialCategory = Category(
            name = "Educación",
            icon = "📚",
            color = "#20C997",
            subcategories = listOf(Subcategory("Cursos"))
        )
        repository.insertCategory(initialCategory)

        repository.insertSubcategory("Educación", Subcategory("Libros"))

        val categories = repository.getAllCategories().first()
        val education = categories.find { it.name == "Educación" }
        assertTrue(education != null)
        val subNames = education!!.subcategories.map { it.name }
        assertTrue(subNames.contains("Cursos"))
        assertTrue(subNames.contains("Libros"))
    }

    @Test
    fun subcategories_initialPreload_populatesDefaultSubcategoriesWhenEmpty() = runBlocking {
        repository.preloadDefaultCategoriesIfNeeded()

        val subcategoryCount = db.categoryDao().getSubcategoryCount()
        assertTrue(subcategoryCount > 0)

        val categories = repository.getAllCategories().first()
        val food = categories.find { it.name == "Food & Dining" }
        assertTrue(food != null)
        assertTrue(food!!.subcategories.any { it.name == "Supermarket" })
    }
}
