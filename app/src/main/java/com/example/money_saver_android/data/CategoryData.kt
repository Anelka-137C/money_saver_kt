package com.example.money_saver_android.data

data class Subcategory(
    val name: String
)

data class Category(
    val name: String,
    val icon: String = "💰",
    val color: String = "#4E73DF",
    val subcategories: List<Subcategory>
)

val defaultCategories = listOf(
    Category(
        name = "Employment",
        icon = "💼",
        color = "#4E73DF",
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
        color = "#1CC88A",
        subcategories = listOf(
            Subcategory("Supermarket"),
            Subcategory("Restaurants"),
            Subcategory("Coffee")
        )
    ),
    Category(
        name = "Housing & Bills",
        icon = "🏠",
        color = "#F6C23E",
        subcategories = listOf(
            Subcategory("Rent"),
            Subcategory("Utilities"),
            Subcategory("Internet")
        )
    ),
    Category(
        name = "Transport",
        icon = "🚗",
        color = "#E74A3B",
        subcategories = listOf(
            Subcategory("Gas"),
            Subcategory("Public Transit"),
            Subcategory("Taxi")
        )
    ),
    Category(
        name = "Other",
        icon = "📦",
        color = "#858796",
        subcategories = listOf(
            Subcategory("General"),
            Subcategory("Miscellaneous")
        )
    )
)
