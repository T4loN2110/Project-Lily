package ui.features.chat

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import model.Message

import com.t4lon.lily.lily_impl.LilyClient

class ChatScreenModel(
    private val lily: LilyClient
) : StateScreenModel<ChatContract.State>(
    ChatContract.State()
) {
    private val _effects = MutableSharedFlow<ChatContract.Effect>()
    val effects = _effects.asSharedFlow()

    fun handleIntent(intent: ChatContract.Intent) {
        when (intent) {
            is ChatContract.Intent.InputTextChanged -> {
                mutableState.update { it.copy(inputText = intent.text) }
            }
            is ChatContract.Intent.SendMessage -> {
                sendMessage()
            }
            is ChatContract.Intent.ClearError -> {
                mutableState.update { it.copy(error = null) }
            }
        }
    }

    private fun sendMessage() {
        val userMsg = state.value.inputText.trim()
        if (userMsg.isBlank()) return

        screenModelScope.launch {
            try {
                // Create user message
                val userMessage = Message(
                    text = userMsg,
                    role = "user",
                    timestamp = System.currentTimeMillis()
                )

                // Clear input and set generating state
                mutableState.update {
                    it.copy(
                        inputText = "",
                        isGenerating = true,
                        error = null
                    )
                }

                // Add user message to list
                mutableState.update {
                    it.copy(messages = it.messages + userMessage)
                }

                // Get response from Lily
                val response = lily.chat(userMsg)

                // Create bot message
                val botMessage = Message(
                    text = response,
                    role = "assistant",
                    timestamp = System.currentTimeMillis()
                )

                // Add bot message and stop generating
                mutableState.update {
                    it.copy(
                        messages = it.messages + botMessage,
                        isGenerating = false
                    )
                }
            } catch (e: Exception) {
                mutableState.update {
                    it.copy(
                        isGenerating = false,
                        error = e.message ?: "An error occurred"
                    )
                }
                _effects.emit(ChatContract.Effect.ShowError(e.message ?: "An error occurred"))
            }
        }
    }
}