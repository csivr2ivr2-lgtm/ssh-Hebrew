package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Connection model
data class Connection(
  val id: String,
  val name: String,
  val host: String,
  val port: Int,
  val username: String,
  val authType: String,
  val isFavorite: Boolean = false
)

// Active terminal tab model
data class TerminalTab(
  val id: String,
  val title: String,
  val serverHost: String,
  val logs: List<String>,
  val currentPromptPath: String = "~"
)

// Terminal themes
enum class TerminalTheme(val displayName: String, val bg: Color, val text: Color, val accent: Color) {
  SLATE("Slate Modern", Color(0xFF1E222A), Color(0xFFABB2BF), Color(0xFF61AFEF)),
  MATRIX("Matrix Cyber", Color(0xFF030A04), Color(0xFF33FF33), Color(0xFF00FF00)),
  AMBER("Retro Amber", Color(0xFF0D0B01), Color(0xFFFFB000), Color(0xFFFFCC00)),
  CYBERPUNK("Cyber Sunset", Color(0xFF180A2B), Color(0xFF00E5FF), Color(0xFFFF007F))
}

@Composable
fun LocalizationProvider(
  languageCode: String,
  content: @Composable () -> Unit
) {
  val context = LocalContext.current
  val localizedContext = remember(context, languageCode) {
    val locale = java.util.Locale(languageCode)
    java.util.Locale.setDefault(locale)
    val config = android.content.res.Configuration(context.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    context.createConfigurationContext(config)
  }

  val direction = if (languageCode == "he") LayoutDirection.Rtl else LayoutDirection.Ltr

  CompositionLocalProvider(
    LocalContext provides localizedContext,
    LocalLayoutDirection provides direction
  ) {
    content()
  }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      var currentLanguage by remember { mutableStateOf("he") } // Default to Hebrew for dynamic locale setup

      LocalizationProvider(languageCode = currentLanguage) {
        MyApplicationTheme(darkTheme = true) {
          MainAppScreen(
            currentLanguage = currentLanguage,
            onLanguageChange = { currentLanguage = it }
          )
        }
      }
    }
  }
}

