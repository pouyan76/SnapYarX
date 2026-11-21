package ir.snapyarx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
        setContent { SnapYarApp() }
    }
}

@Composable
fun SnapYarApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("snapyar_local", 0) }
    var role by remember { mutableStateOf(prefs.getString("role", "driver") ?: "driver") }
    var entries by remember { mutableStateOf(loadEntries(prefs)) }
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }

    fun saveRole(value: String) {
        role = value
        prefs.edit().putString("role", value).apply()
    }
    fun addEntry(amount: Long, cost: Long, note: String) {
        val date = SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.getDefault()).format(Date())
        val item = Entry(System.currentTimeMillis(), role, amount, cost, note, date)
        entries = listOf(item) + entries
        saveEntries(prefs, entries)
    }

    val current = entries.filter { it.role == role }
    val totalIncome = if (role == "driver") current.sumOf { it.amount } else 0L
    val totalCosts = if (role == "driver") current.sumOf { it.cost } else current.sumOf { it.amount }
    val net = totalIncome - totalCosts

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Green, background = Bg, surface = SurfaceDark,
                onPrimary = Bg, onBackground = White, onSurface = White
            )
        ) {
            Scaffold(
                containerColor = Bg,
                topBar = {
                    Column(
                        Modifier.fillMaxWidth().background(Bg).padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(42.dp).background(Green, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.DirectionsCar, null, tint = Bg) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("اسنپ‌یار X", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = White)
                                Text("دستیار هوشمند سفرهای شما", fontSize = 11.sp, color = Muted)
                            }
                            Icon(Icons.Default.NotificationsNone, null, tint = White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(
                            Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(16.dp)).padding(4.dp)
                        ) {
                            RoleChip("راننده", role == "driver", Modifier.weight(1f)) { saveRole("driver") }
                            RoleChip("مسافر", role == "passenger", Modifier.weight(1f)) { saveRole("passenger") }
                        }
                    }
                },
                bottomBar = {
                    NavigationBar(containerColor = SurfaceDark, contentColor = Muted) {
                        val labels = listOf("خانه", "سفرها", "گزارش", "ابزارها", "پروفایل")
                        val icons = listOf(Icons.Default.Home, Icons.Default.Route, Icons.Default.BarChart,
                            Icons.Default.Construction, Icons.Default.Person)
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(icons[index], contentDescription = label) },
                                label = { Text(label, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Green, selectedTextColor = Green,
                                    indicatorColor = Green.copy(alpha = .13f),
                                    unselectedIconColor = Muted, unselectedTextColor = Muted
                                )
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (tab == 0 || tab == 1) {
                        FloatingActionButton(onClick = { showAdd = true }, containerColor = Green, contentColor = Bg) {
                            Icon(Icons.Default.Add, contentDescription = "ثبت مورد")
                        }
                    }
                }
            ) { padding ->
                when (tab) {
                    0 -> HomeScreen(role, current.size, totalIncome, totalCosts, net,
                        Modifier.padding(padding), onAdd = { showAdd = true },
                        onTrips = { tab = 1 }, recent = current.take(3))
                    1 -> TripsScreen(current, role, Modifier.padding(padding), onAdd = { showAdd = true })
                    2 -> StatsScreen(role, current.size, totalIncome, totalCosts, net, Modifier.padding(padding))
                    3 -> ToolsScreen(role, Modifier.padding(padding))
                    else -> ProfileScreen(role, Modifier.padding(padding))
                }
            }
            if (showAdd) {
                AddEntryDialog(role, onDismiss = { showAdd = false }, onSave = { a, c, n ->
                    addEntry(a, c, n); showAdd = false
                })
            }
        }
    }
}

