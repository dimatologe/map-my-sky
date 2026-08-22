package com.codex.starmapper.solve

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Prozess-Singleton, der UI und [SolveForegroundService] verbindet.
 *
 * Die eigentliche Solve-Coroutine läuft weiterhin in der Compose-Scope (überlebt
 * Backgrounding & Rotation dank `configChanges`); der Foreground-Service hält nur
 * den Prozess auf Vordergrund-Priorität, damit Android dem Solve nicht das Netz/CPU
 * kappt, und zeigt Fortschritt + Abbrechen in einer Notification.
 */
object SolveController {

    // success zeigt an, ob DIESES Ende ein echter Solve-Erfolg war (AstapOperationState.Solved),
    // nicht nur "nicht mehr aktiv" -- ein expliziter Abbruch oder Fehlschlag setzt es nicht. Der
    // Service nutzt es, um NUR bei echtem Erfolg eine "fertig"-Notification zu posten statt nur
    // sein eigenes (stummes) Icon verschwinden zu lassen.
    data class Status(val active: Boolean, val title: String, val text: String, val success: Boolean = false)

    private val _status = MutableStateFlow(Status(active = false, title = "", text = ""))
    val status: StateFlow<Status> = _status.asStateFlow()

    @Volatile
    private var cancelHandler: (() -> Unit)? = null

    /** Vom UI beim Solve-Start aufgerufen: Status aktiv setzen + Abbruch-Callback registrieren. */
    fun begin(title: String, text: String, onCancel: () -> Unit) {
        cancelHandler = onCancel
        _status.value = Status(active = true, title = title, text = text)
    }

    /** Fortschrittstext aktualisieren (nur wenn ein Solve aktiv ist). */
    fun update(text: String) {
        val current = _status.value
        if (current.active) {
            _status.value = current.copy(text = text)
        }
    }

    /** Vom UI im finally-Block: Solve beendet -> Service darf sich beenden. */
    fun finish(success: Boolean = false) {
        cancelHandler = null
        _status.value = _status.value.copy(active = false, success = success)
    }

    /** Von der Notification-Aktion „Abbrechen" über den Service ausgelöst. */
    fun requestCancel() {
        cancelHandler?.invoke()
    }
}
