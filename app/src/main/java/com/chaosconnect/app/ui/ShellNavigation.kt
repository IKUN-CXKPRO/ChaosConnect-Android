package com.chaosconnect.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.nav.core.*

/** Owned above AppTheme so changing design systems preserves routes and pager motion. */
@Stable
class ShellNavigation(val stack: NavBackStack, val pager: PagerState,
    private val selection: MutableIntState, private val scope: CoroutineScope) {
    val selected get() = selection.intValue
    private var animationJob: Job? = null
    private var velocity = 0f
    fun select(index: Int) {
        if(index !in 0..4 || selected == index) return
        selection.intValue = index
        val initialVelocity = velocity
        animationJob?.cancel()
        animationJob = scope.launch {
            pager.scroll {
                val pageWidth = pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing
                if(pageWidth > 0) {
                    val distance = (index - pager.currentPage - pager.currentPageOffsetFraction) * pageWidth
                    var consumed = 0f
                    animate(0f, distance, initialVelocity = initialVelocity,
                        animationSpec = spring(1f,450f,visibilityThreshold=.5f)) { value, v ->
                        velocity = v
                        consumed += scrollBy(value - consumed)
                    }
                }
            }
            velocity = 0f
        }
    }
    fun settle(page: Int) {
        if(animationJob?.isActive != true) { selection.intValue = page; velocity = 0f }
    }
    fun navigate(route: Route) { if(stack.lastOrNull()!=route) stack.add(route) }
    fun back() { if(stack.size>1) stack.removeLastOrNull() }
}

@Composable
fun rememberShellNavigation(): ShellNavigation {
    val stack = rememberNavBackStack<Route>(Route.Main)
    val pager = rememberPagerState { 5 }
    val selection = rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val navigation = remember(stack,pager,selection,scope) { ShellNavigation(stack,pager,selection,scope) }
    LaunchedEffect(navigation) {
        snapshotFlow { pager.isScrollInProgress to pager.settledPage }.distinctUntilChanged().collect { (moving,page) ->
            if(!moving) navigation.settle(page)
        }
    }
    return navigation
}
