package com.anitail.desktop.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Representa una pantalla en la navegación.
 */
sealed class Screen {
    object Home : Screen()
    object Explore : Screen()
    object Library : Screen()
    object Settings : Screen()
    
    // Pantallas de detalle
    data class ArtistDetail(val artistId: String, val artistName: String) : Screen()
    data class AlbumDetail(val albumId: String, val albumName: String) : Screen()
    data class PlaylistDetail(val playlistId: String, val playlistName: String) : Screen()
    data class Search(val query: String = "") : Screen()
}

/**
 * Estado de navegación con soporte para back stack.
 * Mantiene un historial de pantallas visitadas para permitir navegación hacia atrás.
 */
class NavigationState {
    private val backStack = mutableStateListOf<Screen>()
    var currentScreen by mutableStateOf<Screen>(Screen.Home)
        private set
    
    /**
     * Indica si podemos navegar hacia atrás.
     */
    val canGoBack: Boolean
        get() = backStack.isNotEmpty()
    
    /**
     * Navega a una nueva pantalla, agregando la actual al back stack.
     */
    fun navigateTo(screen: Screen) {
        // No agregar duplicados consecutivos
        if (currentScreen != screen) {
            backStack.add(currentScreen)
            currentScreen = screen
        }
    }
    
    /**
     * Navega hacia atrás en el historial.
     * @return true si se navegó hacia atrás, false si no había historial.
     */
    fun goBack(): Boolean {
        return if (backStack.isNotEmpty()) {
            currentScreen = backStack.removeAt(backStack.lastIndex)
            true
        } else {
            false
        }
    }
    
    /**
     * Navega a una de las pantallas principales (Home, Explore, Library, Settings)
     * limpiando el back stack si es necesario.
     */
    fun navigateToRoot(screen: Screen) {
        backStack.clear()
        currentScreen = screen
    }
    
    /**
     * Navega a Home limpiando todo el back stack.
     */
    fun navigateToHomeAndClear() {
        backStack.clear()
        currentScreen = Screen.Home
    }
    
    /**
     * Retorna la cantidad de pantallas en el historial.
     */
    val backStackSize: Int
        get() = backStack.size
}

/**
 * Recuerda el estado de navegación.
 */
@Composable
fun rememberNavigationState(): NavigationState {
    return remember { NavigationState() }
}