@Composable
fun MainAppScreen(
  currentLanguage: String,
  onLanguageChange: (String) -> Unit
) {
  val context = LocalContext.current
  val listDirection = LocalLayoutDirection.current
  val isRtl = listDirection == LayoutDirection.Rtl

  // Base state management
  var currentTabIdx by remember { mutableStateOf(0) } // 0: Servers, 1: Terminal, 2: Files, 3: Settings
  
  // Connection states
  var serversList by remember {
    mutableStateOf(
      listOf(
        Connection("1", "שרת הפקה VPS", "cloud.vps.company.com", 22, "root", "Password", true),
        Connection("2", "בסיס נתונים - פיתוח", "db-prod.internal.net", 5432, "admin", "SSH Key"),
        Connection("3", "שרת גיבוי", "backup-02.local", 2200, "backup", "Password")
      )
    )
  }

  // Active terminal tabs state
  var terminalTabs by remember(context) {
    mutableStateOf(
      listOf(
        TerminalTab(
          "1",
          "שרת הפקה VPS",
          "cloud.vps.company.com",
          listOf(
            "${context.getString(R.string.welcome_message)} v1.0.42 (Local Shell)",
            "${context.getString(R.string.status_connected)}: root@cloud.vps.company.com:22",
            "Linux core-prod-vps 5.15.0-88-generic #98-Ubuntu SMP x86_64",
            "Last login: Thu Apr  4 10:45:12 2026 from 192.168.1.104",
            "root@prod-vps:~# "
          )
        )
      )
    )
  }
  var activeTabIdx by remember { mutableStateOf(0) }

  // Search state
  var searchQuery by remember { mutableStateOf("") }
  
  // Form dialog states
  var showAddDialog by remember { mutableStateOf(false) }
  var showEditDialog by remember { mutableStateOf<Connection?>(null) }
  var quickHostInput by remember { mutableStateOf("") }
  var quickPortInput by remember { mutableStateOf("22") }
  var quickUserInput by remember { mutableStateOf("root") }

  // Settings states
  var selectedTheme by remember { mutableStateOf(TerminalTheme.SLATE) }
  var fontSize by remember { mutableStateOf(14) }
  var autoLock by remember { mutableStateOf(true) }
  var biometricAuth by remember { mutableStateOf(false) }

  // File browser folder system
  var isRemoteFiles by remember { mutableStateOf(false) }
  var currentFilesPath by remember { mutableStateOf("/") }
  var mockFilesList by remember {
    mutableStateOf(
      listOf(
        "bin" to true,
        "etc" to true,
        "home" to true,
        "var" to true,
        "docker-compose.yml" to false,
        "nginx.conf" to false,
        "authorized_keys" to false
      )
    )
  }

  // Coroutine scope
  val scope = rememberCoroutineScope()

  // Terminal navigation trigger from connection list
  val connectToServer: (Connection) -> Unit = { conn ->
    // Check if tab already exists for host
    val existingTabIdx = terminalTabs.indexOfFirst { it.serverHost == conn.host }
    if (existingTabIdx >= 0) {
      activeTabIdx = existingTabIdx
    } else {
      val newTerminalTab = TerminalTab(
        id = System.currentTimeMillis().toString(),
        title = conn.name,
        serverHost = conn.host,
        logs = listOf(
          context.getString(R.string.welcome_message),
          "${context.getString(R.string.status_connecting)}: ${conn.host}:${conn.port}",
          context.getString(R.string.fingerprint_verified),
          context.getString(R.string.secure_connection),
          "Last login: ${conn.username}@${conn.host} on ${conn.authType}",
          "${conn.username}@${conn.name}:~$ "
        )
      )
      terminalTabs = terminalTabs + newTerminalTab
      activeTabIdx = terminalTabs.size - 1
    }
    currentTabIdx = 1 // Nav to Terminal view
    Toast.makeText(context, context.getString(R.string.status_connecting), Toast.LENGTH_SHORT).show()
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    bottomBar = {
      NavigationBar(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = Color(0xFF1E1E2C)
      ) {
        NavigationBarItem(
          selected = currentTabIdx == 0,
          onClick = { currentTabIdx = 0 },
          icon = { Icon(Icons.Default.Dns, contentDescription = stringResource(R.string.connections_title)) },
          label = { Text(stringResource(R.string.connections_title), fontSize = 11.sp) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color(0xFF00FFCC),
            selectedTextColor = Color(0xFF00FFCC),
            unselectedIconColor = Color.Gray,
            unselectedTextColor = Color.Gray,
            indicatorColor = Color(0xFF2C2C40)
          )
        )
        NavigationBarItem(
          selected = currentTabIdx == 1,
          onClick = { currentTabIdx = 1 },
          icon = { Icon(Icons.Default.Terminal, contentDescription = stringResource(R.string.terminal_activity_title)) },
          label = { Text(stringResource(R.string.terminal_activity_title), fontSize = 11.sp) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color(0xFF00FFCC),
            selectedTextColor = Color(0xFF00FFCC),
            unselectedIconColor = Color.Gray,
            unselectedTextColor = Color.Gray,
            indicatorColor = Color(0xFF2C2C40)
          )
        )
        NavigationBarItem(
          selected = currentTabIdx == 2,
          onClick = { currentTabIdx = 2 },
          icon = { Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.file_browser_title)) },
          label = { Text(stringResource(R.string.file_browser_title), fontSize = 11.sp) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color(0xFF00FFCC),
            selectedTextColor = Color(0xFF00FFCC),
            unselectedIconColor = Color.Gray,
            unselectedTextColor = Color.Gray,
            indicatorColor = Color(0xFF2C2C40)
          )
        )
        NavigationBarItem(
          selected = currentTabIdx == 3,
          onClick = { currentTabIdx = 3 },
          icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title)) },
          label = { Text(stringResource(R.string.settings_title), fontSize = 11.sp) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color(0xFF00FFCC),
            selectedTextColor = Color(0xFF00FFCC),
            unselectedIconColor = Color.Gray,
            unselectedTextColor = Color.Gray,
            indicatorColor = Color(0xFF2C2C40)
          )
        )
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF0F0F1A))
        .padding(innerPadding)
    ) {
      // Background Cyber Mesh Drawing
      Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val columns = 16
        val rows = 24
        val colWidth = width / columns
        val rowHeight = height / rows

        for (i in 0..columns) {
          drawLine(
            color = Color(0x0600FF99),
            start = androidx.compose.ui.geometry.Offset(i * colWidth, 0f),
            end = androidx.compose.ui.geometry.Offset(i * colWidth, height),
            strokeWidth = 1f
          )
        }
        for (i in 0..rows) {
          drawLine(
            color = Color(0x0600FF99),
            start = androidx.compose.ui.geometry.Offset(0f, i * rowHeight),
            end = androidx.compose.ui.geometry.Offset(width, i * rowHeight),
            strokeWidth = 1f
          )
        }
      }

      AnimatedContent(
        targetState = currentTabIdx,
        transitionSpec = {
          fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
        },
        label = "TabContent"
      ) { targetIdx ->
        when (targetIdx) {
          0 -> {
            // My Servers tab
            ConnectionsScreen(
              servers = serversList,
              searchQuery = searchQuery,
              onSearchChange = { searchQuery = it },
              quickHost = quickHostInput,
              onQuickHostChange = { quickHostInput = it },
              quickPort = quickPortInput,
              onQuickPortChange = { quickPortInput = it },
              quickUser = quickUserInput,
              onQuickUserChange = { quickUserInput = it },
              onConnect = { host, port, user ->
                connectToServer(
                  Connection(
                    id = System.currentTimeMillis().toString(),
                    name = "Quick Connection",
                    host = host,
                    port = port,
                    username = user,
                    authType = "Password"
                  )
                )
              },
              onAddClick = { showAddDialog = true },
              onEditClick = { showEditDialog = it },
              onDeleteClick = { conn ->
                serversList = serversList.filter { it.id != conn.id }
                Toast.makeText(context, context.getString(R.string.connection_deleted), Toast.LENGTH_SHORT).show()
              },
              onSelectConnection = connectToServer
            )
          }
          1 -> {
            // Tabbed Terminal client
            TerminalScreen(
              tabs = terminalTabs,
              activeIdx = activeTabIdx,
              onTabSelect = { activeTabIdx = it },
              onCloseTab = { idx ->
                if (terminalTabs.size > 1) {
                  terminalTabs = terminalTabs.filterIndexed { i, _ -> i != idx }
                  if (activeTabIdx >= terminalTabs.size) {
                    activeTabIdx = terminalTabs.size - 1
                  }
                } else {
                  Toast.makeText(context, "Cannot close the last tab", Toast.LENGTH_SHORT).show()
                }
              },
              onNewTab = {
                val newTab = TerminalTab(
                  id = System.currentTimeMillis().toString(),
                  title = "Default Core",
                  serverHost = "local.ssh.host",
                  logs = listOf(
                    "Welcome to TabSSH",
                    "Opened secure terminal session (SSH Mode)",
                    "Local: shell 1.4-v4",
                    "user@tabssh:~$ "
                  )
                )
                terminalTabs = terminalTabs + newTab
                activeTabIdx = terminalTabs.size - 1
              },
              theme = selectedTheme,
              sizeText = fontSize,
              onExecuteCommand = { cmd ->
                val currentTab = terminalTabs[activeTabIdx]
                val currentPrompt = "${currentTab.serverHost.substringBefore(".")}:${currentTab.currentPromptPath}$ "
                val echoInput = "$currentPrompt$cmd"
                
                val output = when (cmd.trim().lowercase()) {
                  "help" -> listOf(
                    "Available SSH terminal commands:",
                    "  help          - Display this help prompt in TabSSH client",
                    "  neofetch      - Render system neofetch in Hebrew aesthetics",
                    "  ls            - List simulated remote files on host",
                    "  pwd           - Print remote workspace directory",
                    "  uptime        - Render server online duration",
                    "  clear         - Wipe the current screen logs",
                    "  exit          - Close active connection tab securely"
                  )
                  "neofetch" -> listOf(
                    "         .o8888b.        root@vps-server",
                    "        d8b_  _d8b       ---------------",
                    "        88_ @  @ _88     OS: TabSSH Virtual GNU/Linux x86_64",
                    "        88        88     Host: AI Studio Android Runtime Workspace",
                    "        d8b_  _d8b       Kernel: 5.15.0-3abf-aistudio",
                    "         `Y8888P'        Uptime: 2 days, 16 hours, 24 mins",
                    "                         Shell: zsh 5.8 (Hebrew Locale ready)",
                    "                         Resolution: 1080x2400 (Fluid Adaptive M3)",
                    "                         Terminal Theme: ${selectedTheme.displayName}",
                    "                         WM: Jetpack Compose Smooth Engine v1.8.0",
                    "                         CPU: ARMv8 Neon Cluster v4 (8) @ 2.80GHz",
                    "                         Memory: 3341MiB / 8192MiB"
                  )
                  "ls" -> listOf(
                    "total 32",
                    "drwxr-xr-x  5 root  root  4096 Apr  4 10:12 bin",
                    "drwxr-xr-x  2 root  root  4096 Apr  4 11:05 etc",
                    "drwxr-xr-x  3 root  root  4096 Apr  4 11:20 home",
                    "drwxr-xr-x 12 root  root  4096 Apr  4 10:45 var",
                    "-rw-r--r--  1 root  root   420 Apr  2 12:45 docker-compose.yml",
                    "-rw-r--r--  1 root  root  1820 Apr  4 11:15 nginx.conf",
                    "-rw-------  1 root  root   385 Apr  4 09:30 authorized_keys"
                  )
                  "pwd" -> listOf("/root")
                  "uptime" -> listOf(" 11:12:05 up 2 days, 16:24,  1 user,  load average: 0.15, 0.08, 0.05")
                  "clear" -> emptyList()
                  "exit" -> listOf("Logging logout signal from remote server... Session closed.")
                  else -> listOf("bash: $cmd: command not found (type 'help' for Hebrew localization helpers)")
                }

                // Update tab logs
                val updatedTabs = terminalTabs.mapIndexed { idx, tab ->
                  if (idx == activeTabIdx) {
                    if (cmd.trim().lowercase() == "clear") {
                      tab.copy(logs = listOf("${tab.serverHost.substringBefore(".")}:${tab.currentPromptPath}$ "))
                    } else {
                      val newLogs = tab.logs.dropLast(1) + echoInput + output + "${tab.serverHost.substringBefore(".")}:${tab.currentPromptPath}$ "
                      tab.copy(logs = newLogs)
                    }
                  } else tab
                }
                terminalTabs = updatedTabs
              }
            )
          }
          2 -> {
            // File Explorer screen
            FileBrowserScreen(
              isRemote = isRemoteFiles,
              onToggleRemote = { isRemoteFiles = it },
              currentPath = currentFilesPath,
              files = mockFilesList,
              onFileClick = { name, isFolder ->
                if (isFolder) {
                  currentFilesPath += "$name/"
                  if (name == "..") {
                    currentFilesPath = "/"
                  }
                  // Shuffle mock files to simulate navigation
                  mockFilesList = if (currentFilesPath == "/") {
                    listOf(
                      "bin" to true,
                      "etc" to true,
                      "home" to true,
                      "var" to true,
                      "docker-compose.yml" to false,
                      "nginx.conf" to false,
                      "authorized_keys" to false
                    )
                  } else {
                    listOf(
                      ".." to true,
                      "ssh_config" to false,
                      "sshd_config" to false,
                      "ssl" to true,
                      "hosts" to false
                    )
                  }
                } else {
                  Toast.makeText(context, "${context.getString(R.string.file_properties)}: $name", Toast.LENGTH_SHORT).show()
                }
              },
              onCreateFolder = { folderName ->
                mockFilesList = listOf(folderName to true) + mockFilesList
                Toast.makeText(context, context.getString(R.string.connection_saved), Toast.LENGTH_SHORT).show()
              },
              onDeleteFile = { name ->
                mockFilesList = mockFilesList.filter { it.first != name }
                Toast.makeText(context, context.getString(R.string.connection_deleted), Toast.LENGTH_SHORT).show()
              }
            )
          }
          3 -> {
            // Settings screen
            SettingsScreen(
              selectedTheme = selectedTheme,
              onThemeSelect = { selectedTheme = it },
              fontSize = fontSize,
              onFontSizeChange = { fontSize = it },
              autoLock = autoLock,
              onAutoLockToggle = { autoLock = it },
              biometricAuth = biometricAuth,
              onBiometricAuthToggle = { biometricAuth = it },
              currentLanguage = currentLanguage,
              onLanguageChange = onLanguageChange
            )
          }
        }
      }

      // Dialog for Adding server
      if (showAddDialog) {
        AddServerDialog(
          onDismiss = { showAddDialog = false },
          onSave = { conn ->
            serversList = serversList + conn
            showAddDialog = false
            Toast.makeText(context, context.getString(R.string.connection_saved), Toast.LENGTH_SHORT).show()
          }
        )
      }

      // Dialog for Editing server
      showEditDialog?.let { currentConn ->
        EditServerDialog(
          conn = currentConn,
          onDismiss = { showEditDialog = null },
          onSave = { updatedConn ->
            serversList = serversList.map { if (it.id == updatedConn.id) updatedConn else it }
            showEditDialog = null
            Toast.makeText(context, context.getString(R.string.connection_saved), Toast.LENGTH_SHORT).show()
          }
        )
      }
    }
  }
}

