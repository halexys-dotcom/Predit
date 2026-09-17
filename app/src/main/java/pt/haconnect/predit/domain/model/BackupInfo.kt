package pt.haconnect.predit.domain.model

/**
 * Uma cópia de segurança da base de dados, tal como está no disco do dispositivo.
 *
 * Domínio puro: sem Room nem Android. O caminho é texto e quem o interpreta é o
 * [pt.haconnect.predit.data.backup.BackupManager].
 */
data class BackupInfo(
    val nome: String,           // "predit-20260917-140523.db"
    val caminho: String,        // caminho absoluto no dispositivo
    val tamanhoBytes: Long,
    val criadoEm: Long,         // epoch millis
    val versionBd: Int,         // user_version lido do ficheiro
    val ehAutomatico: Boolean   // true = criado por auto-backup
)
