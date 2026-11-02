package uz.ilhomvich.galleryfeed.ui

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.ilhomvich.galleryfeed.data.GalleryDatabase
import uz.ilhomvich.galleryfeed.data.LocalMedia
import uz.ilhomvich.galleryfeed.data.LocalMediaScanner
import uz.ilhomvich.galleryfeed.data.MediaComment
import uz.ilhomvich.galleryfeed.data.MediaReaction
import uz.ilhomvich.galleryfeed.data.formatBytes
import uz.ilhomvich.galleryfeed.data.formatDate
import uz.ilhomvich.galleryfeed.data.formatDuration
import uz.ilhomvich.galleryfeed.data.hasAnyMediaPermission
import uz.ilhomvich.galleryfeed.data.mediaPermissionRequest
import java.util.Locale

private val Night = Color(0xFF080A12)
private val Purple = Color(0xFF9B7BFF)
private val Mint = Color(0xFF70E1C1)
private val Muted = Color(0xFF9BA3B8)

private enum class Destination(val title: String) {
    FEED("Feed"), EXPLORE("Explore"), SAVED("Saved"), PROFILE("Profile")
}

private enum class MediaFilter(val label: String) {
    ALL("Hammasi"), PHOTOS("Rasmlar"), VIDEOS("Videolar")
}

@Composable
fun GalleryFeedApp(database: GalleryDatabase) {
    val context = LocalContext.current
    val dao = remember(database) { database.galleryDao() }
    val reactions by dao.observeReactions().collectAsState(initial = emptyList())
    val commentCount by dao.observeCommentCount().collectAsState(initial = 0)
    val reactionMap = remember(reactions) { reactions.associateBy { it.mediaKey } }
    var hasPermission by remember { mutableStateOf(hasAnyMediaPermission(context)) }
    var media by remember { mutableStateOf<List<LocalMedia>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var destination by rememberSaveable { mutableStateOf(Destination.FEED) }
    var filter by rememberSaveable { mutableStateOf(MediaFilter.ALL) }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedMedia by remember { mutableStateOf<LocalMedia?>(null) }
    var selectedMediaKey by remember { mutableStateOf<String?>(null) }
    var showPermissionInfo by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasPermission = hasAnyMediaPermission(context)
        if (!hasPermission) showPermissionInfo = true
    }

    LaunchedEffect(hasPermission, refreshKey) {
        if (hasPermission) {
            loading = true
            media = runCatching { LocalMediaScanner.scan(context) }.getOrDefault(emptyList())
            loading = false
        } else {
            media = emptyList()
        }
    }

    val selectedList = remember(media, reactionMap, destination, filter, search) {
        val base = when (destination) {
            Destination.SAVED -> media.filter { reactionMap[it.key]?.bookmarked == true }
            else -> media
        }
        val byType = when (filter) {
            MediaFilter.ALL -> base
            MediaFilter.PHOTOS -> base.filterNot { it.isVideo }
            MediaFilter.VIDEOS -> base.filter { it.isVideo }
        }
        if (search.isBlank()) byType else byType.filter {
            it.displayName.contains(search.trim(), ignoreCase = true) ||
                it.relativePath.contains(search.trim(), ignoreCase = true) ||
                it.mimeType.contains(search.trim(), ignoreCase = true)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Purple,
        secondary = Mint,
        background = Night,
        surface = Color(0xFF151827),
        onBackground = Color(0xFFF5F4FA),
        onSurface = Color(0xFFF5F4FA),
    )) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF242044), Night, Night),
                        center = Offset(0f, 0f),
                        radius = 1100f,
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                TopHeader(
                    destination = destination,
                    onRefresh = { refreshKey++ },
                    onSearchClick = { destination = Destination.EXPLORE },
                )

                if (destination != Destination.PROFILE && hasPermission) {
                    FilterRow(filter = filter, onSelect = { filter = it })
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        !hasPermission -> PermissionScreen(
                            onGrant = { permissionLauncher.launch(mediaPermissionRequest()) },
                        )
                        loading -> LoadingScreen()
                        destination == Destination.PROFILE -> ProfileScreen(
                            mediaCount = media.size,
                            photoCount = media.count { !it.isVideo },
                            videoCount = media.count { it.isVideo },
                            savedCount = reactions.count { it.bookmarked },
                            likedCount = reactions.count { it.liked },
                            commentCount = commentCount,
                        )
                        destination == Destination.EXPLORE -> ExploreScreen(
                            media = selectedList,
                            search = search,
                            onSearch = { search = it },
                            onOpen = {
                                selectedMediaKey = it.key
                                destination = Destination.FEED
                            },
                        )
                        selectedList.isEmpty() -> EmptyMediaScreen(
                            isSaved = destination == Destination.SAVED,
                            onRefresh = { refreshKey++ },
                            onExplore = { destination = Destination.EXPLORE },
                        )
                        else -> key(destination, selectedMediaKey, selectedList.firstOrNull()?.key) {
                            FeedPager(
                                media = selectedList,
                                initialKey = selectedMediaKey,
                                dao = dao,
                                reactions = reactionMap,
                                onOpenComments = { selectedMedia = it },
                            )
                        }
                    }
                }

                BottomNavigation(destination = destination, onSelect = {
                    destination = it
                    if (it != Destination.EXPLORE) search = ""
                    if (it == Destination.FEED || it == Destination.SAVED) selectedMediaKey = null
                })
            }

            selectedMedia?.let { item ->
                CommentsSheet(
                    media = item,
                    dao = dao,
                    onDismiss = { selectedMedia = null },
                )
            }

            if (showPermissionInfo) {
                AlertDialog(
                    onDismissRequest = { showPermissionInfo = false },
                    containerColor = Color(0xFF1A1D2C),
                    title = { Text("Media ruxsati kerak") },
                    text = { Text("GalleryFeed telefondagi rasm va videolarni ko‘rsatishi uchun media ruxsati kerak. Android sozlamalarida tanlangan rasmlar ruxsatini ham berishing mumkin.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showPermissionInfo = false
                            permissionLauncher.launch(mediaPermissionRequest())
                        }) { Text("Qayta so‘rash") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPermissionInfo = false }) { Text("Yopish") }
                    },
                )
            }
        }
    }
}

