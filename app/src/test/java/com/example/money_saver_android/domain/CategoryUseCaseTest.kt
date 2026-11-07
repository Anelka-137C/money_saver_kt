package com.example.money_saver_android.domain

import com.example.money_saver_android.data.Category
import com.example.money_saver_android.data.Subcategory
import com.example.money_saver_android.data.repository.CategoryRepository
import com.example.money_saver_android.domain.usecase.CategoryResult
import com.example.money_saver_android.domain.usecase.CategoryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeCategoryRepository : CategoryRepository {
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    override fun getAllCategories(): Flow<List<Category>> = _categories

    override suspend fun preloadDefaultCategoriesIfNeeded() {
        if (_categories.value.isEmpty()) {
            _categories.value = listOf(
                Category(name = "Comida", icon = "🍔", color = "#1CC88A", subcategories = listOf(Subcategory("Restaurante")))
            )
        }
    }

    override suspend fun insertCategory(category: Category) {
        _categories.value = _categories.value + category
    }

    override suspend fun insertSubcategory(categoryName: String, subcategory: Subcategory) {
        _categories.value = _categories.value.map { cat ->
            if (cat.name.equals(categoryName, ignoreCase = true)) {
                cat.copy(subcategories = cat.subcategories + subcategory)
            } else {
                cat
            }
        }
    }
}

class CategoryUseCaseTest {

    private lateinit var repository: FakeCategoryRepository
    private lateinit var useCase: CategoryUseCase

    @Before
    fun setup() {
        repository = FakeCategoryRepository()
        useCase = CategoryUseCase(repository)
    }

    @Test
    fun createCategory_withValidNameAndColor_returnsSuccessAndPersists() = runBlocking {
        val result = useCase.createCategory(nameInput = "Salud", colorInput = "#E74A3B")

        assertTrue(result is CategoryResult.Success)
        val list = useCase.getCategories().first()
        assertEquals(1, list.size)
        assertEquals("Salud", list.first().name)
        assertEquals("#E74A3B", list.first().color)
    }

    @Test
    fun createCategory_withBlankName_returnsError() = runBlocking {
        val result = useCase.createCategory(nameInput = "   ", colorInput = "#E74A3B")

        assertTrue(result is CategoryResult.Error)
        assertEquals("El nombre de la categoría no puede estar vacío", (result as CategoryResult.Error).message)
        val list = useCase.getCategories().first()
        assertEquals(0, list.size)
    }

    @Test
    fun createCategory_withDuplicateName_returnsError() = runBlocking {
        repository.insertCategory(Category(name = "Salud", color = "#E74A3B", subcategories = emptyList()))

        val result = useCase.createCategory(nameInput = "salud", colorInput = "#1CC88A")

        assertTrue(result is CategoryResult.Error)
        assertEquals("Ya existe una categoría con este nombre", (result as CategoryResult.Error).message)
        val list = useCase.getCategories().first()
        assertEquals(1, list.size)
    }

    @Test
    fun createSubcategory_withValidName_returnsSuccessAndPersists() = runBlocking {
        repository.insertCategory(Category(name = "Salud", color = "#E74A3B", subcategories = emptyList()))

        val result = useCase.createSubcategory(categoryName = "Salud", subcategoryNameInput = "Farmacia")

        assertTrue(result is CategoryResult.Success)
        val list = useCase.getCategories().first()
        val category = list.first()
        assertEquals(1, category.subcategories.size)
        assertEquals("Farmacia", category.subcategories.first().name)
    }

    @Test
    fun createSubcategory_withBlankName_returnsError() = runBlocking {
        repository.insertCategory(Category(name = "Salud", color = "#E74A3B", subcategories = emptyList()))

        val result = useCase.createSubcategory(categoryName = "Salud", subcategoryNameInput = "  ")

        assertTrue(result is CategoryResult.Error)
        assertEquals("El nombre de la subcategoría no puede estar vacío", (result as CategoryResult.Error).message)
        val list = useCase.getCategories().first()
        assertEquals(0, list.first().subcategories.size)
    }

    @Test
    fun createSubcategory_withDuplicateName_returnsError() = runBlocking {
        repository.insertCategory(
            Category(
                name = "Salud",
                color = "#E74A3B",
                subcategories = listOf(Subcategory("Farmacia"))
            )
        )

        val result = useCase.createSubcategory(categoryName = "Salud", subcategoryNameInput = "farmacia")

        assertTrue(result is CategoryResult.Error)
        assertEquals("Ya existe una subcategoría con este nombre", (result as CategoryResult.Error).message)
        val list = useCase.getCategories().first()
        assertEquals(1, list.first().subcategories.size)
    }
}
