
package ir.snapyarx.app

import android.os.Bundle
import android.net.Uri
import android.content.Intent
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Premium light palette: white surfaces, blue primary actions, red highlights
private val Bg = Color(0xFFF4F7FC)
private val SurfaceDark = Color(0xFFFFFFFF)
private val Surface2 = Color(0xFFEAF1FB)
private val Green = Color(0xFF1769E0)
private val Gold = Color(0xFFE34B5B)
private val Muted = Color(0xFF68778D)
private val White = Color(0xFF17243A)
private val Red = Color(0xFFD92D3F)

data class Entry(
    val id: Long,
    val role: String,
    val amount: Long,
    val cost: Long,
    val note: String,
    val date: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SnapYarApp() }
    }
}

@Composable
fun SnapYarApp() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("snapyar_local", 0)
    }

    var role by remember {
        mutableStateOf(prefs.getString("role", "driver") ?: "driver")
    }
    var entries by remember { mutableStateOf(loadEntries(prefs)) }
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingBackup by remember { mutableStateOf<Pair<String, List<Entry>>?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = createBackupJson(role, entries)
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("امکان ایجاد فایل وجود ندارد")
                Toast.makeText(context, "نسخه پشتیبان ذخیره شد", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "ذخیره پشتیبان ناموفق بود: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IllegalStateException("خواندن فایل ممکن نیست")
                pendingBackup = parseBackupJson(json)
                showRestoreConfirm = true
            } catch (e: Exception) {
                Toast.makeText(context, "فایل پشتیبان معتبر نیست: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun saveRole(value: String) {
        role = value
        prefs.edit().putString("role", value).apply()
    }

    fun addEntry(amount: Long, cost: Long, note: String) {
        val date = SimpleDateFormat(
            "yyyy/MM/dd - HH:mm",
            Locale.getDefault()
        ).format(Date())

        val item = Entry(
            System.currentTimeMillis(),
            role,
            amount,
            cost,
            note,
            date
        )

        entries = listOf(item) + entries
        saveEntries(prefs, entries)
    }

    val current = entries.filter { it.role == role }
    val totalIncome = if (role == "driver") {
        current.sumOf { it.amount }
    } else 0L

    val totalCosts = if (role == "driver") {
        current.sumOf { it.cost }
    } else {
        current.sumOf { it.amount }
    }

    val net = totalIncome - totalCosts

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Green,
                background = Bg,
                surface = SurfaceDark,
                onPrimary = Bg,
                onBackground = White,
                onSurface = White
            )
        ) {
            Scaffold(
                containerColor = Bg,
                topBar = {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Bg)
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .background(Green, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.DirectionsCar,
                                    null,
                                    tint = Bg
                                )
                            }

                            Spacer(Modifier.width(10.dp))

                            Column(Modifier.weight(1f)) {
                                Text(
                                    "اسپکس‌یار",
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = White
                                )
                                Text(
                                    "دستیار هوشمند سفرهای شما",
                                    fontSize = 11.sp,
                                    color = Muted
                                )
                            }

                            Icon(
                                Icons.Default.NotificationsNone,
                                null,
                                tint = White
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    SurfaceDark,
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(4.dp)
                        ) {
                            RoleChip(
                                "راننده",
                                role == "driver",
                                Modifier.weight(1f)
                            ) { saveRole("driver") }

                            RoleChip(
                                "مسافر",
                                role == "passenger",
                                Modifier.weight(1f)
                            ) { saveRole("passenger") }
                        }
                    }
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = SurfaceDark,
                        contentColor = Muted
                    ) {
                        val labels = listOf(
                            "خانه", "سفرها", "گزارش", "مسیریابی", "ابزارها", "پروفایل"
                        )
                        val icons = listOf(
                            Icons.Default.Home,
                            Icons.Default.Route,
                            Icons.Default.BarChart,
                            Icons.Default.Navigation,
                            Icons.Default.Construction,
                            Icons.Default.Person
                        )

                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = {
                                    Icon(icons[index], contentDescription = label)
                                },
                                label = { Text(label, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Green,
                                    selectedTextColor = Green,
                                    indicatorColor = Green.copy(alpha = .13f),
                                    unselectedIconColor = Muted,
                                    unselectedTextColor = Muted
                                )
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (tab == 0 || tab == 1) {
                        FloatingActionButton(
                            onClick = { showAdd = true },
                            containerColor = Green,
                            contentColor = Bg
                        ) {
                            Icon(Icons.Default.Add, "ثبت مورد")
                        }
                    }
                }
            ) { padding ->
                when (tab) {
                    0 -> HomeScreen(
                        role,
                        current.size,
                        totalIncome,
                        totalCosts,
                        net,
                        Modifier.padding(padding),
                        onAdd = { showAdd = true },
                        onTrips = { tab = 1 },
                        recent = current.take(3)
                    )

                    1 -> TripsScreen(
                        current,
                        role,
                        Modifier.padding(padding),
                        onAdd = { showAdd = true }
                    )

                    2 -> StatsScreen(
                        role,
                        current,
                        Modifier.padding(padding)
                    )

                    3 -> NavigatorScreen(Modifier.padding(padding))

                    4 -> ToolsScreen(role, Modifier.padding(padding), onOpenNavigator = { tab = 3 })

                    else -> ProfileScreen(
                        role,
                        Modifier.padding(padding),
                        onExportBackup = {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                            exportBackupLauncher.launch("snapyar-backup-$stamp.json")
                        },
                        onImportBackup = {
                            importBackupLauncher.launch(arrayOf("application/json", "text/json"))
                        }
                    )
                }
            }

            if (showAdd) {
                AddEntryDialog(
                    role,
                    onDismiss = { showAdd = false },
                    onSave = { a, c, n ->
                        addEntry(a, c, n)
                        showAdd = false
                    }
                )
            }

            if (showRestoreConfirm && pendingBackup != null) {
                AlertDialog(
                    onDismissRequest = {
                        showRestoreConfirm = false
                        pendingBackup = null
                    },
                    containerColor = SurfaceDark,
                    title = { Text("بازیابی نسخه پشتیبان", color = White) },
                    text = {
                        Text(
                            "اطلاعات فایل با اطلاعات فعلی ادغام می‌شود؛ رکوردهای فعلی حذف نخواهند شد. ادامه می‌دهید؟",
                            color = White
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val backup = pendingBackup
                            if (backup != null) {
                                val merged = (entries + backup.second).distinctBy { it.id }
                                val saved = prefs.edit()
                                    .putString("role", backup.first)
                                    .putString("entries", entriesToJson(merged))
                                    .commit()
                                if (saved) {
                                    role = backup.first
                                    entries = merged
                                    Toast.makeText(context, "بازیابی انجام شد؛ اطلاعات قبلی حفظ شدند", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "ذخیره اطلاعات بازیابی‌شده ناموفق بود", Toast.LENGTH_LONG).show()
                                }
                            }
                            showRestoreConfirm = false
                            pendingBackup = null
                        }) {
                            Text("ادامه و بازیابی", color = Green)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showRestoreConfirm = false
                            pendingBackup = null
                        }) {
                            Text("انصراف", color = Muted)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun RoleChip(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .background(
                if (selected) Green else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Bg else Muted,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun HomeScreen(
    role: String,
    count: Int,
    income: Long,
    costs: Long,
    net: Long,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
    onTrips: () -> Unit,
    recent: List<Entry>
) {
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (role == "driver") "داشبورد راننده" else "داشبورد مسافر",
                        color = White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold
                    )
                    Text("نمای کلی عملکرد و مدیریت سفرها", color = Muted, fontSize = 12.sp)
                }
                Box(
                    Modifier.size(44.dp).background(Gold.copy(alpha = .13f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoGraph, null, tint = Gold, modifier = Modifier.size(23.dp))
                }
            }
        }

        item {
            Box(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF1458C8), Color(0xFF2678E8), Color(0xFF4C91EF))),
                        RoundedCornerShape(26.dp)
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(38.dp).background(Color.White.copy(alpha = .12f), RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.DirectionsCar, null, tint = Gold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("SPEX YAR", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("هر سفر، یک حساب؛ هر روز، یک سود", color = White, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(
                        if (role == "driver") "سود خالص ثبت‌شده" else "مجموع هزینه سفرها",
                        color = Color.White.copy(alpha = .76f), fontSize = 12.sp
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "${money(if (role == "driver") net else costs)} تومان",
                        color = if (role == "driver" && net < 0) Red else White,
                        fontSize = 29.sp, fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(13.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, null, tint = Gold, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (role == "driver") "محاسبه بر اساس درآمد و هزینه‌های ثبت‌شده" else "بر اساس هزینه‌های ثبت‌شده شما",
                            color = Color.White.copy(alpha = .72f), fontSize = 10.sp
                        )
                    }
                }
            }
        }

        item {
            if (role == "driver") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("درآمد ثبت‌شده", money(income), Icons.Default.AccountBalanceWallet, Modifier.weight(1f), Green)
                    MetricCard("هزینه‌ها", money(costs), Icons.Default.Payments, Modifier.weight(1f), Red)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("سفرهای ثبت‌شده", count.toString(), Icons.Default.Route, Modifier.weight(1f), Green)
                    MetricCard("میانگین هزینه", money(if (count > 0) costs / count else 0), Icons.Default.Calculate, Modifier.weight(1f), Gold)
                }
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("مدیریت هوشمند سفر", color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("ثبت جزئیات، کنترل هزینه و بررسی عملکرد", color = Muted, fontSize = 11.sp)
                    }
                    Box(
                        Modifier.size(42.dp).background(Gold.copy(alpha = .13f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Insights, null, tint = Gold)
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("دسترسی سریع", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("سوابق سفر", color = Gold, fontSize = 12.sp, modifier = Modifier.clickable { onTrips() })
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(
                    if (role == "driver") "ثبت درآمد و هزینه" else "ثبت هزینه سفر",
                    Icons.Default.AddCircle, Modifier.weight(1f), onAdd
                )
                QuickAction("مشاهده سوابق", Icons.Default.History, Modifier.weight(1f), onTrips)
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("آخرین فعالیت‌ها", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${recent.size} مورد", color = Muted, fontSize = 11.sp)
            }
        }

        if (recent.isEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    EmptyState("هنوز موردی ثبت نشده است", "برای شروع، اولین سفر یا هزینه خود را ثبت کنید.")
                }
            }
        } else {
            items(recent) { EntryRow(it, role) }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Green
) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = Muted, fontSize = 11.sp)
            Text(
                value,
                color = White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun QuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Green)
            Spacer(Modifier.width(8.dp))
            Text(label, color = White, fontSize = 12.sp)
        }
    }
}

