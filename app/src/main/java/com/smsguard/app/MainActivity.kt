
package com.smsguard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log

private val Navy = Color(0xFF142B4A)
private val Blue = Color(0xFF3478F6)
private val Background = Color(0xFFF5F7FB)
private val Green = Color(0xFF159B72)
private val Red = Color(0xFFD94A4A)
private val Muted = Color(0xFF78869A)

private enum class AppScreen {
    HOME, ANALYZE, RESULT, HISTORY, SETTINGS
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("TOKENIZER_TEST", "Début du test tokenizer")
        TokenizerTest.run(this)
        TokenizerTest.run(this)
        setContent {
            SmsGuardTheme {
                SmsGuardApp()
            }
        }
    }
}

@Composable
private fun SmsGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            secondary = Green,
            background = Background,
            surface = Color.White,
            error = Red
        ),
        content = content
    )
}

@Composable
private fun SmsGuardApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var smsText by remember { mutableStateOf("") }
    var resultLabel by remember { mutableStateOf("") }
    var suspicious by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var history by remember {
        mutableStateOf(SmsHistoryRepository.load(context))
    }

    fun navigate(screen: AppScreen) {
        currentScreen = screen
        errorMessage = null
    }

    fun analyzeSms() {
        if (smsText.isBlank()) {
            errorMessage = "Veuillez saisir un SMS à analyser."
            return
        }

        isAnalyzing = true
        errorMessage = null

        scope.launch {
            try {
                val prediction = withContext(Dispatchers.IO) {
                    SmsClassifierEngine.analyze(context, smsText.trim())
                }

                resultLabel = prediction.label
                suspicious = prediction.isSuspicious

                val item = SmsHistoryItem(
                    id = System.currentTimeMillis(),
                    sender = "Analyse manuelle",
                    message = smsText.trim(),
                    label = prediction.label,
                    timestamp = System.currentTimeMillis()
                )

                withContext(Dispatchers.IO) {
                    SmsHistoryRepository.save(context, item)
                }

                history = withContext(Dispatchers.IO) {
                    SmsHistoryRepository.load(context)
                }

                currentScreen = AppScreen.RESULT
            } catch (e: Exception) {
                errorMessage = "Erreur pendant l'analyse : ${e.message ?: "erreur inconnue"}"
            } finally {
                isAnalyzing = false
            }
        }
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(
                containerColor = Color.White
            ) {
                NavigationBarItem(
                    selected = currentScreen == AppScreen.HOME,
                    onClick = { navigate(AppScreen.HOME) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Accueil") },
                    label = { Text("Accueil") }
                )

                NavigationBarItem(
                    selected = currentScreen == AppScreen.ANALYZE,
                    onClick = { navigate(AppScreen.ANALYZE) },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Analyser") },
                    label = { Text("Analyser") }
                )

                NavigationBarItem(
                    selected = currentScreen == AppScreen.HISTORY,
                    onClick = {
                        history = SmsHistoryRepository.load(context)
                        navigate(AppScreen.HISTORY)
                    },
                    icon = { Icon(Icons.Default.History, contentDescription = "Historique") },
                    label = { Text("Historique") }
                )

                NavigationBarItem(
                    selected = currentScreen == AppScreen.SETTINGS,
                    onClick = { navigate(AppScreen.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Paramètres") },
                    label = { Text("Paramètres") }
                )
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            when (currentScreen) {

                AppScreen.HOME -> {
                    HomeScreen(
                        onAnalyze = { navigate(AppScreen.ANALYZE) },
                        onHistory = {
                            history = SmsHistoryRepository.load(context)
                            navigate(AppScreen.HISTORY)
                        }
                    )
                }

                AppScreen.ANALYZE -> {
                    AnalyzeScreen(
                        smsText = smsText,
                        onSmsChange = {
                            smsText = it
                            errorMessage = null
                        },
                        isAnalyzing = isAnalyzing,
                        errorMessage = errorMessage,
                        onAnalyze = { analyzeSms() }
                    )
                }

                AppScreen.RESULT -> {
                    ResultScreen(
                        message = smsText,
                        label = resultLabel,
                        isSuspicious = suspicious,
                        onBack = { navigate(AppScreen.ANALYZE) },
                        onHome = { navigate(AppScreen.HOME) }
                    )
                }

                AppScreen.HISTORY -> {
                    HistoryScreen(
                        items = history,
                        onClear = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    SmsHistoryRepository.clear(context)
                                }
                                history = emptyList()
                            }
                        }
                    )
                }

                AppScreen.SETTINGS -> {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun Header(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Navy)
            .padding(horizontal = 22.dp, vertical = 26.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(29.dp)
                    )
                }
            }

            Spacer(Modifier.width(13.dp))

            Column {
                Text(
                    "SMS Guard",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Protection intelligente contre les SMS suspects",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(25.dp))

        Text(
            title,
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(5.dp))

        Text(
            subtitle,
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 13.sp
        )
    }
}

@Composable
private fun HomeScreen(
    onAnalyze: () -> Unit,
    onHistory: () -> Unit
) {
    Column {
        Header(
            title = "Bienvenue !",
            subtitle = "Votre sécurité commence ici."
        )

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = Blue,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Votre protection SMS",
                            color = Navy,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Analysez vos messages pour identifier les SMS potentiellement frauduleux ou indésirables.",
                        color = Muted,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = onAnalyze,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Analyser un SMS")
                    }
                }
            }

            Text(
                "Fonctionnalités",
                color = Navy,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            FeatureCard(
                title = "Analyse intelligente",
                description = "Le modèle IA examine le contenu de vos messages.",
                icon = "🔎"
            )

            FeatureCard(
                title = "Détection automatique",
                description = "Les SMS reçus peuvent être analysés automatiquement.",
                icon = "🛡️"
            )

            FeatureCard(
                title = "Historique",
                description = "Consultez les résultats de vos analyses précédentes.",
                icon = "📋",
                onClick = onHistory
            )
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    description: String,
    icon: String,
    onClick: (() -> Unit)? = null
) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 27.sp)
            Spacer(Modifier.width(15.dp))
            Column {
                Text(
                    title,
                    color = Navy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun AnalyzeScreen(
    smsText: String,
    onSmsChange: (String) -> Unit,
    isAnalyzing: Boolean,
    errorMessage: String?,
    onAnalyze: () -> Unit
) {
    Column {
        Header(
            title = "Analyser un SMS",
            subtitle = "Collez le texte du message à examiner."
        )

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Contenu du message",
                        color = Navy,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = smsText,
                        onValueChange = onSmsChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp),
                        placeholder = {
                            Text("Écrivez ou collez votre SMS ici...")
                        },
                        shape = RoundedCornerShape(12.dp),
                        minLines = 5,
                        maxLines = 9
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        "${smsText.length} caractères",
                        color = Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            if (errorMessage != null) {
                Text(
                    errorMessage,
                    color = Red,
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = onAnalyze,
                enabled = !isAnalyzing && smsText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(13.dp)
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(21.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Analyse en cours...")
                } else {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Spacer(Modifier.width(9.dp))
                    Text("Lancer l'analyse")
                }
            }

            Text(
                "Conseil : utilisez des exemples fictifs pour vos tests et évitez de partager des informations personnelles.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun ResultScreen(
    message: String,
    label: String,
    isSuspicious: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    val resultColor = if (isSuspicious) Red else Green
    val resultTitle = if (isSuspicious) {
        "SMS potentiellement suspect"
    } else {
        "SMS classé comme normal"
    }

    Column {
        Header(
            title = "Résultat de l'analyse",
            subtitle = "Voici la classification fournie par le modèle."
        )

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = resultColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(76.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = resultColor,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(17.dp))

                    Text(
                        resultTitle,
                        color = resultColor,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Classe prédite : $label",
                        color = Muted,
                        fontSize = 13.sp
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Message analysé",
                        color = Navy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        message,
                        color = Muted,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                }
            }

            if (isSuspicious) {
                Text(
                    "Prudence : ne cliquez pas sur les liens et ne communiquez pas de codes ou d'informations personnelles à un expéditeur inconnu.",
                    color = Red,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            } else {
                Text(
                    "Cette classification ne garantit pas que le message est sans risque. Restez vigilant face aux demandes inhabituelles.",
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Analyser un autre SMS")
            }

            OutlinedButton(
                onClick = onHome,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Retour à l'accueil")
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    items: List<SmsHistoryItem>,
    onClear: () -> Unit
) {
    Column {
        Header(
            title = "Historique",
            subtitle = "Retrouvez les messages déjà analysés."
        )

        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${items.size} analyse(s)",
                    color = Navy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                if (items.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = Red
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Effacer", color = Red)
                    }
                }
            }

            if (items.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(17.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = Muted,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Aucun historique",
                            color = Navy,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Vos analyses apparaîtront ici.",
                            color = Muted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items.sortedByDescending { it.timestamp }.forEach { item ->
                    HistoryItemCard(item)
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(item: SmsHistoryItem) {
    val suspicious = item.label.equals("spam", true) ||
            item.label.equals("suspect", true) ||
            item.label.equals("fraud", true)

    val color = if (suspicious) Red else Green

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.sender,
                    color = Navy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Surface(
                    color = color.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        item.label,
                        color = color,
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 5.dp
                        ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(9.dp))

            Text(
                item.message,
                color = Muted,
                fontSize = 13.sp,
                maxLines = 3
            )

            Spacer(Modifier.height(8.dp))

            Text(
                SimpleDateFormat(
                    "dd/MM/yyyy HH:mm",
                    Locale.getDefault()
                ).format(Date(item.timestamp)),
                color = Muted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column {
        Header(
            title = "Paramètres",
            subtitle = "Informations sur SMS Guard."
        )

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Card(
                shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "À propos de l'application",
                        color = Navy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "SMS Guard est une application de démonstration qui utilise un modèle d'intelligence artificielle pour classifier les SMS en messages normaux ou potentiellement suspects.",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Version 1.0",
                        color = Blue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Confidentialité",
                        color = Navy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Les résultats dépendent du modèle installé. Une classification normale ne garantit pas l'absence de fraude. Vérifiez toujours l'identité de l'expéditeur.",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
