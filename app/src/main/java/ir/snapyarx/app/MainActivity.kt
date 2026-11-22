
package ir.snapyarx.app

import android.os.Bundle
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF0B100F)
private val SurfaceDark = Color(0xFF151D1A)
private val Surface2 = Color(0xFF1D2924)
private val Green = Color(0xFF19C784)
private val Muted = Color(0xFF9BAAA2)
private val White = Color(0xFFF4F8F5)
private val Red = Color(0xFFFF6B6B)

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
        MapLibre.getInstance(this)
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
                                    "اسنپ‌یار X",
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
                            "خانه", "سفرها", "گزارش", "ابزارها", "پروفایل"
                        )
                        val icons = listOf(
                            Icons.Default.Home,
                            Icons.Default.Route,
                            Icons.Default.BarChart,
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

                    3 -> ToolsScreen(role, Modifier.padding(padding))

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
        modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))

            Text(
                if (role == "driver") "داشبورد راننده" else "داشبورد مسافر",
                color = White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text("خلاصه عملکرد شما", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))

            if (role == "driver") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(
                        "درآمد ثبت‌شده",
                        money(income),
                        Icons.Default.AccountBalanceWallet,
                        Modifier.weight(1f)
                    )
                    MetricCard(
                        "هزینه‌ها",
                        money(costs),
                        Icons.Default.Payments,
                        Modifier.weight(1f),
                        Red
                    )
                }

                Spacer(Modifier.height(10.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Surface2
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Text("سود خالص", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "${money(net)} تومان",
                            color = if (net >= 0) Green else Red,
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "بر اساس اطلاعاتی که ثبت کرده‌اید",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Surface2
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("مجموع هزینه سفرها", color = Muted)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "${money(costs)} تومان",
                            color = Green,
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "تعداد سفرهای ثبت‌شده: $count",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(
                        "سفرهای ثبت‌شده",
                        count.toString(),
                        Icons.Default.Route,
                        Modifier.weight(1f)
                    )
                    MetricCard(
                        "میانگین هزینه",
                        money(if (count > 0) costs / count else 0),
                        Icons.Default.Calculate,
                        Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "دسترسی سریع",
                    color = White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "مشاهده همه",
                    color = Green,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onTrips() }
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(
                    if (role == "driver") "ثبت درآمد و هزینه" else "ثبت هزینه سفر",
                    Icons.Default.Add,
                    Modifier.weight(1f),
                    onAdd
                )
                QuickAction(
                    "مشاهده سوابق",
                    Icons.Default.Insights,
                    Modifier.weight(1f),
                    onTrips
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("آخرین فعالیت‌ها", color = White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }

        if (recent.isEmpty()) {
            item {
                EmptyState(
                    "هنوز موردی ثبت نشده است",
                    "برای شروع، اولین سفر یا هزینه خود را ثبت کنید."
                )
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
        Spacer(Modifier.height(12.dp))
        Text(
            "نقشه و سفرها",
            color = White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "نمایش نقشه نشان و سوابق ثبت‌شده",
            color = Muted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(18.dp)
        ) {
            NeshanMapScreen(Modifier.fillMaxSize())
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "سوابق ثبت‌شده",
            color = White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "اطلاعات فقط روی همین گوشی نگهداری می‌شود",
            color = Muted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))

        if (entries.isEmpty()) {
            EmptyState(
                "فهرست خالی است",
                "با دکمه افزودن، اولین مورد را ثبت کنید."
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(entries) { EntryRow(it, role) }
            }
        }
    }
}

@Composable
fun NeshanMapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = (context as ComponentActivity).lifecycle
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                map.setStyle(
                    Style.Builder().fromUri(
                        "https://static.neshan.org/sdk/maplibre/styles/light.json"
                    )
                )
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(35.6892, 51.3890))
                    .zoom(11.0)
                    .build()
            }
        }
    }

    DisposableEffect(mapView, lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                mapView.onStart()
            }

            override fun onResume(owner: LifecycleOwner) {
                mapView.onResume()
            }

            override fun onPause(owner: LifecycleOwner) {
                mapView.onPause()
            }

            override fun onStop(owner: LifecycleOwner) {
                mapView.onStop()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                mapView.onDestroy()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
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
fun ToolsScreen(role: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Text(
            "ابزارهای کاربردی",
            color = White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(14.dp))

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
                shape = RoundedCornerShape(15.dp)
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, tint = Green)
                    Spacer(Modifier.width(12.dp))
                    Text(title, color = White)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ChevronLeft, null, tint = Muted)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "ابزارهای بیشتر در نسخه‌های بعدی اضافه می‌شوند.",
            color = Muted,
            fontSize = 11.sp
        )
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
                    "اسنپ‌یار X",
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
    require(root.optString("app") == "SnapYarX") { "این فایل متعلق به اسنپ‌یار X نیست" }
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