@Composable
fun ConnectionsScreen(
  servers: List<Connection>,
  searchQuery: String,
  onSearchChange: (String) -> Unit,
  quickHost: String,
  onQuickHostChange: (String) -> Unit,
  quickPort: String,
  onQuickPortChange: (String) -> Unit,
  quickUser: String,
  onQuickUserChange: (String) -> Unit,
  onConnect: (String, Int, String) -> Unit,
  onAddClick: () -> Unit,
  onEditClick: (Connection) -> Unit,
  onDeleteClick: (Connection) -> Unit,
  onSelectConnection: (Connection) -> Unit
) {
  val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    // Top Brand Header with glowing accent
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp)
        .border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF161626)),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(48.dp)
            .background(
              Brush.linearGradient(listOf(Color(0xFF00FFCC), Color(0xFF0099FF))),
              shape = RoundedCornerShape(12.dp)
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Terminal, contentDescription = "TabSSH Icon", tint = Color.Black, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "TabSSH — " + stringResource(R.string.main_activity_title),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
          )
          Text(
            text = stringResource(R.string.welcome_message),
            color = Color(0xFF00FFCC),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
          )
        }
      }
    }

    // Quick Connect Panel (collapsible or streamlined card form)
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 20.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF131322)),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text(
          text = stringResource(R.string.quick_connect_title),
          color = Color.White,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          modifier = Modifier.padding(bottom = 12.dp)
        )
        
        OutlinedTextField(
          value = quickHost,
          onValueChange = onQuickHostChange,
          label = { Text(stringResource(R.string.host_hint), fontSize = 13.sp) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF00FFCC),
            focusedLabelColor = Color(0xFF00FFCC),
            unfocusedBorderColor = Color.Gray,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
          ),
          textStyle = TextStyle(fontFamily = FontFamily.Monospace),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("quick_host_input")
            .padding(bottom = 8.dp),
          singleLine = true
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = quickUser,
            onValueChange = onQuickUserChange,
            label = { Text(stringResource(R.string.username_hint), fontSize = 13.sp) },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Color(0xFF00FFCC),
              focusedLabelColor = Color(0xFF00FFCC),
              unfocusedBorderColor = Color.Gray,
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("quick_user_input"),
            singleLine = true
          )

          OutlinedTextField(
            value = quickPort,
            onValueChange = onQuickPortChange,
            label = { Text(stringResource(R.string.port_hint).substringBefore(" ("), fontSize = 13.sp) },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Color(0xFF00FFCC),
              focusedLabelColor = Color(0xFF00FFCC),
              unfocusedBorderColor = Color.Gray,
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
              .width(90.dp)
              .testTag("quick_port_input"),
            singleLine = true
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
          onClick = {
            if (quickHost.isNotBlank()) {
              val parsedPort = quickPort.toIntOrNull() ?: 22
              onConnect(quickHost, parsedPort, quickUser)
            }
          },
          enabled = quickHost.isNotBlank(),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("connect_button"),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF00FFCC),
            contentColor = Color.Black,
            disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
          ),
          shape = RoundedCornerShape(10.dp)
        ) {
          Icon(Icons.Default.Bolt, contentDescription = "Connect Bolt")
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(R.string.connect_button), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
      }
    }

    // Saved Server Connections Header with ADD button
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Storage, contentDescription = "Servers Icon", tint = Color(0xFF00FFCC), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.connections_title),
          color = Color.White,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp
        )
      }
      IconButton(
        onClick = onAddClick,
        modifier = Modifier
          .size(36.dp)
          .background(Color(0xFF202035), CircleShape)
          .testTag("add_server_fab")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Add Connection", tint = Color(0xFF00FFCC))
      }
    }

    // Search connection bar
    OutlinedTextField(
      value = searchQuery,
      onValueChange = onSearchChange,
      placeholder = { Text(stringResource(R.string.search_connections), color = Color.Gray, fontSize = 14.sp) },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon", tint = Color.Gray) },
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 10.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color(0xFF0099FF),
        unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White
      ),
      shape = RoundedCornerShape(12.dp),
      singleLine = true
    )

    // Connections list
    val filteredServers = servers.filter {
      it.name.contains(searchQuery, ignoreCase = true) || it.host.contains(searchQuery, ignoreCase = true)
    }

    if (filteredServers.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(Icons.Default.TvOff, contentDescription = "Empty list", tint = Color.Gray, modifier = Modifier.size(64.dp))
          Spacer(modifier = Modifier.height(16.dp))
          Text(stringResource(R.string.no_connections_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            stringResource(R.string.no_connections_message).replace("\\n", "\n"),
            color = Color.Gray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(filteredServers) { conn ->
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("server_card_${conn.id}")
              .clickable { onSelectConnection(conn) },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141424)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.5.dp, if (conn.isFavorite) Color(0xFF00FFCC).copy(alpha = 0.3f) else Color.Transparent)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .background(Color(0xFF212136), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = if (conn.authType == "Password") Icons.Default.VpnKey else Icons.Default.Key,
                  contentDescription = "Auth Icon",
                  tint = if (conn.isFavorite) Color(0xFF00FFCC) else Color.White,
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.width(14.dp))
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(conn.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                  if (conn.isFavorite) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.Star, contentDescription = "Favorite star", tint = Color(0xFFFFCC00), modifier = Modifier.size(14.dp))
                  }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  "${conn.username}@${conn.host}:${conn.port}",
                  color = Color.Gray,
                  fontSize = 12.sp,
                  fontFamily = FontFamily.Monospace
                )
              }

              // Card action buttons
              IconButton(onClick = { onEditClick(conn) }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit server info", tint = Color.Gray)
              }
              IconButton(onClick = { onDeleteClick(conn) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete server configuration", tint = Color.Red.copy(alpha = 0.7f))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun TerminalScreen(
  tabs: List<TerminalTab>,
  activeIdx: Int,
  onTabSelect: (Int) -> Unit,
  onCloseTab: (Int) -> Unit,
  onNewTab: () -> Unit,
  theme: TerminalTheme,
  sizeText: Int,
  onExecuteCommand: (String) -> Unit
) {
  val selectedTab = tabs.getOrNull(activeIdx) ?: tabs.first()
  var inputCmd by remember { mutableStateOf("") }
  val focusRequester = remember { FocusRequester() }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(theme.bg)
  ) {
    // Beautiful browser style tab bar across top
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(0.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF131322))
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Default.Layers, contentDescription = "Tab Bar Layout", tint = Color(0xFF00FFCC), modifier = Modifier.padding(horizontal = 4.dp))
        
        Row(
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          tabs.forEachIndexed { idx, tab ->
            val isActive = idx == activeIdx
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isActive) theme.bg else Color(0xFF1B1B2C))
                .clickable { onTabSelect(idx) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = tab.title,
                color = if (isActive) theme.accent else Color.LightGray,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
              )
              Spacer(modifier = Modifier.width(6.dp))
              Icon(
                Icons.Default.Close,
                contentDescription = "Close tab icon",
                tint = Color.Gray,
                modifier = Modifier
                  .size(12.dp)
                  .clickable { onCloseTab(idx) }
              )
            }
          }
        }

        // Add tab icon
        IconButton(
          onClick = onNewTab,
          modifier = Modifier
            .size(32.dp)
            .background(Color(0xFF2C2C40), CircleShape)
        ) {
          Icon(Icons.Default.Add, contentDescription = "Add secure tab session", tint = Color.White, modifier = Modifier.size(16.dp))
        }
      }
    }

    // Active terminal output console
    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      items(selectedTab.logs) { logLine ->
        Text(
          text = logLine,
          color = when {
            logLine.contains("✔") -> Color(0xFF00FFCC)
            logLine.contains("root@") || logLine.contains("user@") || logLine.contains("~$") -> theme.accent
            logLine.contains("bash: ") -> Color.Red
            else -> theme.text
          },
          fontSize = sizeText.sp,
          fontFamily = FontFamily.Monospace,
          lineHeight = (sizeText + 4).sp
        )
      }
    }

    // Interactive function keyboard row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF11111E).copy(alpha = 0.9f))
        .padding(horizontal = 8.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceAround
    ) {
      arrayOf("Esc", "Ctrl", "Alt", "Tab", "Up", "Dn", "Shift", "/").forEach { label ->
        Card(
          shape = RoundedCornerShape(4.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF202035)),
          modifier = Modifier
            .clickable {
              if (label == "Tab") {
                inputCmd += "  "
              } else if (label == "Up") {
                inputCmd = "neofetch"
              } else if (label == "Dn") {
                inputCmd = "help"
              } else {
                inputCmd += label
              }
            }
            .padding(2.dp)
        ) {
          Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
          )
        }
      }
    }

    // Interactive console input area
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, Color(0xFF1E1E2C))
        .background(Color(0xFF0D0D15))
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(Icons.Default.ChevronRight, contentDescription = "Terminal Prompt Chevron", tint = theme.accent)
      
      TextField(
        value = inputCmd,
        onValueChange = { inputCmd = it },
        placeholder = { Text("הקלד פקודה (הקלד help)...", color = theme.text.copy(alpha = 0.4f), fontSize = 13.sp) },
        textStyle = TextStyle(
          color = theme.text,
          fontSize = sizeText.sp,
          fontFamily = FontFamily.Monospace
        ),
        colors = TextFieldDefaults.colors(
          focusedContainerColor = Color.Transparent,
          unfocusedContainerColor = Color.Transparent,
          disabledContainerColor = Color.Transparent,
          focusedIndicatorColor = Color.Transparent,
          unfocusedIndicatorColor = Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(
          imeAction = ImeAction.Send,
          autoCorrectEnabled = false
        ),
        keyboardActions = KeyboardActions(
          onSend = {
            if (inputCmd.isNotBlank()) {
              onExecuteCommand(inputCmd)
              inputCmd = ""
            }
          }
        ),
        modifier = Modifier
          .weight(1f)
          .focusRequester(focusRequester)
          .testTag("terminal_input_field"),
        singleLine = true
      )

      IconButton(
        onClick = {
          if (inputCmd.isNotBlank()) {
            onExecuteCommand(inputCmd)
            inputCmd = ""
          }
        }
      ) {
        Icon(Icons.Default.Send, contentDescription = "Send active command execution", tint = theme.accent)
      }
    }
  }

  // Auto request focus to make typing instant
  LaunchedEffect(activeIdx) {
    focusRequester.requestFocus()
  }
}

