@Composable
fun SnapYarApp() {
    val context = LocalContext.current

    val prefs = remember {
        context.getSharedPreferences("snapyar_local", 0)
    }

    var role by remember {
        mutableStateOf(
            prefs.getString("role", "driver") ?: "driver"
        )
    }

    var entries by remember {
        mutableStateOf(loadEntries(prefs))
    }

    var tab by remember {
        mutableIntStateOf(0)
    }

    var showAdd by remember {
        mutableStateOf(false)
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

        val entry = Entry(
            System.currentTimeMillis(),
            role,
            amount,
            cost,
            note,
            date
        )

        entries = listOf(entry) + entries
        saveEntries(prefs, entries)
    }

    val current = entries.filter { it.role == role }

    val totalIncome = if (role == "driver") {
        current.sumOf { it.amount }
    } else {
        0L
    }

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
                            .padding(
                                horizontal = 20.dp,
                                vertical = 14.dp
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .background(
                                        Green,
                                        RoundedCornerShape(14.dp)
                                    ),
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
                            ) {
                                saveRole("driver")
                            }

                            RoleChip(
                                "مسافر",
                                role == "passenger",
                                Modifier.weight(1f)
                            ) {
                                saveRole("passenger")
                            }
                        }
                    }
                },

                bottomBar = {
                    NavigationBar(
                        containerColor = SurfaceDark,
                        contentColor = Muted
                    ) {
                        val labels = listOf(
                            "خانه",
                            "سفرها",
                            "گزارش",
                            "ابزارها",
                            "پروفایل"
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
                                    Icon(
                                        icons[index],
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(label, fontSize = 10.sp)
                                },
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

                    3 -> ToolsScreen(
                        role,
                        Modifier.padding(padding)
                    )

                    else -> ProfileScreen(
                        role,
                        Modifier.padding(padding)
                    )
                }
            }

            if (showAdd) {
                AddEntryDialog(
                    role,
                    onDismiss = {
                        showAdd = false
                    },
                    onSave = { amount, cost, note ->
                        addEntry(amount, cost, note)
                        showAdd = false
                    }
                )
            }
        }
    }
}