@Composable
fun TripsScreen(
    entries: List<Entry>,
    role: String,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "سوابق ثبت‌شده",
            color = White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "اطلاعات فقط روی همین گوشی نگهداری می‌شود",
            color = Muted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(14.dp))

        if (entries.isEmpty()) {
            EmptyState(
                "فهرست خالی است",
                "با دکمه افزودن، اولین مورد را ثبت کنید."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(entries) { EntryRow(it, role) }
            }
        }
    }
}

@Composable
fun EntryRow(entry: Entry, role: String) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(Surface2, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (role == "driver") Icons.Default.LocalTaxi
                    else Icons.Default.Route,
                    null,
                    tint = Green
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    entry.note.ifBlank {
                        if (role == "driver") "ثبت درآمد و هزینه"
                        else "هزینه سفر"
                    },
                    color = White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(entry.date, color = Muted, fontSize = 10.sp)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${money(entry.amount)} تومان",
                    color = Green,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                if (role == "driver" && entry.cost > 0) {
                    Text(
                        "هزینه: ${money(entry.cost)}",
                        color = Red,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/* صفحه گزارش آماری نسخه دوم */

@Composable
fun StatsScreen(
    role: String,
    entries: List<Entry>,
    modifier: Modifier = Modifier
) {
    var period by remember { mutableStateOf("ماه") }

    val now = Calendar.getInstance()

    val filtered = entries.filter { entry ->
        val date = parseEntryDate(entry.date) ?: return@filter false
        val itemCal = Calendar.getInstance().apply { time = date }

        when (period) {
            "روز" -> {
                itemCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                itemCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
            }

            "هفته" -> {
                val start = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -6)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                !date.before(start.time) && !date.after(Date())
            }

            "ماه" -> {
                itemCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                itemCal.get(Calendar.MONTH) == now.get(Calendar.MONTH)
            }

            else -> true
        }
    }

    val income = if (role == "driver") {
        filtered.sumOf { it.amount }
    } else 0L

    val costs = if (role == "driver") {
        filtered.sumOf { it.cost }
    } else {
        filtered.sumOf { it.amount }
    }

    val net = income - costs

    val chartItems = filtered
        .groupBy { it.date.substringBefore(" - ") }
        .toList()
        .sortedBy { it.first }
        .takeLast(7)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            "گزارش عملکرد",
            color = White,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "تحلیل درآمد و هزینه‌های شما",
            color = Muted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(18.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceDark, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("روز", "هفته", "ماه", "همه").forEach { item ->
                Box(
                    Modifier
                        .weight(1f)
                        .background(
                            if (period == item) Green else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { period = item }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        item,
                        color = if (period == item) Bg else Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "خلاصه مالی",
            color = White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(10.dp))

        if (role == "driver") {
            MetricCard(
                "درآمد این بازه",
                "${money(income)} تومان",
                Icons.Default.AccountBalanceWallet,
                Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            MetricCard(
                "هزینه‌های این بازه",
                "${money(costs)} تومان",
                Icons.Default.Payments,
                Modifier.fillMaxWidth(),
                Red
            )

            Spacer(Modifier.height(10.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Surface2),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.TrendingUp,
                            null,
                            tint = if (net >= 0) Green else Red
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("سود خالص", color = Muted)
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "${money(net)} تومان",
                        color = if (net >= 0) Green else Red,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "درآمد منهای هزینه‌ها",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            MetricCard(
                "مجموع هزینه سفر",
                "${money(costs)} تومان",
                Icons.Default.AccountBalanceWallet,
                Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                "تعداد موارد",
                filtered.size.toString(),
                Icons.Default.ReceiptLong,
                Modifier.weight(1f)
            )

            MetricCard(
                "میانگین مبلغ",
                money(
                    if (filtered.isNotEmpty()) {
                        if (role == "driver") income / filtered.size
                        else costs / filtered.size
                    } else 0L
                ),
                Icons.Default.Calculate,
                Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "نمودار عملکرد",
            color = White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "مبالغ به تفکیک روز",
            color = Muted,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendItem(
                        if (role == "driver") "درآمد" else "هزینه",
                        Green
                    )

                    if (role == "driver") {
                        LegendItem("هزینه", Red)
                    }
                }

                Spacer(Modifier.height(18.dp))

                if (chartItems.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(170.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "برای نمایش نمودار، اطلاعات ثبت کنید",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    val maxValue = chartItems.maxOf { (_, list) ->
                        val total = if (role == "driver") {
                            list.sumOf { it.amount } + list.sumOf { it.cost }
                        } else {
                            list.sumOf { it.amount }
                        }
                        total
                    }.coerceAtLeast(1L)

                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                    ) {
                        val chartHeight = size.height - 15f
                        val chartWidth = size.width
                        val step = chartWidth / chartItems.size
                        val barWidth = (step * 0.24f).coerceAtLeast(5f)

                        for (i in 0..3) {
                            val y = chartHeight * i / 3f
                            drawLine(
                                color = Muted.copy(alpha = 0.18f),
                                start = Offset(0f, y),
                                end = Offset(chartWidth, y),
                                strokeWidth = 1f
                            )
                        }

                        chartItems.forEachIndexed { index, pair ->
                            val list = pair.second
                            val dayIncome = if (role == "driver") {
                                list.sumOf { it.amount }
                            } else {
                                list.sumOf { it.amount }
                            }

                            val dayCost = if (role == "driver") {
                                list.sumOf { it.cost }
                            } else 0L

                            val center = step * index + step / 2f

                            val incomeHeight =
                                (dayIncome.toFloat() / maxValue.toFloat()) * chartHeight

                            val costHeight =
                                (dayCost.toFloat() / maxValue.toFloat()) * chartHeight

                            drawRect(
                                color = Green,
                                topLeft = Offset(
                                    center - barWidth - 2f,
                                    chartHeight - incomeHeight
                                ),
                                size = Size(
                                    barWidth,
                                    incomeHeight
                                )
                            )

                            if (role == "driver") {
                                drawRect(
                                    color = Red,
                                    topLeft = Offset(
                                        center + 2f,
                                        chartHeight - costHeight
                                    ),
                                    size = Size(
                                        barWidth,
                                        costHeight
                                    )
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        chartItems.forEach { pair ->
                            Text(
                                pair.first.takeLast(5),
                                color = Muted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "جزئیات این بازه",
            color = White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "تعداد ثبت‌ها: ${filtered.size}",
            color = Muted,
            fontSize = 12.sp
        )

        Text(
            "گزارش بر اساس اطلاعات ثبت‌شده در برنامه محاسبه می‌شود.",
            color = Muted,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(5.dp))
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

fun parseEntryDate(value: String): Date? {
    return try {
        SimpleDateFormat(
            "yyyy/MM/dd - HH:mm",
            Locale.getDefault()
        ).parse(value)
    } catch (_: Exception) {
        null
    }
}


@Composable
@Composable
fun NavigatorScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mapView by remember { mutableStateOf<org.osmdroid.views.MapView?>(null) }
    var origin by remember { mutableStateOf("تهران") }
    var destination by remember { mutableStateOf("") }
    var routeStatus by remember { mutableStateOf("نقشه در حال آماده‌سازی است…") }

    Box(modifier.fillMaxSize().background(Bg)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
                org.osmdroid.views.MapView(ctx).apply {
                    setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(true)
                    controller.setZoom(5.5)
                    controller.setCenter(org.osmdroid.util.GeoPoint(35.7, 51.4))
                    setUseDataConnection(true)
                    mapView = this
                }
            },
            update = { view -> mapView = view }
        )

        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("مسیریابی اسپکس‌یار", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedTextField(
                    value = origin,
                    onValueChange = { origin = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("مبدأ") },
                    placeholder = { Text("نام شهر یا نشانی مبدأ") }
                )
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("مقصد") },
                    placeholder = { Text("نام شهر یا نشانی مقصد") }
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = destination.isNotBlank(),
                    onClick = {
                        val mv = mapView
                        if (mv == null) {
                            routeStatus = "نقشه هنوز آماده نشده است."
                        } else {
                            scope.launch {
                                routeStatus = "در حال جست‌وجوی مبدأ و مقصد…"
                                try {
                                    val start = geocodeSpexPlace(origin)
                                    val end = geocodeSpexPlace(destination)
                                    routeStatus = "در حال محاسبه مسیر…"
                                    val points = fetchSpexRoute(start, end)
                                    mv.overlays.removeAll { it is org.osmdroid.views.overlay.Polyline || it is org.osmdroid.views.overlay.Marker }
                                    val line = org.osmdroid.views.overlay.Polyline(mv).apply {
                                        setPoints(points)
                                        outlinePaint.color = android.graphics.Color.rgb(23, 105, 224)
                                        outlinePaint.strokeWidth = 10f
                                    }
                                    mv.overlays.add(line)
                                    val startMarker = org.osmdroid.views.overlay.Marker(mv).apply {
                                        position = start
                                        title = "مبدأ"
                                    }
                                    val endMarker = org.osmdroid.views.overlay.Marker(mv).apply {
                                        position = end
                                        title = "مقصد"
                                    }
                                    mv.overlays.add(startMarker)
                                    mv.overlays.add(endMarker)
                                    mv.controller.setCenter(start)
                                    mv.zoomToBoundingBox(line.bounds, true, 72)
                                    mv.invalidate()
                                    routeStatus = "مسیر با موفقیت روی نقشه نمایش داده شد."
                                } catch (e: Exception) {
                                    routeStatus = "خطا در یافتن مسیر: ${e.message ?: "اتصال اینترنت را بررسی کنید"}"
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("نمایش مسیر")
                }
                Text(routeStatus, color = Muted, fontSize = 11.sp)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledIconButton(
                onClick = { mapView?.controller?.zoomIn() },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = SurfaceDark, contentColor = Green)
            ) { Icon(Icons.Default.Add, contentDescription = "بزرگ‌نمایی") }
            FilledIconButton(
                onClick = { mapView?.controller?.zoomOut() },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = SurfaceDark, contentColor = Green)
            ) { Icon(Icons.Default.Remove, contentDescription = "کوچک‌نمایی") }
        }
    }

    DisposableEffect(mapView) {
        mapView?.onResume()
        onDispose {
            mapView?.onPause()
            mapView?.onDetach()
        }
    }
}

private suspend fun geocodeSpexPlace(query: String): org.osmdroid.util.GeoPoint =
    withContext(kotlinx.coroutines.Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val connection = java.net.URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=$encoded")
            .openConnection() as java.net.HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "SPEX-YAR-Android/1.0")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val results = org.json.JSONArray(body)
            if (results.length() == 0) throw IllegalArgumentException("مکان «$query» پیدا نشد")
            val item = results.getJSONObject(0)
            org.osmdroid.util.GeoPoint(item.getDouble("lat"), item.getDouble("lon"))
        } finally {
            connection.disconnect()
        }
    }

private suspend fun fetchSpexRoute(
    start: org.osmdroid.util.GeoPoint,
    end: org.osmdroid.util.GeoPoint
): List<org.osmdroid.util.GeoPoint> = withContext(kotlinx.coroutines.Dispatchers.IO) {
    val address = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"
    val connection = java.net.URL(address).openConnection() as java.net.HttpURLConnection
    try {
        connection.connectTimeout = 20000
        connection.readTimeout = 20000
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val routes = org.json.JSONObject(body).getJSONArray("routes")
        if (routes.length() == 0) throw IllegalStateException("برای این مسیر راهی پیدا نشد")
        val coordinates = routes.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        (0 until coordinates.length()).map { index ->
            val pair = coordinates.getJSONArray(index)
            org.osmdroid.util.GeoPoint(pair.getDouble(1), pair.getDouble(0))
        }
    } finally {
        connection.disconnect()
    }
}

@Composable
fun ToolsScreen(role: String, modifier: Modifier = Modifier, onOpenNavigator: () -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "ابزارهای کاربردی",
                color = White,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(5.dp))

            Text(
                "امکانات هوشمند برای مدیریت بهتر سفر",
                color = Muted,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(18.dp))

            val tools = if (role == "driver") {
                listOf(
                    "ماشین‌حساب سود سفر" to Icons.Default.Calculate,
                    "محاسبه هزینه سوخت" to Icons.Default.LocalGasStation,
                    "یادآور سرویس خودرو" to Icons.Default.Build,
                    "چک‌لیست پیش از حرکت" to Icons.Default.Checklist
                )
            } else {
                listOf(
                    "ماشین‌حساب هزینه سفر" to Icons.Default.Calculate,
                    "چک‌لیست وسایل سفر" to Icons.Default.Checklist,
                    "یادداشت مقصدها" to Icons.Default.Map,
                    "راهنمای ایمنی سفر" to Icons.Default.Security
                )
            }

            tools.forEach { (title, icon) ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceDark
                    ),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 17.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .background(Green.copy(alpha = 0.12f), RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, null, tint = Green)
                        }
                        Spacer(Modifier.width(13.dp))
                        Text(
                            title,
                            color = White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ChevronLeft, null, tint = Muted)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "ابزارهای بیشتر در نسخه‌های بعدی اضافه می‌شوند.",
                color = Muted,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
        }

        Card(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface2),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(Green.copy(alpha = 0.15f), RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Directions,
                            contentDescription = null,
                            tint = Green,
                            modifier = Modifier.size(27.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            "نقشه و مسیریاب",
                            color = White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "مقصد را وارد کنید و مسیر را روی نقشه دنبال کنید",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }

                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Green
                    )
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = onOpenNavigator,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Green,
                        contentColor = Bg
                    )
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "باز کردن نقشه و مسیریابی",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    role: String,
    modifier: Modifier = Modifier,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Text(
            "پروفایل",
            color = White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(18.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    "اسپکس‌یار",
                    color = White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text("نسخه ۲.۰.۰", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "حالت فعال: ${if (role == "driver") "راننده" else "مسافر"}",
                    color = Green
                )
                Spacer(Modifier.height(8.dp))
                Text("ذخیره‌سازی: محلی روی دستگاه", color = Muted)
            }
        }

        Spacer(Modifier.height(20.dp))

        Text("پشتیبان‌گیری و بازیابی", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "برای محافظت از درآمدها و هزینه‌های ثبت‌شده، یک فایل پشتیبان در حافظه گوشی ذخیره کنید.",
            color = Muted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onExportBackup,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Bg)
        ) {
            Icon(Icons.Default.SaveAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("ذخیره نسخه پشتیبان")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onImportBackup,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Green)
        ) {
            Icon(Icons.Default.Restore, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("بازیابی از فایل پشتیبان")
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "هنگام بازیابی، اطلاعات فایل با اطلاعات فعلی ادغام می‌شود و رکوردهای فعلی حذف نمی‌شوند. فایل پشتیبان را در جای امنی نگه دارید.",
            color = Muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "اطلاعات شما به سرور ارسال نمی‌شود. با حذف داده‌های برنامه ممکن است اطلاعات محلی نیز پاک شود.",
            color = Muted,
            fontSize = 12.sp
        )
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Inbox,
            null,
            tint = Muted,
            modifier = Modifier.size(42.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(title, color = White, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable
fun AddEntryDialog(
    role: String,
    onDismiss: () -> Unit,
    onSave: (Long, Long, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text(
                if (role == "driver") "ثبت درآمد و هزینه"
                else "ثبت هزینه سفر",
                color = White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit) },
                    label = {
                        Text(
                            if (role == "driver") "درآمد سفر (تومان)"
                            else "هزینه سفر (تومان)"
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true,
                    colors = fieldColors()
                )

                if (role == "driver") {
                    OutlinedTextField(
                        value = cost,
                        onValueChange = { cost = it.filter(Char::isDigit) },
                        label = { Text("هزینه مربوط به سفر (تومان)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true,
                        colors = fieldColors()
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("عنوان یا توضیح (اختیاری)") },
                    singleLine = true,
                    colors = fieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val a = amount.toLongOrNull() ?: 0L
                    val c = if (role == "driver") {
                        cost.toLongOrNull() ?: 0L
                    } else 0L

                    if (a > 0) onSave(a, c, note.trim())
                }
            ) {
                Text("ذخیره", color = Green)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = Muted)
            }
        }
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = White,
    unfocusedTextColor = White,
    focusedBorderColor = Green,
    unfocusedBorderColor = Muted,
    focusedLabelColor = Green,
    unfocusedLabelColor = Muted,
    cursorColor = Green
)

fun money(value: Long): String {
    return DecimalFormat("#,###").format(value)
}

fun loadEntries(
    prefs: android.content.SharedPreferences
): List<Entry> {
    return try {
        val array = JSONArray(
            prefs.getString("entries", "[]") ?: "[]"
        )

        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)

            Entry(
                o.getLong("id"),
                o.getString("role"),
                o.getLong("amount"),
                o.optLong("cost"),
                o.optString("note"),
                o.optString("date")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun entriesToJson(entries: List<Entry>): String {
    val array = JSONArray()
    entries.forEach { e ->
        array.put(
            JSONObject()
                .put("id", e.id)
                .put("role", e.role)
                .put("amount", e.amount)
                .put("cost", e.cost)
                .put("note", e.note)
                .put("date", e.date)
        )
    }
    return array.toString()
}

fun createBackupJson(role: String, entries: List<Entry>): String {
    return JSONObject()
        .put("app", "SnapYarX")
        .put("backup_version", 1)
        .put("created_at", System.currentTimeMillis())
        .put("role", role)
        .put("entries", JSONArray(entriesToJson(entries)))
        .toString(2)
}

fun parseBackupJson(json: String): Pair<String, List<Entry>> {
    val root = JSONObject(json)
    require(root.optString("app") == "SnapYarX") { "این فایل متعلق به اسپکس‌یار نیست" }
    require(root.optInt("backup_version", -1) == 1) { "نسخه فایل پشتیبان پشتیبانی نمی‌شود" }

    val restoredRole = root.optString("role", "driver")
    require(restoredRole == "driver" || restoredRole == "passenger") { "نوع حساب نامعتبر است" }

    val array = root.optJSONArray("entries") ?: throw IllegalArgumentException("فهرست اطلاعات در فایل وجود ندارد")
    val restoredEntries = (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        val entryRole = o.getString("role")
        require(entryRole == "driver" || entryRole == "passenger") { "یکی از رکوردها نوع نامعتبر دارد" }
        val amount = o.getLong("amount")
        val cost = o.optLong("cost", 0L)
        require(amount >= 0L && cost >= 0L) { "مبلغ نامعتبر در فایل وجود دارد" }
        Entry(
            id = o.getLong("id"),
            role = entryRole,
            amount = amount,
            cost = cost,
            note = o.optString("note", ""),
            date = o.optString("date", "")
        )
    }
    return restoredRole to restoredEntries
}

fun saveEntries(
    prefs: android.content.SharedPreferences,
    entries: List<Entry>
) {
    prefs.edit()
        .putString("entries", entriesToJson(entries))
        .apply()
}