@Composable
fun FileBrowserScreen(
  isRemote: Boolean,
  onToggleRemote: (Boolean) -> Unit,
  currentPath: String,
  files: List<Pair<String, Boolean>>,
  onFileClick: (String, Boolean) -> Unit,
  onCreateFolder: (String) -> Unit,
  onDeleteFile: (String) -> Unit
) {
  var showCreateFolderDialog by remember { mutableStateOf(false) }
  var newFolderName by remember { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    // Header for remote vs local switcher
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = stringResource(R.string.file_browser_title),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
      )
      
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0xFF202035))
          .padding(4.dp)
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (!isRemote) Color(0xFF00FFCC) else Color.Transparent)
            .clickable { onToggleRemote(false) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Text(
            stringResource(R.string.local_files),
            color = if (!isRemote) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isRemote) Color(0xFF00FFCC) else Color.Transparent)
            .clickable { onToggleRemote(true) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Text(
            stringResource(R.string.remote_files),
            color = if (isRemote) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    // Path directory breadcrumbs card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF141424))
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Default.Folder, contentDescription = "Active directory icon", tint = Color(0xFF00FFCC))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = currentPath,
          color = Color.White,
          fontFamily = FontFamily.Monospace,
          fontSize = 14.sp
        )
        Spacer(modifier = Modifier.weight(1f))
        
        IconButton(onClick = { showCreateFolderDialog = true }) {
          Icon(Icons.Default.CreateNewFolder, contentDescription = "Create Directory", tint = Color(0xFF00FFCC))
        }
      }
    }

    // Files scrollable list
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF10101F))
    ) {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        items(files) { (name, isFolder) ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .clickable { onFileClick(name, isFolder) }
              .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = if (isFolder) Icons.Default.Folder else Icons.Default.Description,
              contentDescription = "File Type icon",
              tint = if (isFolder) Color(0xFFFFCC00) else Color(0xFFABB2BF),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
              text = name,
              color = Color.White,
              fontSize = 14.sp,
              fontFamily = FontFamily.Monospace,
              modifier = Modifier.weight(1f)
            )
            
            if (name != "..") {
              IconButton(onClick = { onDeleteFile(name) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete file record", tint = Color.LightGray, modifier = Modifier.size(18.dp))
              }
            }
          }
        }
      }
    }
  }

  // Create folder dialog
  if (showCreateFolderDialog) {
    AlertDialog(
      onDismissRequest = { showCreateFolderDialog = false },
      title = { Text(stringResource(R.string.new_folder), color = Color.White) },
      text = {
        OutlinedTextField(
          value = newFolderName,
          onValueChange = { newFolderName = it },
          label = { Text("שם התיקייה") },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF00FFCC),
            focusedLabelColor = Color(0xFF00FFCC),
            unfocusedBorderColor = Color.Gray,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
          ),
          singleLine = true
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (newFolderName.isNotBlank()) {
              onCreateFolder(newFolderName)
              newFolderName = ""
              showCreateFolderDialog = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC), contentColor = Color.Black)
        ) {
          Text(stringResource(R.string.save))
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateFolderDialog = false }) {
          Text(stringResource(R.string.cancel), color = Color.White)
        }
      },
      containerColor = Color(0xFF161626)
    )
  }
}