@Composable
fun RoleChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(if (selected) Green else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { onClick() }.padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Bg else Muted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun HomeScreen(role: String, count: Int, income: Long, costs: Long, net: Long,
               modifier: Modifier = Modifier, onAdd: () -> Unit, onTrips: () -> Unit, recent: List<Entry>) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(if (role == "driver") "داشبورد راننده" else "داشبورد مسافر", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("خلاصه عملکرد شما", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            if (role == "driver") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("درآمد ثبت‌شده", money(income), Icons.Default.AccountBalanceWallet, Modifier.weight(1f))
                    MetricCard("هزینه‌ها", money(costs), Icons.Default.Payments, Modifier.weight(1f), Red)
                }
                Spacer(Modifier.height(10.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Surface2), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("سود خالص", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(5.dp))
                        Text("${money(net)} تومان", color = if (net >= 0) Green else Red, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("بر اساس اطلاعاتی که ثبت کرده‌اید", color = Muted, fontSize = 11.sp)
                    }
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = Surface2), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("مجموع هزینه سفرها", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(5.dp))
                        Text("${money(costs)} تومان", color = Green, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("تعداد سفرهای ثبت‌شده: $count", color = Muted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("سفرهای ثبت‌شده", count.toString(), Icons.Default.Route, Modifier.weight(1f))
                    MetricCard("میانگین هزینه", money(if (count > 0) costs / count else 0), Icons.Default.Calculate, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("دسترسی سریع", color = White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("مشاهده همه", color = Green, fontSize = 12.sp, modifier = Modifier.clickable { onTrips() })
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(if (role == "driver") "ثبت درآمد و هزینه" else "ثبت هزینه سفر", Icons.Default.Add, Modifier.weight(1f), onAdd)
                QuickAction("گزارش عملکرد", Icons.Default.Insights, Modifier.weight(1f), onTrips)
            }
            Spacer(Modifier.height(20.dp))
            Text("آخرین فعالیت‌ها", color = White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        if (recent.isEmpty()) {
            item { EmptyState("هنوز موردی ثبت نشده است", "برای شروع، اولین سفر یا هزینه خود را ثبت کنید.") }
        } else {
            items(recent) { EntryRow(it, role) }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
               modifier: Modifier = Modifier, tint: Color = Green) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = Muted, fontSize = 11.sp)
            Text(value, color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
        }
    }
}

@Composable
fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Surface2), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Green)
            Spacer(Modifier.width(8.dp))
            Text(label, color = White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun TripsScreen(entries: List<Entry>, role: String, modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("سوابق ثبت‌شده", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("اطلاعات فقط روی همین گوشی نگهداری می‌شود", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        if (entries.isEmpty()) {
            EmptyState("فهرست خالی است", "با دکمه افزودن، اولین مورد را ثبت کنید.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 90.dp)) {
                items(entries) { EntryRow(it, role) }
            }
        }
    }
}

@Composable
fun EntryRow(entry: Entry, role: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Surface2, CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (role == "driver") Icons.Default.LocalTaxi else Icons.Default.Route, null, tint = Green)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.note.ifBlank { if (role == "driver") "ثبت درآمد و هزینه" else "هزینه سفر" }, color = White, fontWeight = FontWeight.SemiBold)
                Text(entry.date, color = Muted, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${money(if (role == "driver") entry.amount else entry.amount)} تومان", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (role == "driver" && entry.cost > 0) Text("هزینه: ${money(entry.cost)}", color = Red, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun StatsScreen(role: String, count: Int, income: Long, costs: Long, net: Long, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(18.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("گزارش عملکرد", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("مجموع تمام موارد ثبت‌شده", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        MetricCard(if (role == "driver") "کل درآمد" else "کل هزینه سفر", money(if (role == "driver") income else costs), Icons.Default.AccountBalanceWallet, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        MetricCard("مجموع موارد ثبت‌شده", count.toString(), Icons.Default.ReceiptLong, Modifier.fillMaxWidth())
        if (role == "driver") {
            Spacer(Modifier.height(10.dp))
            MetricCard("کل هزینه‌ها", money(costs), Icons.Default.Payments, Modifier.fillMaxWidth(), Red)
            Spacer(Modifier.height(10.dp))
            MetricCard("سود خالص", money(net), Icons.Default.TrendingUp, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(18.dp))
        Text("توجه: گزارش فعلی بر اساس داده‌های واردشده توسط شما محاسبه می‌شود.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
fun ToolsScreen(role: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(18.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("ابزارهای کاربردی", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        val tools = if (role == "driver") listOf(
            "ماشین‌حساب سود سفر" to Icons.Default.Calculate,
            "محاسبه هزینه سوخت" to Icons.Default.LocalGasStation,
            "یادآور سرویس خودرو" to Icons.Default.Build,
            "چک‌لیست پیش از حرکت" to Icons.Default.Checklist
        ) else listOf(
            "ماشین‌حساب هزینه سفر" to Icons.Default.Calculate,
            "چک‌لیست وسایل سفر" to Icons.Default.Checklist,
            "یادداشت مقصدها" to Icons.Default.Map,
            "راهنمای ایمنی سفر" to Icons.Default.Security
        )
        tools.forEach { (title, icon) ->
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(15.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = Green)
                    Spacer(Modifier.width(12.dp))
                    Text(title, color = White, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ChevronLeft, null, tint = Muted)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("ابزارهای بیشتر در نسخه‌های بعدی اضافه می‌شوند.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
fun ProfileScreen(role: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("پروفایل", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("اسنپ‌یار X", color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("نسخه ۱.۰.۰", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Text("حالت فعال: ${if (role == "driver") "راننده" else "مسافر"}", color = Green)
                Spacer(Modifier.height(8.dp))
                Text("ذخیره‌سازی: محلی روی دستگاه", color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("اطلاعات شما به سرور ارسال نمی‌شود. با حذف داده‌های برنامه ممکن است اطلاعات محلی نیز پاک شود.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Inbox, null, tint = Muted, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = White, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable
fun AddEntryDialog(role: String, onDismiss: () -> Unit, onSave: (Long, Long, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text(if (role == "driver") "ثبت درآمد و هزینه" else "ثبت هزینه سفر", color = White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter(Char::isDigit) },
                    label = { Text(if (role == "driver") "درآمد سفر (تومان)" else "هزینه سفر (تومان)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, colors = fieldColors()
                )
                if (role == "driver") {
                    OutlinedTextField(
                        value = cost, onValueChange = { cost = it.filter(Char::isDigit) },
                        label = { Text("هزینه مربوط به سفر (تومان)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, colors = fieldColors()
                    )
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("عنوان یا توضیح (اختیاری)") }, singleLine = true, colors = fieldColors())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.toLongOrNull() ?: 0L
                val c = if (role == "driver") cost.toLongOrNull() ?: 0L else 0L
                if (a > 0) onSave(a, c, note.trim())
            }) { Text("ذخیره", color = Green) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف", color = Muted) } }
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = White, unfocusedTextColor = White,
    focusedBorderColor = Green, unfocusedBorderColor = Muted,
    focusedLabelColor = Green, unfocusedLabelColor = Muted,
    cursorColor = Green
)

fun money(value: Long): String = DecimalFormat("#,###").format(value)

fun loadEntries(prefs: android.content.SharedPreferences): List<Entry> {
    return try {
        val array = JSONArray(prefs.getString("entries", "[]") ?: "[]")
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Entry(o.getLong("id"), o.getString("role"), o.getLong("amount"),
                o.optLong("cost"), o.optString("note"), o.optString("date"))
        }
    } catch (_: Exception) { emptyList() }
}

fun saveEntries(prefs: android.content.SharedPreferences, entries: List<Entry>) {
    val array = JSONArray()
    entries.forEach { e ->
        array.put(JSONObject().put("id", e.id).put("role", e.role)
            .put("amount", e.amount).put("cost", e.cost)
            .put("note", e.note).put("date", e.date))
    }
    prefs.edit().putString("entries", array.toString()).apply()
}
