package com.zelkatani

import com.zelkatani.Token.*
import java.io.File

enum class Token {
    q, Q, KU
}

class Lexer {
    infix fun lex(text: String): List<Token> {
        val lexed = mutableListOf<Token>()

        text.forEach {
            when (it) {
                'q' -> lexed.add(q)
                'Q' -> lexed.add(Q)
                '\n' -> lexed.add(KU)
            }
        }

        return lexed
    }

    infix fun lex(file: File): List<Token> {
        if (file.isDirectory) {
            throw IllegalArgumentException("Expected file, but got a directory.")
        }

        val text = file.readText()
        return lex(text)
    }
}
