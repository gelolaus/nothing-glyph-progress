package dev.gelo.glyphprogress.tile

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.gelo.glyphprogress.GlyphProgressApp
import dev.gelo.glyphprogress.R
import dev.gelo.glyphprogress.core.GlyphPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Lets the current Live Update be checked, and the pin cycled to the next candidate,
 * without unlocking the phone or opening the app.
 */
class GlyphProgressTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val app = application as? GlyphProgressApp ?: return
        val job = SupervisorJob()
        val tileScope = CoroutineScope(Dispatchers.Main.immediate + job)
        scope = tileScope
        tileScope.launch {
            combine(app.repository.board, app.repository.manual) { board, manual -> board to manual }
                .collect { (board, manual) -> render(board.active?.phase, board.active?.percent, board.active?.title, manual != null) }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        (application as? GlyphProgressApp)?.repository?.pinNext()
    }

    private fun render(phase: GlyphPhase?, percent: Int?, title: String?, previewing: Boolean) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
        when {
            previewing -> {
                tile.label = "Glyph Progress"
                tile.subtitle = "Preview"
                tile.state = Tile.STATE_ACTIVE
            }
            phase == null -> {
                tile.label = "Glyph Progress"
                tile.subtitle = "No Live Update"
                tile.state = Tile.STATE_INACTIVE
            }
            else -> {
                tile.label = title?.takeIf { it.isNotBlank() } ?: "Glyph Progress"
                tile.subtitle = when (phase) {
                    GlyphPhase.Indeterminate -> "Searching"
                    GlyphPhase.Complete -> "Done"
                    GlyphPhase.Progress -> "${percent ?: 0}%"
                }
                tile.state = Tile.STATE_ACTIVE
            }
        }
        tile.updateTile()
    }
}
