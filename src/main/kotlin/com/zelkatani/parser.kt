package com.zelkatani

import com.zelkatani.Instruction.*
import com.zelkatani.LabeledInstruction.*
import java.io.OutputStream
import com.zelkatani.Token.KU as LF
import com.zelkatani.Token.q as S
import com.zelkatani.Token.Q as T

class Parser(private val out: OutputStream = System.out) {

    infix fun parse(tokens: List<Token>): Program {
        val instructions = mutableListOf<Instruction>()
        val iterator = tokens.iterator()

        val labels = mutableMapOf<Int, Label>()
        var labelInScope: Label? = null

        while (iterator.hasNext()) {
            val token = iterator.next()

            val instruction = when (token) {
                S -> parseStack(iterator)
                T -> {
                    val next = iterator.next()

                    when (next) {
                        S -> parseArithmetic(iterator)
                        T -> parseHeap(iterator)
                        LF -> parseIO(iterator)
                    }
                }

                LF -> parseFlowControl(iterator)
            }

            when {
                instruction is MarkLocationInstruction -> {
                    if (instruction.labelId in labels) {
                        throw ParserException("Labels cannot have the same id.")
                    }

                    val label = Label(instruction.labelId, instruction.stringForm)
                    labels[instruction.labelId] = label
                    labelInScope = label
                }

                labelInScope != null -> labelInScope.add(instruction)
                else -> instructions.add(instruction)
            }
        }

        return Program(instructions, labels, out)
    }

    private fun parseStack(iterator: Iterator<Token>): Instruction {
        val token = iterator.next()

        return when (token) {
            S -> {
                val number = parseNumber(iterator)
                PushNumberInstruction(number)
            }

            LF -> {
                val next = iterator.next()

                when (next) {
                    S -> DuplicateInstruction()
                    T -> SwapInstruction()
                    LF -> DiscardInstruction()
                }
            }

            else -> throw ParserException()
        }
    }

    private fun parseNumber(iterator: Iterator<Token>): Int {
        val sign = when (iterator.next()) {
            S -> 1
            T -> -1
            else -> throw ParserException()
        }

        var number = 0
        var next = iterator.next()

        // I'm more in favor of throwing an error here, since the number is really just a sign with no value.
        if (next == LF) {
            return 0
        }

        while (next != LF) {
            number = number shl 1

            if (next == T) {
                number++
            }

            next = iterator.next()
        }

        return sign * number
    }

    private fun parseArithmetic(iterator: Iterator<Token>): Instruction {
        val token = iterator.next()

        return when (token) {
            S -> {
                val next = iterator.next()

                when (next) {
                    S -> AdditionInstruction()
                    T -> SubtractionInstruction()
                    LF -> MultiplicationInstruction()
                }
            }

            T -> {
                val next = iterator.next()

                when (next) {
                    S -> IntDivisionInstruction()
                    T -> ModuloInstruction()
                    else -> throw ParserException()
                }
            }

            else -> throw ParserException()
        }
    }

    private fun parseHeap(iterator: Iterator<Token>): Instruction {
        val token = iterator.next()

        return when (token) {
            S -> StoreInstruction()
            T -> RetrieveInstruction()
            else -> throw ParserException()
        }
    }

    private fun parseFlowControl(iterator: Iterator<Token>): Instruction {
        val token = iterator.next()

        return when (token) {
            S -> {
                val next = iterator.next()
                val labelId = parseNumber(iterator)

                when (next) {
                    S -> MarkLocationInstruction(labelId)
                    T -> CallSubroutineInstruction(labelId)
                    LF -> JumpInstruction(labelId)
                }
            }

            T -> {
                val next = iterator.next()

                when (next) {
                    S -> {
                        val labelId = parseNumber(iterator)
                        JumpZeroInstruction(labelId)
                    }

                    T -> {
                        val labelId = parseNumber(iterator)
                        JumpNegativeInstruction(labelId)
                    }

                    else -> EndSubroutineInstruction()
                }
            }

            LF -> {
                val next = iterator.next()

                if (next == LF) {
                    EndProgramInstruction()
                } else {
                    throw ParserException()
                }
            }
        }
    }

