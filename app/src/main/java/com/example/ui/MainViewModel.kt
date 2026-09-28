package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiBrain
import com.example.data.ai.AssistantAction
import com.example.data.db.AppDatabase
import com.example.data.db.Bookmark
import com.example.data.db.ConversationMessage
import com.example.data.db.FileOperationLog
import com.example.device.BatteryInfo
import com.example.device.DeviceController
import com.example.filemanager.FileItem
import com.example.filemanager.FileManager
import com.example.filemanager.StorageSpaceInfo
import com.example.news.NewsArticle
import com.example.news.NewsService
import com.example.service.voice.VoiceAssistantManager
import com.example.service.voice.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class AppTab {
    ASSISTANT,
    FILES,
    PHONE_CONTROL,
    WEB_NEWS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val voiceManager = VoiceAssistantManager(application)
    val deviceController = DeviceController(application)
    val fileManager = FileManager(application)

    // Current Tab
    private val _currentTab = MutableStateFlow(AppTab.ASSISTANT)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // Sub-tab for Web & News
    private val _webNewsSubTab = MutableStateFlow(0) // 0: News, 1: Browser
    val webNewsSubTab: StateFlow<Int> = _webNewsSubTab.asStateFlow()

    // Voice & Chat State
    val voiceState: StateFlow<VoiceState> = voiceManager.voiceState
    val recognizedSpeech: StateFlow<String> = voiceManager.recognizedText

    val conversationMessages: StateFlow<List<ConversationMessage>> =
        db.conversationDao().getAllMessages()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // File Manager State
    private val _currentDirectory = MutableStateFlow(fileManager.appStorageDir)
    val currentDirectory: StateFlow<File> = _currentDirectory.asStateFlow()

    private val _directoryFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val directoryFiles: StateFlow<List<FileItem>> = _directoryFiles.asStateFlow()

    private val _storageInfo = MutableStateFlow(fileManager.getStorageStats())
    val storageInfo: StateFlow<StorageSpaceInfo> = _storageInfo.asStateFlow()

    private val _fileOperationStatus = MutableStateFlow<String?>(null)
    val fileOperationStatus: StateFlow<String?> = _fileOperationStatus.asStateFlow()

    // Phone & Device State
    val isFlashlightOn: StateFlow<Boolean> = deviceController.isFlashlightOn
    val batteryInfo: StateFlow<BatteryInfo> = deviceController.batteryInfo
    private val _mediaVolumePercent = MutableStateFlow(deviceController.getMediaVolumePercent())
    val mediaVolumePercent: StateFlow<Float> = _mediaVolumePercent.asStateFlow()

    // News & Web State
    private val _newsArticles = MutableStateFlow<List<NewsArticle>>(emptyList())
    val newsArticles: StateFlow<List<NewsArticle>> = _newsArticles.asStateFlow()

    private val _selectedNewsCategory = MutableStateFlow("Top Headlines")
    val selectedNewsCategory: StateFlow<String> = _selectedNewsCategory.asStateFlow()

    private val _isLoadingNews = MutableStateFlow(false)
    val isLoadingNews: StateFlow<Boolean> = _isLoadingNews.asStateFlow()

    private val _browserUrl = MutableStateFlow("https://www.google.com")
    val browserUrl: StateFlow<String> = _browserUrl.asStateFlow()

    val bookmarks: StateFlow<List<Bookmark>> = db.bookmarkDao().getAllBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Voice callback integration
        voiceManager.onSpeechResultReceived = { speech ->
            processCommand(speech)
        }

        // Load initial files & news
        loadCurrentDirectoryFiles()
        loadNews("Top Headlines")

        // Seed initial greeting if chat is empty
        viewModelScope.launch {
            val existing = db.conversationDao().getAllMessages()
            // Welcome message if needed
            db.conversationDao().insertMessage(
                ConversationMessage(
                    text = "Salam! Mein Zoya hoon, aap ki AI voice assistant. Mein phone control, folder copy, real-time web browsing aur fresh news sunane ke liye tayyar hoon. Bolain mein kya karun?",
                    isUser = false,
                    actionType = "GENERAL",
                    actionDetail = "Female Voice Assistant Ready"
                )
            )
        }
    }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun setWebNewsSubTab(index: Int) {
        _webNewsSubTab.value = index
    }

    // Voice interaction
    fun toggleListening() {
        if (voiceState.value == VoiceState.LISTENING) {
            voiceManager.stopListening()
        } else {
            voiceManager.startListening()
        }
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    fun processCommand(input: String) {
        if (input.isBlank()) return
        viewModelScope.launch {
            // Save user message
            db.conversationDao().insertMessage(
                ConversationMessage(
                    text = input,
                    isUser = true,
                    actionType = "USER_PROMPT"
                )
            )

            // Process through AI Brain
            val response = AiBrain.processUserCommand(input)

            // Speak response in female voice
            voiceManager.speak(response.spokenText)

            // Execute structured action
            var actionDetail = response.displayDetails
            when (val action = response.action) {
                is AssistantAction.CallPhone -> {
                    val result = deviceController.makeCall(action.target)
                    actionDetail = result
                }
                is AssistantAction.CopyFolder -> {
                    _currentTab.value = AppTab.FILES
                    val result = fileManager.executeVoiceFolderCopy(action.source, action.target)
                    actionDetail = result.second
                    db.fileLogDao().insertLog(
                        FileOperationLog(
                            operation = "COPY",
                            sourcePath = action.source,
                            destinationPath = action.target,
                            status = if (result.first) "SUCCESS" else "FAILED"
                        )
                    )
                    loadCurrentDirectoryFiles()
                    _storageInfo.value = fileManager.getStorageStats()
                }
                is AssistantAction.GetNews -> {
                    _currentTab.value = AppTab.WEB_NEWS
                    _webNewsSubTab.value = 0
                    loadNews(action.category)
                }
                is AssistantAction.WebBrowse -> {
                    _currentTab.value = AppTab.WEB_NEWS
                    _webNewsSubTab.value = 1
                    loadUrl(action.urlOrQuery)
                }
                is AssistantAction.ToggleFlashlight -> {
                    deviceController.toggleFlashlight(action.enable)
                }
                is AssistantAction.CheckBattery -> {
                    val info = deviceController.updateBatteryStatus()
                    actionDetail = "Battery: ${info.level}%, ${if (info.isCharging) "Charging" else "Discharging"}"
                    voiceManager.speak("Aap ki battery ${info.level} percent hai, aur ${if (info.isCharging) "charging ho rahi hai" else "normal chal rahi hai"}.")
                }
                is AssistantAction.AdjustVolume -> {
                    deviceController.adjustVolume(action.direction)
                    _mediaVolumePercent.value = deviceController.getMediaVolumePercent()
                }
                is AssistantAction.OpenApp -> {
                    deviceController.launchAppByName(action.appName)
                }
                AssistantAction.None -> {}
            }

            // Save Assistant response
            db.conversationDao().insertMessage(
                ConversationMessage(
                    text = response.spokenText,
                    isUser = false,
                    actionType = response.action.javaClass.simpleName,
                    actionDetail = actionDetail
                )
            )
        }
    }

    // File Manager operations
    fun loadCurrentDirectoryFiles() {
        viewModelScope.launch {
            _directoryFiles.value = fileManager.listFilesInDirectory(_currentDirectory.value)
        }
    }

    fun navigateToDirectory(directory: File) {
        if (directory.isDirectory && directory.canRead()) {
            _currentDirectory.value = directory
            loadCurrentDirectoryFiles()
        }
    }

    fun navigateUp() {
        val parent = _currentDirectory.value.parentFile
        if (parent != null && parent.canRead()) {
            _currentDirectory.value = parent
            loadCurrentDirectoryFiles()
        }
    }

    fun copyFolderInteractive(sourceDirName: String, targetDirName: String) {
        viewModelScope.launch {
            _fileOperationStatus.value = "Copying from $sourceDirName to $targetDirName..."
            val result = fileManager.executeVoiceFolderCopy(sourceDirName, targetDirName)
            _fileOperationStatus.value = result.second
            voiceManager.speak(result.second)
            loadCurrentDirectoryFiles()
            _storageInfo.value = fileManager.getStorageStats()
        }
    }

    fun createNewFolder(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                val success = fileManager.createNewFolder(_currentDirectory.value, name.trim())
                if (success) {
                    loadCurrentDirectoryFiles()
                    _fileOperationStatus.value = "Folder '$name' created."
                }
            }
        }
    }

    fun deleteItem(fileItem: FileItem) {
        viewModelScope.launch {
            val file = File(fileItem.path)
            fileManager.deleteFileOrDirectory(file)
            loadCurrentDirectoryFiles()
            _storageInfo.value = fileManager.getStorageStats()
        }
    }

    // Device control
    fun toggleFlashlight() {
        deviceController.toggleFlashlight()
    }

    fun makeCall(target: String) {
        val detail = deviceController.makeCall(target)
        viewModelScope.launch {
            db.conversationDao().insertMessage(
                ConversationMessage(
                    text = "Call milao: $target",
                    isUser = true,
                    actionType = "CALL"
                )
            )
            db.conversationDao().insertMessage(
                ConversationMessage(
                    text = "$target ko call mila rahi hoon...",
                    isUser = false,
                    actionType = "CALL",
                    actionDetail = detail
                )
            )
        }
    }

    fun setMediaVolume(percent: Float) {
        deviceController.setMediaVolumePercent(percent)
        _mediaVolumePercent.value = percent
    }

    fun refreshBattery() {
        deviceController.updateBatteryStatus()
    }

    // News
    fun loadNews(category: String) {
        _selectedNewsCategory.value = category
        _isLoadingNews.value = true
        viewModelScope.launch {
            val articles = NewsService.getNews(category)
            _newsArticles.value = articles
            _isLoadingNews.value = false
        }
    }

    fun readNewsSummaryAloud() {
        val summary = NewsService.generateSpeechSummary(_newsArticles.value, _selectedNewsCategory.value)
        voiceManager.speak(summary)
    }

    fun readArticleAloud(article: NewsArticle) {
        voiceManager.speak("${article.title}. ${article.description}")
    }

    // Web Browser
    fun loadUrl(urlOrQuery: String) {
        val finalUrl = when {
            urlOrQuery.startsWith("http://") || urlOrQuery.startsWith("https://") -> urlOrQuery
            urlOrQuery.contains(".") && !urlOrQuery.contains(" ") -> "https://$urlOrQuery"
            else -> "https://www.google.com/search?q=${urlOrQuery.replace(" ", "+")}"
        }
        _browserUrl.value = finalUrl
    }

    fun addBookmark(title: String, url: String) {
        viewModelScope.launch {
            db.bookmarkDao().insertBookmark(Bookmark(title = title, url = url))
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            db.conversationDao().clearHistory()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.destroy()
    }
}
