package com.docukal.app

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.docukal.app.data.local.DocumentEntity
import com.docukal.app.data.local.ImportantDateEntity
import com.docukal.app.data.local.SettingsEntity
import com.docukal.app.data.local.WarrantyEntity
import com.docukal.app.billing.AdManager
import com.docukal.app.billing.BillingManager
import com.docukal.app.navigation.Routes
import com.docukal.app.notifications.ReminderKind
import com.docukal.app.ui.theme.DocuKalTheme
import com.docukal.app.utils.BackupUtils
import com.docukal.app.utils.ExpiryStatus
import com.docukal.app.utils.daysUntil
import com.docukal.app.utils.expiryStatus
import com.docukal.app.utils.formatDate
import com.docukal.app.utils.startOfDay
import com.docukal.app.ui.BackupControls
import com.docukal.app.ui.DocTypeBadge
import com.docukal.app.ui.ShareDocumentButton
import com.docukal.app.ui.DocumentDetailScreen
import com.docukal.app.ui.DocumentEditor
import com.docukal.app.utils.DocKind
import com.docukal.app.viewmodel.MainViewModel
import java.util.Calendar

/** Reminder lead times offered throughout the app, matching the product requirement. */
private val REMINDER_OPTIONS = listOf(1, 3, 7, 15, 30, 60, 90)

fun showSaveAdIfNeeded(context: android.content.Context, premium: Boolean, onFinished: () -> Unit) {
    if (premium) {
        onFinished()
        return
    }
    val activity = context as? Activity
    if (activity == null) {
        onFinished()
        return
    }
    AdManager.show(activity, onFinished)
}

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_KIND = "open_kind"
        const val EXTRA_OPEN_ID = "open_id"
    }

    /** Holds the most recent deep-link request from a tapped reminder notification. */
    private var pendingOpen = mutableStateOf<Pair<ReminderKind, Long>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOpen.value = extractDeepLink(intent)
        AdManager.initialize(this)
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            val darkTheme = when (settings?.theme) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }
            DocuKalTheme(darkTheme) {
                App(vm, pendingOpen) { pendingOpen.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpen.value = extractDeepLink(intent)
    }

    private fun extractDeepLink(intent: Intent?): Pair<ReminderKind, Long>? {
        val kindName = intent?.getStringExtra(EXTRA_OPEN_KIND) ?: return null
        val id = intent.getLongExtra(EXTRA_OPEN_ID, -1L)
        if (id < 0) return null
        return runCatching { ReminderKind.valueOf(kindName) }.getOrNull()?.let { it to id }
    }
}