@Composable
fun SettingsScreen(
  selectedTheme: TerminalTheme,
  onThemeSelect: (TerminalTheme) -> Unit,
  fontSize: Int,
  onFontSizeChange: (Int) -> Unit,
  autoLock: Boolean,
  onAutoLockToggle: (Boolean) -> Unit,
  biometricAuth: Boolean,
  onBiometricAuthToggle: (Boolean) -> Unit,
  currentLanguage: String,
  onLanguageChange: (String) -> Unit
) {
  val context = LocalContext.current

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    Text(
      text = stringResource(R.string.settings_title),
      color = Color.White,
      fontWeight = FontWeight.Bold,
      fontSize = 20.sp,
      modifier = Modifier.padding(bottom = 16.dp)
    )

    LazyColumn(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // General Settings Grouping
      item {
        Text(stringResource(R.string.settings_general).uppercase(), color = Color(0xFF00FFCC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF141424))
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(stringResource(R.string.pref_startup_behavior), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.pref_startup_behavior_summary), color = Color.Gray, fontSize = 11.sp)
              }
              Icon(Icons.Filled.KeyboardArrowRight, contentDescription = ">", tint = Color.Gray)
            }
            Divider(modifier = Modifier.padding(vertical = 10.dp), color = Color.Gray.copy(alpha = 0.2f))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(stringResource(R.string.pref_auto_backup), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.pref_auto_backup_summary), color = Color.Gray, fontSize = 11.sp)
              }
              Switch(
                checked = true,
                onCheckedChange = {},
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFF00FFCC))
              )
            }
            Divider(modifier = Modifier.padding(vertical = 10.dp), color = Color.Gray.copy(alpha = 0.2f))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(stringResource(R.string.pref_language), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.pref_language_summary), color = Color.Gray, fontSize = 11.sp)
              }
              Row(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(Color(0xFF202035))
                  .padding(2.dp)
              ) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (currentLanguage == "he") Color(0xFF00FFCC) else Color.Transparent)
                    .clickable { onLanguageChange("he") }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                  Text(
                    "עברית",
                    color = if (currentLanguage == "he") Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (currentLanguage == "en") Color(0xFF00FFCC) else Color.Transparent)
                    .clickable { onLanguageChange("en") }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                  Text(
                    "English",
                    color = if (currentLanguage == "en") Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        }
      }

      // Terminal styling groupings
      item {
        Text(stringResource(R.string.settings_terminal).uppercase(), color = Color(0xFF00FFCC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF141424))
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(stringResource(R.string.pref_theme), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.pref_theme_summary), color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
            
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              TerminalTheme.values().forEach { t ->
                val isSel = t == selectedTheme
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(t.bg)
                    .border(2.dp, if (isSel) Color(0xFF00FFCC) else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onThemeSelect(t) }
                    .padding(8.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(t.displayName.substringBefore(" "), color = t.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
              }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.2f))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(stringResource(R.string.pref_font_size), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.pref_font_size_summary), color = Color.Gray, fontSize = 11.sp)
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (fontSize > 10) onFontSizeChange(fontSize - 1) }) {
                  Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease size", tint = Color.White)
                }
                Text("$fontSize", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                IconButton(onClick = { if (fontSize < 24) onFontSizeChange(fontSize + 1) }) {
                  Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase size", tint = Color.White)
                }
              }
            }
          }
        }
      }

      // Security configuration group
      item {
        Text(stringResource(R.string.settings_security).uppercase(), color = Color(0xFF00FFCC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF141424))
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(stringResource(R.string.pref_biometric_auth), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.pref_biometric_auth_summary), color = Color.Gray, fontSize = 11.sp)
              }
              Switch(
                checked = biometricAuth,
                onCheckedChange = onBiometricAuthToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFF00FFCC))
              )
            }
            Divider(modifier = Modifier.padding(vertical = 10.dp), color = Color.Gray.copy(alpha = 0.2f))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(stringResource(R.string.pref_auto_lock), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.pref_auto_lock_summary), color = Color.Gray, fontSize = 11.sp)
              }
              Switch(
                checked = autoLock,
                onCheckedChange = onAutoLockToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFF00FFCC))
              )
            }
          }
        }
      }

      // About box details
      item {
        Text(stringResource(R.string.settings_about).uppercase(), color = Color(0xFF00FFCC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 24.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF161626)),
          border = BorderStroke(1.dp, Color(0xFF00FFCC).copy(alpha = 0.4f))
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text("TabSSH - לקוח SSH מודרני", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("${stringResource(R.string.about_version)}: 1.0.42 (Production-HE)", color = Color.LightGray, fontSize = 12.sp)
            Text("${stringResource(R.string.about_license)}", color = Color.Gray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              "מפותח בקידוד Jetpack Compose ייעודי עבור קהילת הקוד הפתוח.",
              color = Color(0xFF00FFCC),
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }
    }
  }
}