@Composable
private fun TopHeader(
    destination: Destination,
    onRefresh: () -> Unit,
    onSearchClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Purple, Color(0xFF4D8DFF)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Collections, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("GalleryFeed", fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Text(
                text = when (destination) {
                    Destination.FEED -> "LOCAL MEDIA · FOR YOU"
                    Destination.EXPLORE -> "DISCOVER YOUR LIBRARY"
                    Destination.SAVED -> "YOUR COLLECTION"
                    Destination.PROFILE -> "LOCAL PROFILE"
                },
                fontSize = 9.sp,
                color = Muted,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        GlassIconButton(icon = Icons.Default.Refresh, description = "Yangilash", onClick = onRefresh)
        Spacer(Modifier.width(7.dp))
        GlassIconButton(icon = Icons.Default.Search, description = "Qidirish", onClick = onSearchClick)
    }
}

@Composable
private fun FilterRow(filter: MediaFilter, onSelect: (MediaFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MediaFilter.entries.forEach { item ->
            val selected = filter == item
            Surface(
                modifier = Modifier.clip(CircleShape).clickable { onSelect(item) },
                color = if (selected) Purple.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.045f),
                shape = CircleShape,
                border = BorderStroke(1.dp, if (selected) Purple.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f)),
            ) {
                Text(
                    item.label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (selected) Color.White else Muted,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun GlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(39.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(112.dp).clip(RoundedCornerShape(34.dp))
                .background(Brush.linearGradient(listOf(Purple.copy(alpha = 0.35f), Mint.copy(alpha = 0.15f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Collections, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Kutubxonangni och", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(9.dp))
        Text(
            "Telefoningdagi rasm va videolarni bitta chiroyli feed ichida ko‘rish uchun media ruxsatini ber.",
            color = Muted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(23.dp))
        Button(
            onClick = onGrant,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple),
            contentPadding = PaddingValues(horizontal = 25.dp, vertical = 14.dp),
        ) {
            Text("Media ruxsatini berish", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text("Fayllar o‘zgartirilmaydi va serverga yuborilmaydi.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun LoadingScreen() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        androidx.compose.material3.CircularProgressIndicator(color = Purple)
        Spacer(Modifier.height(14.dp))
        Text("Media fayllar topilmoqda…", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyMediaScreen(isSaved: Boolean, onRefresh: () -> Unit, onExplore: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (isSaved) Icons.Default.Bookmark else Icons.Default.Image,
            contentDescription = null,
            tint = Purple,
            modifier = Modifier.size(54.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(if (isSaved) "Hali saqlangan media yo‘q" else "Media topilmadi", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(
            if (isSaved) "Feed ichida bookmark tugmasini bosib, media fayllarni shu yerga yig‘." else "Telefon xotirasida ko‘rish mumkin bo‘lgan rasm yoki video topilmadi.",
            color = Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Yangilash") }
            if (isSaved) TextButton(onClick = onExplore) { Text("Explore") }
        }
    }
}

@Composable
private fun FeedPager(
    media: List<LocalMedia>,
    initialKey: String?,
    dao: uz.ilhomvich.galleryfeed.data.GalleryDao,
    reactions: Map<String, MediaReaction>,
    onOpenComments: (LocalMedia) -> Unit,
) {
    val start = media.indexOfFirst { it.key == initialKey }.takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(initialPage = start, pageCount = { media.size })
    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        key = { index -> media[index].key },
        beyondViewportPageCount = 1,
    ) { page ->
        val item = media[page]
        FeedMediaPage(
            media = item,
            isActive = pagerState.currentPage == page,
            reaction = reactions[item.key] ?: MediaReaction(mediaKey = item.key),
            dao = dao,
            onComments = { onOpenComments(item) },
        )
    }
}

@Composable
private fun FeedMediaPage(
    media: LocalMedia,
    isActive: Boolean,
    reaction: MediaReaction,
    dao: uz.ilhomvich.galleryfeed.data.GalleryDao,
    onComments: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val comments by dao.observeComments(media.key).collectAsState(initial = emptyList())
    var showInfo by remember { mutableStateOf(false) }
    var likePulse by remember { mutableStateOf(false) }
    val likeScale by animateFloatAsState(
        targetValue = if (likePulse) 1.28f else 1f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "like-pulse",
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF090B13))
            .pointerInput(media.key) {
                detectTapGestures(onDoubleTap = {
                    likePulse = true
                    scope.launch {
                        dao.saveReaction(reaction.copy(liked = true, updatedAt = System.currentTimeMillis()))
                    }
                })
            },
    ) {
        if (media.isVideo) {
            if (isActive) InlineVideoPlayer(media.uri)
            else MediaThumbnail(media = media, modifier = Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = media.uri,
                contentDescription = media.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.24f),
                    0.36f to Color.Transparent,
                    0.62f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.88f),
                ),
            ),
        )

        if (media.isVideo) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                color = Color.Black.copy(alpha = 0.44f),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Text(formatDuration(media.durationMs), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 18.dp, end = 78.dp, bottom = 22.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.displayName,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "${formatDate(media.dateAddedSeconds)}  ·  ${formatBytes(media.sizeBytes)}",
                    color = Color.White.copy(alpha = 0.77f),
                    fontSize = 11.sp,
                )
                if (media.relativePath.isNotBlank()) {
                    Text(
                        media.relativePath.trimEnd('/'),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetadataPill(if (media.isVideo) "VIDEO" else "PHOTO")
                    if (media.width > 0 && media.height > 0) MetadataPill("${media.width} × ${media.height}")
                    TextButton(onClick = { showInfo = true }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                        Text("Batafsil", color = Mint, fontSize = 11.sp)
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 13.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            ActionButton(
                icon = if (reaction.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (reaction.liked) "Liked" else "Like",
                tint = if (reaction.liked) Color(0xFFFF6688) else Color.White,
                modifier = Modifier.scale(likeScale),
            ) {
                likePulse = true
                scope.launch {
                    dao.saveReaction(reaction.copy(liked = !reaction.liked, updatedAt = System.currentTimeMillis()))
                }
            }
            ActionButton(Icons.Default.ChatBubbleOutline, "${comments.size}", Color.White, onClick = onComments)
            ActionButton(
                icon = Icons.Default.Bookmark,
                label = if (reaction.bookmarked) "Saved" else "Save",
                tint = if (reaction.bookmarked) Mint else Color.White,
            ) {
                scope.launch {
                    dao.saveReaction(reaction.copy(bookmarked = !reaction.bookmarked, updatedAt = System.currentTimeMillis()))
                }
            }
            ActionButton(Icons.Default.Share, "Share", Color.White) {
                runCatching {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = media.mimeType.ifBlank { "*/*" }
                        putExtra(Intent.EXTRA_STREAM, media.uri)
                        clipData = android.content.ClipData.newUri(context.contentResolver, media.displayName, media.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "GalleryFeed orqali ulashish"))
                }
            }
            ActionButton(Icons.Default.MoreHoriz, "Info", Color.White) { showInfo = true }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            containerColor = Color(0xFF1A1D2C),
            title = { Text("Media ma'lumotlari") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    InfoLine("Fayl", media.displayName)
                    InfoLine("Turi", media.mimeType)
                    InfoLine("Hajmi", formatBytes(media.sizeBytes))
                    InfoLine("O‘lchami", if (media.width > 0) "${media.width} × ${media.height}" else "Noma'lum")
                    InfoLine("Sana", formatDate(media.dateAddedSeconds))
                    InfoLine("Papka", media.relativePath.ifBlank { "Noma'lum" })
                    if (media.isVideo) InfoLine("Davomiyligi", formatDuration(media.durationMs))
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Yopish") } },
        )
    }
}

@Composable
private fun MetadataPill(text: String) {
    Surface(color = Color.White.copy(alpha = 0.12f), shape = CircleShape, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.11f))) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 9.sp, color = Color.White.copy(alpha = 0.86f))
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Surface(
            modifier = Modifier.size(46.dp).clickable(onClick = onClick),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.32f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 9.sp, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InlineVideoPlayer(uri: Uri) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(PlayerMediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun MediaThumbnail(media: LocalMedia, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(media.key) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(media.key) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(media.uri, Size(512, 512), null)
                } else {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, media.uri)
                        retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {
                        retriever.release()
                    }
                }
            }.getOrNull()
        }
    }
    Box(modifier.background(Color(0xFF171A29)), contentAlignment = Alignment.Center) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = media.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Box(Modifier.size(58.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Video", tint = Color.White, modifier = Modifier.size(36.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(
    media: LocalMedia,
    dao: uz.ilhomvich.galleryfeed.data.GalleryDao,
    onDismiss: () -> Unit,
) {
    val comments by dao.observeComments(media.key).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var draft by remember(media.key) { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151827),
        contentColor = Color.White,
        dragHandle = { Surface(Modifier.padding(vertical = 10.dp).size(width = 38.dp, height = 4.dp), color = Color.White.copy(alpha = 0.25f), shape = CircleShape) {} },
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f).padding(horizontal = 18.dp)) {
            Text("Izohlar", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(media.displayName, fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            if (comments.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Muted, modifier = Modifier.size(35.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Hali izoh yo‘q", color = Muted, fontSize = 13.sp)
                        Text("Birinchi izohni yoz.", color = Muted.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    lazyItems(comments, key = { it.id }) { comment ->
                        CommentRow(comment = comment, onDelete = { scope.launch { dao.deleteComment(comment.id) } })
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(500) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Lokal izoh yoz…", color = Muted, fontSize = 13.sp) },
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Purple,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Purple,
                    ),
                    maxLines = 3,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    modifier = Modifier.size(48.dp).clickable(enabled = draft.isNotBlank()) {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            scope.launch { dao.addComment(MediaComment(mediaKey = media.key, text = text)) }
                            draft = ""
                        }
                    },
                    shape = CircleShape,
                    color = if (draft.isNotBlank()) Purple else Color.White.copy(alpha = 0.08f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Send, contentDescription = "Yuborish", tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: MediaComment, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.045f)).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Purple, Mint))), contentAlignment = Alignment.Center) {
            Text("G", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Local user", fontSize = 11.sp, color = Mint, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(comment.text, fontSize = 13.sp, color = Color.White, lineHeight = 18.sp)
            Text(
                java.text.SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.getDefault()).format(java.util.Date(comment.createdAt)),
                color = Muted,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        TextButton(onClick = onDelete, contentPadding = PaddingValues(0.dp)) { Text("O‘chirish", color = Muted, fontSize = 10.sp) }
    }
}

@Composable
private fun ExploreScreen(
    media: List<LocalMedia>,
    search: String,
    onSearch: (String) -> Unit,
    onOpen: (LocalMedia) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Fayl yoki papka qidirish…", fontSize = 13.sp, color = Muted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Muted) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Purple,
                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            ),
        )
        if (media.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Mos media topilmadi", color = Muted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                gridItems(media, key = { it.key }) { item ->
                    Box(
                        modifier = Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF171A29)).clickable { onOpen(item) },
                    ) {
                        if (!item.isVideo) {
                            AsyncImage(item.uri, contentDescription = item.displayName, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            MediaThumbnail(media = item, modifier = Modifier.fillMaxSize())
                        }
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))).padding(6.dp)) {
                            Text(item.displayName, fontSize = 9.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    mediaCount: Int,
    photoCount: Int,
    videoCount: Int,
    savedCount: Int,
    likedCount: Int,
    commentCount: Int,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.size(90.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Purple, Color(0xFF477CEB), Mint))),
            contentAlignment = Alignment.Center,
        ) {
            Text("G", fontSize = 42.sp, color = Color.White, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(13.dp))
        Text("Local Profile", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Bu profil faqat shu qurilmada", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatCard("MEDIA", mediaCount.toString(), Modifier.weight(1f))
            StatCard("LIKES", likedCount.toString(), Modifier.weight(1f))
            StatCard("SAVED", savedCount.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatCard("PHOTOS", photoCount.toString(), Modifier.weight(1f))
            StatCard("VIDEOS", videoCount.toString(), Modifier.weight(1f))
            StatCard("COMMENTS", commentCount.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(22.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White.copy(alpha = 0.055f),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Offline by design", color = Mint, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(5.dp))
                Text("Like, izoh va bookmark ma'lumotlari GalleryFeed bazasida saqlanadi. Media fayllarning o‘zi joyidan ko‘chirilmaydi.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("GalleryFeed · v1.0.0", color = Muted.copy(alpha = 0.65f), fontSize = 10.sp)
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.055f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(Modifier.padding(vertical = 15.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 21.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 8.sp, color = Muted, letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BottomNavigation(destination: Destination, onSelect: (Destination) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xCC151827))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            .padding(horizontal = 5.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(Destination.FEED, Icons.Default.Home, destination, Modifier.weight(1f), onSelect)
        NavItem(Destination.EXPLORE, Icons.Default.Explore, destination, Modifier.weight(1f), onSelect)
        NavItem(Destination.SAVED, Icons.Default.Bookmark, destination, Modifier.weight(1f), onSelect)
        NavItem(Destination.PROFILE, Icons.Default.Person, destination, Modifier.weight(1f), onSelect)
    }
}

@Composable
private fun NavItem(
    destination: Destination,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Destination,
    modifier: Modifier = Modifier,
    onSelect: (Destination) -> Unit,
) {
    val active = destination == selected
    Column(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).clickable { onSelect(destination) }
            .background(if (active) Purple.copy(alpha = 0.22f) else Color.Transparent)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = destination.title, tint = if (active) Color.White else Muted, modifier = Modifier.size(19.dp))
        Text(destination.title, fontSize = 9.sp, color = if (active) Color.White else Muted, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.width(14.dp))
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
    }
}