@Composable
fun App(
    vm: MainViewModel,
    pendingOpen: androidx.compose.runtime.MutableState<Pair<ReminderKind, Long>?>,
    onConsumedDeepLink: () -> Unit
) {
    val context = LocalContext.current
    val billingManager = remember { BillingManager(context) }
    val billingPremium by billingManager.premium
    val premium = billingPremium
    LaunchedEffect(Unit) { billingManager.startConnection() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { billingManager.refreshPurchases() }
    DisposableEffect(billingManager) {
        onDispose { billingManager.endConnection() }
    }
    val nav = rememberNavController()
    var notificationRequested by remember { mutableStateOf(false) }

    if (!notificationRequested && Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            notificationRequested = true
        }
    }

    val deepLink = pendingOpen.value
    LaunchedEffect(deepLink) {
        val (kind, id) = deepLink ?: return@LaunchedEffect
        if (kind == ReminderKind.DOCUMENT) {
            nav.navigate(Routes.documentDetail(id))
        } else {
            // Warranties and important dates don't have a dedicated detail screen yet;
            // land the user on the list where the record lives.
            nav.navigate(if (kind == ReminderKind.WARRANTY) Routes.WARRANTIES else Routes.REMINDERS)
        }
        onConsumedDeepLink()
    }

    Scaffold(bottomBar = { BottomBar(nav) }) { padding ->
        NavHost(nav, startDestination = Routes.HOME, Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(vm, premium) { nav.navigate(Routes.ADD_DOCUMENT) }
            }
            composable(Routes.DOCUMENTS) {
                DocumentsScreen(
                    vm = vm,
                    onAdd = { nav.navigate(Routes.ADD_DOCUMENT) },
                    onOpen = { doc -> nav.navigate(Routes.documentDetail(doc.id)) }
                )
            }
            composable(Routes.DOCUMENT_DETAIL) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                DocumentDetailScreen(vm, id) { nav.popBackStack() }
            }
            composable(Routes.WARRANTIES) { WarrantiesScreen(vm, premium) }
            composable(Routes.REMINDERS) { RemindersScreen(vm, premium) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, billingManager, premium) }
            composable(Routes.ADD_DOCUMENT) { DocumentEditor(vm, premium) { nav.popBackStack() } }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController) {
    val currentRoute by nav.currentBackStackEntryAsState()
    val route = currentRoute?.destination?.route
    val entries = listOf(
        Triple("Home", Icons.Default.Home, Routes.HOME),
        Triple("Documents", Icons.Default.Description, Routes.DOCUMENTS),
        Triple("Warranties", Icons.Default.Shield, Routes.WARRANTIES),
        Triple("Reminders", Icons.Default.Notifications, Routes.REMINDERS),
        Triple("Settings", Icons.Default.Settings, Routes.SETTINGS)
    )
    NavigationBar {
        entries.forEach { (label, icon, destination) ->
            NavigationBarItem(
                selected = route == destination,
                onClick = {
                    nav.navigate(destination) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icon, label) },
                // Keep the navigation compact: the icon's content description still
                // makes every destination accessible to TalkBack users.
                alwaysShowLabel = false
            )
        }
    }
}

