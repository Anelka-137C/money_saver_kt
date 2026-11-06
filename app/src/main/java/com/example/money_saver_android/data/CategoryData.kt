package com.example.money_saver_android.data

data class Subcategory(
    val name: String
)

data class Category(
    val name: String,
    val icon: String = "💰",
    val subcategories: List<Subcategory>
)

val defaultCategories = listOf(
    Category(
        name = "Employment",
        icon = "💼",
        subcategories = listOf(
            Subcategory("Salary"),
            Subcategory("Bonus"),
            Subcategory("Commission"),
            Subcategory("Freelance")
        )
    ),
    Category(
        name = "Food & Dining",
        icon = "🍔",
        subcategories = listOf(
            Subcategory("Supermarket"),
            Subcategory("Restaurants"),
            Subcategory("Coffee")
        )
    ),
    Category(
        name = "Housing & Bills",
        icon = "🏠",
        subcategories = listOf(
            Subcategory("Rent"),
            Subcategory("Utilities"),
            Subcategory("Internet")
        )
    ),
    Category(
        name = "Transport",
        icon = "🚗",
        subcategories = listOf(
            Subcategory("Gas"),
            Subcategory("Public Transit"),
            Subcategory("Taxi")
        )
    ),
    Category(
        name = "Other",
        icon = "📦",
        subcategories = listOf(
            Subcategory("General"),
            Subcategory("Miscellaneous")
        )
    )
)
