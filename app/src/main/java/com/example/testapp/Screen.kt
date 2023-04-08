package com.example.testapp

sealed class Screen(route: String) {
    object FirstScreen: Screen("first_screen")
    object SecondScreen : Screen("second_screen")
}