@Composable
fun Title(text: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val AccentPurple = Color(0xFF7C4DFF)
private val AccentBlue = Color(0xFF2196F3)
private val AccentGreen = Color(0xFF00A884)
private val AccentOrange = Color(0xFFFF8A00)
private val AccentPink = Color(0xFFE84393)

private fun statusContainer(status: ExpiryStatus): Color = when (status) {
    ExpiryStatus.EXPIRED -> Color(0xFFFFE1E1)
    ExpiryStatus.EXPIRING_SOON -> Color(0xFFFFE8C2)
    ExpiryStatus.ACTIVE -> Color(0xFFDDF7E8)
    ExpiryStatus.NO_EXPIRY -> Color(0xFFE5E7FF)
}

private fun statusTextColor(status: ExpiryStatus): Color = when (status) {
    ExpiryStatus.EXPIRED -> Color(0xFFB42318)
    ExpiryStatus.EXPIRING_SOON -> Color(0xFF9A5B00)
    ExpiryStatus.ACTIVE -> Color(0xFF087443)
    ExpiryStatus.NO_EXPIRY -> Color(0xFF5145CD)
}

@Composable
fun StatusPill(expiry: Long?, reminderDays: Int) {
    val status = expiryStatus(expiry, reminderDays)
    Surface(shape = RoundedCornerShape(50), color = statusContainer(status)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (status) {
                    ExpiryStatus.EXPIRED -> Icons.Default.Error
                    ExpiryStatus.EXPIRING_SOON -> Icons.Default.Warning
                    ExpiryStatus.ACTIVE -> Icons.Default.CheckCircle
                    ExpiryStatus.NO_EXPIRY -> Icons.Default.Shield
                },
                null, Modifier.size(15.dp), tint = statusTextColor(status)
            )
            Spacer(Modifier.width(5.dp))
            Text(statusText(expiry, reminderDays), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = statusTextColor(status))
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.13f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = accent.copy(alpha = 0.20f)) {
                Icon(icon, null, Modifier.padding(9.dp).size(22.dp), tint = accent)
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Empty(text: String) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
    ) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
                Icon(Icons.Default.CheckCircle, null, Modifier.padding(12.dp).size(36.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(text, fontWeight = FontWeight.Bold)
            Text("Everything is up to date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PremiumCard(premium: Boolean, onUpgrade: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(alpha = 0.15f))
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), color = AccentPurple.copy(alpha = 0.22f)) {
                    Icon(Icons.Default.CardGiftcard, null, Modifier.padding(11.dp).size(28.dp), tint = AccentPurple)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (premium) "DocuKal Premium" else "Upgrade to DocuKal Premium", fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (premium) "You're ad-free. Premium insights are unlocked."
                        else "Remove ads and unlock Premium Insights.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!premium) {
                Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Star, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Upgrade — Ad-free")
                }
            } else {
                Surface(shape = RoundedCornerShape(50), color = AccentPurple) {
                    Text("PREMIUM", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PremiumInsightsCard(documentCount: Int, warrantyCount: Int, dateCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Premium Insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text("Your personal overview without any extra setup.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InsightChip("$documentCount docs", Modifier.weight(1f))
                InsightChip("$warrantyCount warranties", Modifier.weight(1f))
                InsightChip("$dateCount dates", Modifier.weight(1f))
            }
        }
    }
}

// Compact single-line chip so the count and its label never split across two lines.
@Composable
private fun InsightChip(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun PremiumStatusCard(premium: Boolean) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(alpha = 0.15f))
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = AccentPurple.copy(alpha = 0.22f)) {
                Icon(Icons.Default.Star, null, Modifier.padding(11.dp).size(28.dp), tint = AccentPurple)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (premium) "Premium active" else "Free plan", fontWeight = FontWeight.ExtraBold)
                Text(
                    if (premium) "No ads + Premium Insights"
                    else "Ads appear after selected save actions. Upgrade in Settings to remove them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------

@Composable
fun HomeScreen(vm: MainViewModel, premium: Boolean, onAdd: () -> Unit) {
    val docs by vm.documents.collectAsState()
    val warranties by vm.warranties.collectAsState()
    val dates by vm.dates.collectAsState()
    val expiringSoon = docs.count { expiryStatus(it.expiryDate, it.reminderDays) == ExpiryStatus.EXPIRING_SOON }
    val expired = docs.count { expiryStatus(it.expiryDate, it.reminderDays) == ExpiryStatus.EXPIRED }
    val noExpiry = docs.count { it.expiryDate == null }
    val upcoming = docs.filter { expiryStatus(it.expiryDate, it.reminderDays) == ExpiryStatus.EXPIRING_SOON }.sortedBy { it.expiryDate }.take(5)

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("DocuKal", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                        Text("Your private document & expiry manager", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) {
                        Icon(Icons.Default.Folder, null, Modifier.padding(12.dp).size(44.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Documents", docs.size.toString(), Icons.Default.Description, Modifier.weight(1f), AccentBlue)
                StatCard("Expiring", expiringSoon.toString(), Icons.Default.Warning, Modifier.weight(1f), AccentOrange)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Expired", expired.toString(), Icons.Default.Error, Modifier.weight(1f), AccentPink)
                StatCard("No expiry", noExpiry.toString(), Icons.Default.Shield, Modifier.weight(1f), AccentGreen)
            }
        }
        if (premium) {
            item { PremiumInsightsCard(docs.size, warranties.size, dates.size) }
        }
        item { PremiumStatusCard(premium) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = AccentOrange.copy(alpha = 0.16f)) {
                    Icon(Icons.Default.Event, null, Modifier.padding(7.dp).size(20.dp), tint = AccentOrange)
                }
                Text("Expiring soon", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        if (upcoming.isEmpty()) item { Empty("Nothing expiring soon") } else items(upcoming) { DocumentCard(it, vm) }
        item {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Document", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Documents list
// ---------------------------------------------------------------------------

@Composable
fun DocumentCard(d: DocumentEntity, vm: MainViewModel, onOpen: (DocumentEntity) -> Unit = {}) {
    val status = expiryStatus(d.expiryDate, d.reminderDays)
    Card(
        Modifier.fillMaxWidth().clickable { onOpen(d) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val kind = DocKind.detect(d.name, d.type, d.number)
            DocTypeBadge(kind)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(d.name, fontWeight = FontWeight.ExtraBold)
                Text(if (kind == DocKind.OTHER) d.category else "${kind.label} \u2022 ${d.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusPill(d.expiryDate, d.reminderDays)
                if (d.attachmentUri != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(3.dp))
                        Text("Attachment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            // Favorite and Share are stacked, so the card's width and text alignment stay unchanged.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton({ vm.toggleFavorite(d) }, Modifier.size(40.dp)) { Icon(if (d.favorite) Icons.Default.Star else Icons.Default.StarBorder, "Favorite", Modifier.size(22.dp), tint = if (d.favorite) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant) }
                ShareDocumentButton(d, Modifier.size(40.dp), iconSize = 22.dp)
            }
        }
    }
}

fun statusText(expiry: Long?, reminderDays: Int): String = when (expiryStatus(expiry, reminderDays)) {
    ExpiryStatus.EXPIRED -> "EXPIRED • ${formatDate(expiry)}"
    ExpiryStatus.EXPIRING_SOON -> "EXPIRING SOON • ${daysUntil(expiry) ?: 0} days"
    ExpiryStatus.ACTIVE -> "ACTIVE • ${formatDate(expiry)}"
    ExpiryStatus.NO_EXPIRY -> "NO EXPIRY"
}

private enum class DocFilter(val label: String) { ALL("All"), ACTIVE("Active"), EXPIRING("Expiring"), EXPIRED("Expired"), NO_EXPIRY("No expiry"), FAVORITES("★") }
private enum class DocSort(val label: String) { RECENT("Recent"), NAME_ASC("Name A-Z"), NAME_DESC("Name Z-A"), EXPIRY_NEAREST("Expiry: Nearest"), EXPIRY_LATEST("Expiry: Latest") }

@Composable
fun DocumentsScreen(vm: MainViewModel, onAdd: () -> Unit, onOpen: (DocumentEntity) -> Unit) {
    val docs by vm.documents.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(DocFilter.ALL) }
    var sort by remember { mutableStateOf(DocSort.RECENT) }

    val filtered = docs
        .filter {
            query.isBlank() || listOf(it.name, it.type, it.number, it.category, it.notes).any { field ->
                field.contains(query, ignoreCase = true)
            }
        }
        .filter {
            when (filter) {
                DocFilter.ALL -> true
                DocFilter.ACTIVE -> expiryStatus(it.expiryDate, it.reminderDays) == ExpiryStatus.ACTIVE
                DocFilter.EXPIRING -> expiryStatus(it.expiryDate, it.reminderDays) == ExpiryStatus.EXPIRING_SOON
                DocFilter.EXPIRED -> expiryStatus(it.expiryDate, it.reminderDays) == ExpiryStatus.EXPIRED
                DocFilter.NO_EXPIRY -> it.expiryDate == null
                DocFilter.FAVORITES -> it.favorite
            }
        }
    val shown = when (sort) {
        DocSort.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
        DocSort.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
        DocSort.EXPIRY_NEAREST -> filtered.sortedBy { it.expiryDate ?: Long.MAX_VALUE }
        DocSort.EXPIRY_LATEST -> filtered.sortedByDescending { it.expiryDate ?: Long.MIN_VALUE }
        DocSort.RECENT -> filtered.sortedByDescending { it.updatedAt }
    }

    Scaffold(floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Title("Documents", "Search and manage all your important records")
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DocFilter.entries.forEach { f ->
                    FilterChip(filter == f, { filter = f }, leadingIcon = { if (filter == f) Icon(Icons.Default.FilterAlt, null, Modifier.size(16.dp)) }, label = { Text(f.label) })
                }
            }
            Spacer(Modifier.height(4.dp))
            AssistChip(
                onClick = {
                    val values = DocSort.entries
                    sort = values[(values.indexOf(sort) + 1) % values.size]
                },
                leadingIcon = { Icon(Icons.Default.Sort, null, Modifier.size(18.dp)) },
                label = { Text("Sort: ${sort.label}") }
            )
            Spacer(Modifier.height(4.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (shown.isEmpty()) {
                    item { Empty("No documents found") }
                } else {
                    items(shown) { DocumentCard(it, vm, onOpen) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Warranties
// ---------------------------------------------------------------------------

@Composable
fun WarrantiesScreen(vm: MainViewModel, premium: Boolean) {
    val warranties by vm.warranties.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Title("Warranties", "Keep purchase protection and invoices in one place")
        Spacer(Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.12f))) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, Modifier.size(24.dp), tint = AccentGreen)
                Spacer(Modifier.width(10.dp))
                Text("Track warranty dates before protection runs out", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(17.dp)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add Warranty")
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (warranties.isEmpty()) {
                item { Empty("No warranties added") }
            } else {
                items(warranties) { warranty -> WarrantyCard(warranty, vm) }
            }
        }
    }

    if (showAdd) {
        WarrantyDialog(vm, premium) { showAdd = false }
    }
}

@Composable
private fun WarrantyCard(warranty: WarrantyEntity, vm: MainViewModel) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(warranty.productName, fontWeight = FontWeight.Bold)
                val details = listOf(warranty.brand, warranty.modelNumber).filter { it.isNotBlank() }.joinToString(" • ")
                if (details.isNotBlank()) Text(details)
                StatusPill(warranty.endDate, warranty.reminderDays)
                if (warranty.invoiceUri != null) Text("Invoice attached", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { vm.deleteWarranty(warranty) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
fun WarrantyDialog(vm: MainViewModel, premium: Boolean, onClose: () -> Unit) {
    var product by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var end by remember { mutableStateOf<Long?>(null) }
    var price by remember { mutableStateOf("") }
    var reminderDays by remember { mutableStateOf(30) }
    var invoice by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            invoice = uri
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add Warranty") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(product, { product = it }, label = { Text("Product name *") }, singleLine = true)
                OutlinedTextField(brand, { brand = it }, label = { Text("Brand") }, singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Purchase price") }, singleLine = true)
                DateField(label = "Warranty end", value = end, onValue = { end = it })
                ReminderSelector(reminderDays) { reminderDays = it }
                OutlinedButton({ picker.launch(arrayOf("image/*", "application/pdf")) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AttachFile, null); Spacer(Modifier.width(8.dp)); Text(if (invoice == null) "Attach invoice" else "Invoice selected")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = product.isNotBlank(),
                onClick = {
                    vm.addWarranty(
                        WarrantyEntity(
                            productName = product,
                            brand = brand,
                            endDate = end,
                            price = price.toDoubleOrNull(),
                            reminderDays = reminderDays,
                            invoiceUri = invoice?.toString()
                        )
                    ) {
                        showSaveAdIfNeeded(context, premium) { onClose() }
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------------------
// Reminders / important dates
// ---------------------------------------------------------------------------

@Composable
fun RemindersScreen(vm: MainViewModel, premium: Boolean) {
    var showAdd by remember { mutableStateOf(false) }
    val docs by vm.documents.collectAsState()
    val dates by vm.dates.collectAsState()

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Title("Reminders", "Never miss an important date")
        Spacer(Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = AccentBlue.copy(alpha = 0.12f))) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, null, Modifier.size(24.dp), tint = AccentBlue)
                Spacer(Modifier.width(10.dp))
                Text("Upcoming expiries and important dates in one place", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Button({ showAdd = true }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(17.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("Add Important Date", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            docs.filter { it.expiryDate != null }.sortedBy { it.expiryDate }.take(20).forEach { d ->
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(14.dp), color = AccentOrange.copy(alpha = 0.15f)) { Icon(Icons.Default.Event, null, Modifier.padding(9.dp).size(23.dp), tint = AccentOrange) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.name, fontWeight = FontWeight.Bold)
                                Text("${formatDate(d.expiryDate)} • ${if (d.reminderDays <= 0) "no reminder" else "${d.reminderDays} days before"}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            val upcomingDates = dates.filter { it.date >= startOfDay(System.currentTimeMillis()) }.take(20)
            upcomingDates.forEach { d ->
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(14.dp), color = AccentPurple.copy(alpha = 0.15f)) { Icon(Icons.Default.CalendarMonth, null, Modifier.padding(9.dp).size(23.dp), tint = AccentPurple) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.title, fontWeight = FontWeight.Bold)
                                Text("${formatDate(d.date)} • ${if (d.reminderDays <= 0) "no reminder" else "${d.reminderDays} days before"}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton({ vm.deleteDate(d) }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) ImportantDateDialog(vm, premium) { showAdd = false }
}

@Composable
fun ImportantDateDialog(vm: MainViewModel, premium: Boolean, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<Long?>(null) }
    var desc by remember { mutableStateOf("") }
    var reminderDays by remember { mutableStateOf(1) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add Important Date") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title *") })
                DateField("Date", date) { date = it }
                OutlinedTextField(desc, { desc = it }, label = { Text("Description") })
                ReminderSelector(reminderDays) { reminderDays = it }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && date != null,
                onClick = {
                    vm.addDate(ImportantDateEntity(title = title, date = date!!, description = desc, reminderDays = reminderDays)) {
                        showSaveAdIfNeeded(context, premium) { onClose() }
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClose) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

@Composable
fun SettingsScreen(vm: MainViewModel, billingManager: BillingManager, premium: Boolean) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Title("Settings", "Customize DocuKal and protect your data")

        Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("System", "Light", "Dark").forEach { value ->
                FilterChip(settings?.theme == value, { vm.saveSettings((settings ?: SettingsEntity()).copy(theme = value)) }, leadingIcon = { Icon(if (value == "Dark") Icons.Default.DarkMode else Icons.Default.LightMode, null, Modifier.size(17.dp)) }, label = { Text(value) })
            }
        }

        Text("Default reminder: ${settings?.defaultReminder ?: 30} days")
        OutlinedButton({
            val current = settings?.defaultReminder ?: 30
            val next = REMINDER_OPTIONS.getOrElse(REMINDER_OPTIONS.indexOf(current) + 1) { REMINDER_OPTIONS.first() }
            vm.saveSettings((settings ?: SettingsEntity()).copy(defaultReminder = next))
        }) { Icon(Icons.Default.Notifications, null); Spacer(Modifier.width(8.dp)); Text("Change default reminder") }

        HorizontalDivider()
        PremiumCard(premium) {
            (context as? Activity)?.let { act ->
                billingManager.launchPremiumPurchase(act) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }



        HorizontalDivider()
        Text("Data & Backup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        BackupControls(vm)
        OutlinedButton({ confirmClear = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.DeleteForever, null); Spacer(Modifier.width(8.dp)); Text("Clear All Data") }

        Text("DocuKal v1.1.0", style = MaterialTheme.typography.bodySmall)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all data?") },
            text = { Text("This permanently deletes all your documents, warranties and important dates from this device. Export a backup first if you may need them.") },
            confirmButton = { Button({ confirmClear = false; vm.clearAll() }) { Text("Delete everything") } },
            dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } }
        )
    }

}

// ---------------------------------------------------------------------------
// Document detail / edit / add
// ---------------------------------------------------------------------------

@Composable
fun DetailRow(label: String, value: String) {
    if (value.isNotBlank() && value != "—") {
        Column {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value)
        }
    }
}

@Composable
fun DateField(label: String, value: Long?, onClear: (() -> Unit)? = null, onValue: (Long) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = {
                val calendar = Calendar.getInstance()
                if (value != null) calendar.timeInMillis = value
                DatePickerDialog(
                    context,
                    { _, year, month, day -> calendar.set(year, month, day); onValue(calendar.timeInMillis) },
                    calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(15.dp)
        ) { Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text(if (value == null) label else "$label: ${formatDate(value)}", fontWeight = FontWeight.SemiBold) }
        if (value != null && onClear != null) {
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Clear $label") }
        }
    }
}

/** Chip row for the reminder lead times called out in the product requirement. */
@Composable
fun ReminderSelector(value: Int, onValue: (Int) -> Unit) {
    Column {
        Text("Remind me", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(value <= 0, { onValue(0) }, { Text("None") })
            REMINDER_OPTIONS.forEach { days ->
                FilterChip(value == days, { onValue(days) }, { Text("${days}d") })
            }
        }
    }
}
