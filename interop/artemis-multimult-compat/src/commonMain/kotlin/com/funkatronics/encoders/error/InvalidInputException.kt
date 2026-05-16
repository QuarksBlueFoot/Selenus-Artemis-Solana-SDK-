package com.funkatronics.encoders.error

sealed class InvalidInputException(message: String? = null) : IllegalArgumentException("Invalid input: $message") {
    class InvalidBase(message: String) : InvalidInputException(message)

    class InvalidAlphabet(base: Int, alphabet: String) :
        InvalidInputException("Invalid alphabet for base: alphabet length: ${alphabet.length}, base: $base")

    class InvalidCharacter(val character: Char, val position: Int) :
        InvalidInputException("Invalid character {$character} at index $position")
}