    private fun parseIO(iterator: Iterator<Token>): Instruction {
        val token = iterator.next()

        return when (token) {
            S -> {
                val next = iterator.next()

                when (next) {
                    S -> PrintCharInstruction()
                    T -> PrintNumberInstruction()
                    else -> throw ParserException()
                }
            }

            T -> {
                val next = iterator.next()

                when (next) {
                    S -> ReadCharInstruction()
                    T -> ReadNumberInstruction()
                    else -> throw ParserException()
                }
            }

            else -> throw ParserException()
        }
    }
}

// A label should contain the instructions inside them to make calling them easier
data class Label(private val id: Int, val stringForm: String) {
    private val _instructions = mutableListOf<Instruction>()

    val instructions: List<Instruction>
        get() = _instructions

    override fun toString() = buildString {
        append("label $id:\n")
        _instructions.forEach {
            appendln("Q$it")
        }
    }

    fun add(instruction: Instruction) = _instructions.add(instruction)
}

sealed class Instruction(private val shorthand: String, val stringForm: String) {

    // Stack Manipulation
    class PushNumberInstruction(val number: Int) : Instruction("push $number", "  ${number.toWhitespace()}")
    class DuplicateInstruction : Instruction("dup", "q\nq")
    class SwapInstruction : Instruction("swap", "q\nQ")
    class DiscardInstruction : Instruction("discard", "q\n\n")

    // Arithmetic
    class AdditionInstruction : Instruction("add", "Qqqq")
    class SubtractionInstruction : Instruction("sub", "QqqQ")
    class MultiplicationInstruction : Instruction("mul", "Qqq\n")
    class IntDivisionInstruction : Instruction("div", "QqQq")
    class ModuloInstruction : Instruction("mod", "QqQQ")

    // Heap Access
    class StoreInstruction : Instruction("store", "QQq")
    class RetrieveInstruction : Instruction("retrieve", "QQQ")

    // Flow control, the rest of it is inside LabeledInstruction
    class EndSubroutineInstruction : Instruction("return", "\nQ\n")
    class EndProgramInstruction : Instruction("end", "\n\n\n")

    // I/O
    class PrintCharInstruction : Instruction("printchar", "Q\nqq")
    class PrintNumberInstruction : Instruction("printnum", "Q\nqQ")
    class ReadCharInstruction : Instruction("readchar", "Q\nQq")
    class ReadNumberInstruction : Instruction("readnum", "Q\nQQ")

    override fun toString() = shorthand
}

// All instructions that require a label id to be passed into them
sealed class LabeledInstruction(
        val labelId: Int,
        shorthand: String,
        stringForm: String
) : Instruction(shorthand, stringForm + labelId.toWhitespace()) {

    internal class MarkLocationInstruction(labelId: Int) : LabeledInstruction(labelId, "$labelId:", "\nqq")
    class CallSubroutineInstruction(labelId: Int) : LabeledInstruction(labelId, "call $labelId", "\nqQ")
    class JumpInstruction(labelId: Int) : LabeledInstruction(labelId, "jump $labelId", "\nq\n")
    class JumpZeroInstruction(labelId: Int) : LabeledInstruction(labelId, "jumpzero $labelId", "\nQq")
    class JumpNegativeInstruction(labelId: Int) : LabeledInstruction(labelId, "jumpneg $labelId", "\nQQ")
}

class ParserException(override val message: String = "Error parsing program.") : Exception(message)

fun Int.toWhitespace() = buildString {
    val value = this@toWhitespace
    append(if (value < 0) 'Q' else 'q')
    val asString = Integer.toBinaryString(value)
    // Doing appendln doesn't work.
    append(asString.replace('1', 'Q').replace('0', 'q')).append('\n')
}