@Composable
fun AddServerDialog(
  onDismiss: () -> Unit,
  onSave: (Connection) -> Unit
) {
  var name by remember { mutableStateOf("") }
  var host by remember { mutableStateOf("") }
  var port by remember { mutableStateOf("22") }
  var user by remember { mutableStateOf("root") }
  var authType by remember { mutableStateOf("Password") }
  var isFav by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.add_connection), color = Color.White, fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(stringResource(R.string.connection_name_hint).substringBefore(" (")) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF00FFCC),
            focusedLabelColor = Color(0xFF00FFCC),
            unfocusedBorderColor = Color.Gray,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
          ),
          singleLine = true
        )
        OutlinedTextField(
          value = host,
          onValueChange = { host = it },
          label = { Text(stringResource(R.string.connection_host_hint)) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF00FFCC),
            focusedLabelColor = Color(0xFF00FFCC),
            unfocusedBorderColor = Color.Gray,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
          ),
          textStyle = TextStyle(fontFamily = FontFamily.Monospace),
          singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text(stringResource(R.string.connection_username_hint)) },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Color(0xFF00FFCC),
              focusedLabelColor = Color(0xFF00FFCC),
              unfocusedBorderColor = Color.Gray,
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White
            ),
            modifier = Modifier.weight(1f),
            singleLine = true
          )
          OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("פורט") },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Color(0xFF00FFCC),
              focusedLabelColor = Color(0xFF00FFCC),
              unfocusedBorderColor = Color.Gray,
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(80.dp),
            singleLine = true
          )
        }
        
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Checkbox(
            checked = isFav,
            onCheckedChange = { isFav = it },
            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00FFCC), checkmarkColor = Color.Black)
          )
          Text("הוסף לשרתים מועדפים", color = Color.LightGray, fontSize = 12.sp)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (host.isNotBlank() && name.isNotBlank()) {
            onSave(
              Connection(
                id = System.currentTimeMillis().toString(),
                name = name,
                host = host,
                port = port.toIntOrNull() ?: 22,
                username = user,
                authType = authType,
                isFavorite = isFav
              )
            )
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC), contentColor = Color.Black)
      ) {
        Text(stringResource(R.string.save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.cancel), color = Color.White)
      }
    },
    containerColor = Color(0xFF141424)
  )
}

