package com.example.ui.screens.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FavoriteAuthor
import com.example.ui.components.ChubUrlImportDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChubGalleryScreen(
    viewModel: ChubGalleryViewModel,
    onOpenDrawer: () -> Unit,
    onNavigateToAuthor: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()
    var showUrlImportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Chub Gallery",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.testTag("gallery_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showUrlImportDialog = true },
                        modifier = Modifier.testTag("gallery_import_url_button")
                    ) {
                        Icon(imageVector = Icons.Default.AddLink, contentDescription = "Import by URL")
                    }
                    IconButton(
                        onClick = { viewModel.performSearch(reset = true) },
                        modifier = Modifier.testTag("gallery_search_button")
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tabs: Discover, Trending, Favorite Authors (Matching Image 2)
            val selectedTabIndex = when (state.selectedTab) {
                GalleryTab.DISCOVER -> 0
                GalleryTab.TRENDING -> 1
                GalleryTab.FAVORITE_AUTHORS -> 2
            }
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = state.selectedTab == GalleryTab.DISCOVER,
                    onClick = { viewModel.setSelectedTab(GalleryTab.DISCOVER) },
                    text = { Text("Discover") },
                    icon = { Icon(Icons.Default.TravelExplore, contentDescription = null) }
                )
                Tab(
                    selected = state.selectedTab == GalleryTab.TRENDING,
                    onClick = { viewModel.setSelectedTab(GalleryTab.TRENDING) },
                    text = { Text("Trending") },
                    icon = { Icon(Icons.Default.TrendingUp, contentDescription = null) }
                )
                Tab(
                    selected = state.selectedTab == GalleryTab.FAVORITE_AUTHORS,
                    onClick = { viewModel.setSelectedTab(GalleryTab.FAVORITE_AUTHORS) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Favorite Authors")
                            if (state.favoriteAuthors.isNotEmpty()) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text("${state.favoriteAuthors.size}")
                                }
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) }
                )
            }

            when (state.selectedTab) {
                GalleryTab.DISCOVER, GalleryTab.TRENDING -> {
                    DiscoverView(
                        state = state,
                        gridState = gridState,
                        onSearchChanged = { viewModel.onSearchQueryChanged(it) },
                        onSortChanged = { viewModel.setSortMode(it) },
                        onToggleNsfw = { viewModel.toggleNsfw() },
                        onRetry = { viewModel.performSearch(reset = true) },
                        onLoadMore = { viewModel.performSearch(reset = false) },
                        onCardClick = { viewModel.openCharacterDetail(it) },
                        onAuthorClick = { onNavigateToAuthor(it) },
                        onQuickImport = { viewModel.importCharacter(it) }
                    )
                }
                GalleryTab.FAVORITE_AUTHORS -> {
                    FavoriteAuthorsView(
                        favoriteAuthors = state.favoriteAuthors,
                        onAuthorClick = { onNavigateToAuthor(it) },
                        onRemoveFavorite = { viewModel.toggleFavoriteAuthor(it) },
                        onDiscoverClick = { viewModel.setSelectedTab(GalleryTab.DISCOVER) }
                    )
                }
            }
        }
    }

    // Character Detail Sheet Modal
    state.selectedCharacterForDetail?.let { character ->
        ChubCharacterDetailSheet(
            character = character,
            isAuthorFavorite = state.isSelectedAuthorFavorite,
            isImporting = state.importingCharacterId == character.id,
            onDismiss = { viewModel.closeCharacterDetail() },
            onImport = { viewModel.importCharacter(character) },
            onAuthorClick = { author ->
                viewModel.closeCharacterDetail()
                onNavigateToAuthor(author)
            },
            onToggleFavoriteAuthor = { author ->
                viewModel.toggleFavoriteAuthor(author)
            }
        )
    }

    if (showUrlImportDialog) {
        ChubUrlImportDialog(
            chubRepository = viewModel.repository,
            onDismiss = { showUrlImportDialog = false },
            onCharacterImported = { imported ->
                showUrlImportDialog = false
            }
        )
    }
}

@Composable
fun DiscoverView(
    state: ChubGalleryUiState,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onSearchChanged: (String) -> Unit,
    onSortChanged: (String) -> Unit,
    onToggleNsfw: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onCardClick: (com.example.data.model.ChubCharacterNode) -> Unit,
    onAuthorClick: (String) -> Unit,
    onQuickImport: (com.example.data.model.ChubCharacterNode) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Filters bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChanged,
                placeholder = { Text("Search characters on Chub.ai...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gallery_search_input")
            )

            // Filter chips row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sort: Default vs Trending
                FilterChip(
                    selected = state.sortMode == "default",
                    onClick = { onSortChanged("default") },
                    label = { Text("Default") },
                    leadingIcon = {
                        if (state.sortMode == "default") {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                )
                FilterChip(
                    selected = state.sortMode == "trending",
                    onClick = { onSortChanged("trending") },
                    label = { Text("Trending") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                // NSFW Toggle
                FilterChip(
                    selected = state.nsfwEnabled,
                    onClick = onToggleNsfw,
                    label = { Text(if (state.nsfwEnabled) "NSFW On" else "Safe") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (state.nsfwEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }
        }

        // Content / Character Grid
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.isLoading && state.characters.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Loading characters from Chub.ai...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (state.errorMessage != null && state.characters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    state = gridState,
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.characters, key = { "${it.id}_${it.fullPath}" }) { char ->
                        ChubCharacterCard(
                            character = char,
                            isImporting = state.importingCharacterId == char.id,
                            onClick = { onCardClick(char) },
                            onAuthorClick = onAuthorClick,
                            onQuickImport = { onQuickImport(char) }
                        )
                    }

                    // Load More footer item
                    if (state.hasMorePages) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.isLoadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                } else {
                                    OutlinedButton(
                                        onClick = onLoadMore,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(0.6f)
                                    ) {
                                        Icon(Icons.Default.ExpandMore, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Load More Characters")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FavoriteAuthorsView(
    favoriteAuthors: List<FavoriteAuthor>,
    onAuthorClick: (String) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onDiscoverClick: () -> Unit
) {
    if (favoriteAuthors.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Text(
                    text = "No Favorite Authors Yet",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Discover talented character creators on Chub.ai and click the heart icon to save them here for instant access anytime!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onDiscoverClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Explore, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Explore Characters")
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(favoriteAuthors, key = { it.username }) { author ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAuthorClick(author.username) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Author Avatar
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = author.username.take(2).uppercase(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "@${author.username}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Favorite Creator",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        // Remove from favorites button
                        IconButton(
                            onClick = { onRemoveFavorite(author.username) }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Remove Favorite",
                                tint = Color(0xFFE91E63)
                            )
                        }
                    }
                }
            }
        }
    }
}
