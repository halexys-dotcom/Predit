package pt.haconnect.predit.data.local

import androidx.room.TypeConverter
import pt.haconnect.predit.domain.model.CategoriaTurno

class Conversores {
    @TypeConverter
    fun categoriaParaTexto(valor: CategoriaTurno): String = valor.name

    @TypeConverter
    fun textoParaCategoria(valor: String): CategoriaTurno = CategoriaTurno.valueOf(valor)
}
