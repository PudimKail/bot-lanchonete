package com.pudimkail.botlanchonete.pedido;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class Dinheiro {

    private Dinheiro() {
    }

    /** 18 -> "R$ 18,00" */
    public static String formatar(BigDecimal valor) {
        NumberFormat formato = NumberFormat.getNumberInstance(Locale.of("pt", "BR"));
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);
        return "R$ " + formato.format(valor);
    }
}
