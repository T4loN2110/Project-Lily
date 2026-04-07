package ui.features.chat

import androidx.compose.runtime.Immutable
import model.Message

class ChatContract {
    @Immutable
    data class State(
        val error: String? = null,
        val messages: List<Message> = listOf(),
        val inputText: String = "",
        val isGenerating: Boolean = false,
        val streamingText: String = ""
    )

    sealed class Intent {
        data class InputTextChanged(val text: String) : Intent()
        data object SendMessage : Intent()
        data object ClearError : Intent()
    }

    sealed class Effect {
        data class ShowError(val message: String) : Effect()
    }
}