@Composable
fun EditServerDialog(
  conn: Connection,
  onDismiss: () -> Unit,
  onSave: (Connection) -> Unit
) {
  var name by remember { mutableStateOf(conn.name) }
  var host by remember { mutableStateOf(conn.host) }
  var port by remember { mutableStateOf(conn.port.toString()) }
  var user by remember { mutableStateOf(conn.username) }
  var isFav by remember { mutableStateOf(conn.isFavorite) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.edit_connection), color = Color.White, fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("שם החיבור") },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF00FFCC),
            focusedLabelColor = Color(0xFF00FFCC),
            unfocusedBorderColor = Color.Gray,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
          ),
          singleLine = true
        )
        OutlinedTextField(
          value = host,
          onValueChange = { host = it },
          label = { Text("מארח / IP") },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF00FFCC),
            focusedLabelColor = Color(0xFF00FFCC),
            unfocusedBorderColor = Color.Gray,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
          ),
          textStyle = TextStyle(fontFamily = FontFamily.Monospace),
          singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("שם משתמש") },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Color(0xFF00FFCC),
              focusedLabelColor = Color(0xFF00FFCC),
              unfocusedBorderColor = Color.Gray,
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White
            ),
            modifier = Modifier.weight(1f),
            singleLine = true
          )
          OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("פורט") },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Color(0xFF00FFCC),
              focusedLabelColor = Color(0xFF00FFCC),
              unfocusedBorderColor = Color.Gray,
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(80.dp),
            singleLine = true
          )
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Checkbox(
            checked = isFav,
            onCheckedChange = { isFav = it },
            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00FFCC), checkmarkColor = Color.Black)
          )
          Text("מועדף", color = Color.LightGray, fontSize = 12.sp)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (host.isNotBlank() && name.isNotBlank()) {
            onSave(
              conn.copy(
                name = name,
                host = host,
                port = port.toIntOrNull() ?: 22,
                username = user,
                isFavorite = isFav
              )
            )
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC), contentColor = Color.Black)
      ) {
        Text(stringResource(R.string.save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.cancel), color = Color.White)
      }
    },
    containerColor = Color(0xFF141424)
  )
}
