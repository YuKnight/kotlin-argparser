// Copyright © 2016 Laurence Gonsalves
//
// This file is part of kotlin-argparser, a library which can be found at
// http://github.com/xenomachina/kotlin-argparser
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by the
// Free Software Foundation; either version 2.1 of the License, or (at your
// option) any later version.
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
// for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with this library; if not, see http://www.gnu.org/licenses/

package com.xenomachina.argparser

import com.xenomachina.common.orElse
import io.kotlintest.matchers.Matcher
import io.kotlintest.matchers.Result
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.specs.FunSpec
import java.io.File
import java.io.StringWriter

val TEST_HELP = "test help message"

fun parserOf(
        vararg args: String,
        mode: ArgParser.Mode = ArgParser.Mode.GNU,
        helpFormatter: HelpFormatter? = DefaultHelpFormatter()
) = ArgParser(args, mode, helpFormatter)

enum class Color { RED, GREEN, BLUE }

open class Shape
class Rectangle(val s: String) : Shape()
class Circle : Shape()

// TODO: remove this once kotlintest releases fix
inline fun <reified T : Any> beOfType() = object : Matcher<Any> {
    val exceptionClassName = T::class.qualifiedName

    override fun test(value: Any) =
            Result(value.javaClass == T::class.java, "$value should be of type $exceptionClassName")
}

/**
 * Helper function for getting the static (not runtime) type of an expression. This is useful for verifying that the
 * inferred type of an expression is what you think it should be. For example:
 *
 *     staticType(actuallyACircle) shouldBe Shape::class
 */
inline fun <reified T : Any> staticType(@Suppress("UNUSED_PARAMETER") x: T) = T::class

class ArgParserTest : FunSpec({
    test("ArglessShortOptions") {
        class Args(parser: ArgParser) {
            val xyz by parser.option<MutableList<String>>("-x", "-y", "-z",
                    errorName = "VALUE_NAME",
                    help = TEST_HELP,
                    usageArgument = "ARG_NAME") {
                value.orElse { mutableListOf<String>() }.apply {
                    add("$optionName")
                }
            }
        }

        Args(parserOf("-x", "-y", "-z", "-z", "-y")).xyz shouldBe listOf("-x", "-y", "-z", "-z", "-y")

        Args(parserOf("-xyz")).xyz shouldBe listOf("-x", "-y", "-z")
    }

    test("ShortOptionsWithArgs") {
        class Args(parser: ArgParser) {
            val a by parser.flagging("-a", help = TEST_HELP)
            val b by parser.flagging("-b", help = TEST_HELP)
            val c by parser.flagging("-c", help = TEST_HELP)
            val xyz by parser.option<MutableList<String>>("-x", "-y", "-z",
                    errorName = "VALUE_NAME",
                    usageArgument = "ARG_NAME", help = TEST_HELP) {
                value.orElse { mutableListOf<String>() }.apply {
                    add("$optionName:${next()}")
                }
            }
        }

        // Test with value as separate arg
        Args(parserOf("-x", "0", "-y", "1", "-z", "2", "-z", "3", "-y", "4")).xyz shouldBe listOf("-x:0", "-y:1", "-z:2", "-z:3", "-y:4")

        // Test with value concatenated
        Args(parserOf("-x0", "-y1", "-z2", "-z3", "-y4")).xyz shouldBe listOf("-x:0", "-y:1", "-z:2", "-z:3", "-y:4")

        // Test with = between option and value. Note that the "=" is treated as part of the option value for short options.
        Args(parserOf("-x=0", "-y=1", "-z=2", "-z=3", "-y=4")).xyz shouldBe listOf("-x:=0", "-y:=1", "-z:=2", "-z:=3", "-y:=4")

        // Test chained options. Note that an option with arguments must be last in the chain
        val chain1 = Args(parserOf("-abxc"))
        chain1.a shouldBe true
        chain1.b shouldBe true
        chain1.c shouldBe false
        chain1.xyz shouldBe listOf("-x:c")

        val chain2 = Args(parserOf("-axbc"))
        chain2.a shouldBe true
        chain2.b shouldBe false
        chain2.c shouldBe false
        chain2.xyz shouldBe listOf("-x:bc")
    }

    test("MixedShortOptions") {
        class Args(parser: ArgParser) {
            val def by parser.option<MutableList<String>>("-d", "-e", "-f",
                    errorName = "VALUE_NAME",
                    usageArgument = "ARG_NAME",
                    help = TEST_HELP) {
                value.orElse { mutableListOf<String>() }.apply {
                    add("$optionName")
                }
            }
            val abc by parser.option<MutableList<String>>("-a", "-b", "-c",
                    errorName = "VALUE_NAME",
                    usageArgument = "ARG_NAME",
                    help = TEST_HELP) {
                value.orElse { mutableListOf<String>() }.apply {
                    add("$optionName")
                }
            }
        }

        Args(parserOf("-adbefccbafed")).run {
            def shouldBe listOf("-d", "-e", "-f", "-f", "-e", "-d")
            abc shouldBe listOf("-a", "-b", "-c", "-c", "-b", "-a")
        }
    }

    test("MixedShortOptionsWithArgs") {
        class Args(parser: ArgParser) {
            val def by parser.option<MutableList<String>>("-d", "-e", "-f",
                    errorName = "VALUE_NAME",
                    usageArgument = "ARG_NAME",
                    help = TEST_HELP) {
                value.orElse { mutableListOf<String>() }.apply {
                    add("$optionName")
                }
            }
            val abc by parser.option<MutableList<String>>("-a", "-b", "-c",
                    errorName = "VALUE_NAME",
                    usageArgument = "ARG_NAME",
                    help = TEST_HELP) {
                value.orElse { mutableListOf<String>() }.apply {
                    add("$optionName")
                }
            }
            val xyz by parser.option<MutableList<String>>("-x", "-y", "-z",
                    errorName = "VALUE_NAME",
                    usageArgument = "ARG_NAME",
                    help = TEST_HELP) {
                value.orElse { mutableListOf<String>() }.apply {
                    add("$optionName:${next()}")
                }
            }
        }

        Args(parserOf("-adecfy5", "-x0", "-bzxy")).run {
            abc shouldBe listOf("-a", "-c", "-b")
            def shouldBe listOf("-d", "-e", "-f")
            xyz shouldBe listOf("-y:5", "-x:0", "-z:xy")
        }
    }

    test("ArglessLongOptions") {
        class Args(parser: ArgParser) {
            val xyz by parser.option<MutableList<String>>("--xray", "--yellow", "--zebra",
                    errorName = "ARG_NAME",
                    usageArgument = null,
                    help = TEST_HELP) {
                value.orElse { mutableListOf<String>() }.apply {
                    add("$optionName")
                }
            }
        }

        Args(parserOf("--xray", "--yellow", "--zebra", "--zebra", "--yellow")).xyz shouldBe listOf("--xray", "--yellow", "--zebra", "--zebra", "--yellow")

        Args(parserOf("--xray", "--yellow", "--zebra")).xyz shouldBe listOf("--xray", "--yellow", "--zebra")
    }

    test("LongOptionsWithArgs") {
        class Args(parser: ArgParser) {
            val xyz by parser.option<MutableList<String>>("--xray", "--yellow", "--zaphod",
                    errorName = "ARG_NAME", usageArgument = "ARG_NAME",
                    help = TEST_HELP) {
                value.orElse { mutableListOf<String>() }.apply {
                    add("$optionName:${next()}")
                }
            }
        }

        // Test with value as separate arg
        Args(parserOf("--xray", "0", "--yellow", "1", "--zaphod", "2", "--zaphod", "3", "--yellow", "4")).xyz shouldBe listOf("--xray:0", "--yellow:1", "--zaphod:2", "--zaphod:3", "--yellow:4")

        // Test with = between option and value
        Args(parserOf("--xray=0", "--yellow=1", "--zaphod=2", "--zaphod=3", "--yellow=4")).xyz shouldBe listOf("--xray:0", "--yellow:1", "--zaphod:2", "--zaphod:3", "--yellow:4")

        shouldThrow<UnrecognizedOptionException> {
            Args(parserOf("--xray0", "--yellow1", "--zaphod2", "--zaphod3", "--yellow4")).xyz
        }.run {
            message shouldBe "unrecognized option '--xray0'"
        }
    }

    test("Default") {
        class Args(parser: ArgParser) {
            val x by parser.storing("-x",
                    help = TEST_HELP) { toInt() }.default(5)
        }

        // Test with no value
        Args(parserOf()).x shouldBe 5

        // Test with value
        Args(parserOf("-x6")).x shouldBe 6

        // Test with value as separate arg
        Args(parserOf("-x", "7")).x shouldBe 7

        // Test with multiple values
        Args(parserOf("-x9", "-x8")).x shouldBe 8
    }

    test("DefaultWithProvider") {
        class Args(parser: ArgParser) {
            val x by parser.storing(help = TEST_HELP) { toInt() }.default(5)
        }

        // Test with no value
        Args(parserOf()).x shouldBe 5

        // Test with value
        Args(parserOf("-x6")).x shouldBe 6

        // Test with value as separate arg
        Args(parserOf("-x", "7")).x shouldBe 7

        // Test with multiple values
        Args(parserOf("-x9", "-x8")).x shouldBe 8
    }

    test("Flag") {
        class Args(parser: ArgParser) {
            val x by parser.flagging("-x", "--ecks",
                    help = TEST_HELP)
            val y by parser.flagging("-y",
                    help = TEST_HELP)
            val z by parser.flagging("--zed",
                    help = TEST_HELP)
        }

        Args(parserOf("-x", "-y", "--zed", "--zed", "-y")).run {
            x shouldBe true
            y shouldBe true
            z shouldBe true
        }

        Args(parserOf()).run {
            x shouldBe false
            y shouldBe false
            z shouldBe false
        }

        Args(parserOf("-y", "--ecks")).run {
            x shouldBe true
            y shouldBe true
        }

        Args(parserOf("--zed")).run {
            x shouldBe false
            y shouldBe false
            z shouldBe true
        }
    }

    test("Argument_noParser") {
        class Args(parser: ArgParser) {
            val x by parser.storing("--ecks", "-x",
                    help = TEST_HELP)
        }

        Args(parserOf("-x", "foo")).x shouldBe "foo"

        Args(parserOf("-x", "bar", "-x", "baz")).x shouldBe "baz"

        Args(parserOf("--ecks", "long", "-x", "short")).x shouldBe "short"

        Args(parserOf("-x", "short", "--ecks", "long")).x shouldBe "long"

        val args = Args(parserOf())
        shouldThrow<MissingValueException> {
            args.x
        }.run {
            message shouldBe "missing ECKS"
        }
    }

    test("Argument_missing_long") {
        class Args(parser: ArgParser) {
            val x by parser.storing("--ecks",
                    help = TEST_HELP)
        }

        val args = Args(parserOf())
        shouldThrow<MissingValueException> {
            args.x
        }.run {
            message shouldBe "missing ECKS"
        }
    }

    test("Argument_missing_short") {
        class Args(parser: ArgParser) {
            val x by parser.storing("-x",
                    help = TEST_HELP)
        }

        val args = Args(parserOf())
        shouldThrow<MissingValueException> {
            args.x
        }.run {
            message shouldBe "missing X"
        }
    }

    test("Argument_withParser") {
        class Args(parser: ArgParser) {
            val x by parser.storing("-x", "--ecks",
                    help = TEST_HELP) { toInt() }
        }

        val opts1 = Args(parserOf("-x", "5"))
        opts1.x shouldBe 5

        val opts2 = Args(parserOf("-x", "1", "-x", "2"))
        opts2.x shouldBe 2

        val opts3 = Args(parserOf("--ecks", "3", "-x", "4"))
        opts3.x shouldBe 4

        val opts4 = Args(parserOf("-x", "5", "--ecks", "6"))
        opts4.x shouldBe 6

        val opts6 = Args(parserOf())
        shouldThrow<MissingValueException> {
            opts6.x
        }.run {
            message shouldBe "missing ECKS"
        }
    }

    test("Accumulator_noParser") {
        class Args(parser: ArgParser) {
            val x by parser.adding("-x", "--ecks",
                    help = TEST_HELP)
        }

        Args(parserOf()).x shouldBe listOf<String>()

        Args(parserOf("-x", "foo")).x shouldBe listOf("foo")

        Args(parserOf("-x", "bar", "-x", "baz")).x shouldBe listOf("bar", "baz")

        Args(parserOf("--ecks", "long", "-x", "short")).x shouldBe listOf("long", "short")

        Args(parserOf("-x", "short", "--ecks", "long")).x shouldBe listOf("short", "long")
    }

    test("Accumulator_withParser") {
        class Args(parser: ArgParser) {
            val x by parser.adding("-x", "--ecks",
                    help = TEST_HELP) { toInt() }
        }

        Args(parserOf()).x shouldBe listOf<Int>()
        Args(parserOf("-x", "5")).x shouldBe listOf(5)
        Args(parserOf("-x", "1", "-x", "2")).x shouldBe listOf(1, 2)
        Args(parserOf("--ecks", "3", "-x", "4")).x shouldBe listOf(3, 4)
        Args(parserOf("-x", "5", "--ecks", "6")).x shouldBe listOf(5, 6)
    }

    class ColorArgs(parser: ArgParser) {
        val color by parser.mapping(
                "--red" to Color.RED,
                "--green" to Color.GREEN,
                "--blue" to Color.BLUE,
                help = TEST_HELP)
    }

    test("Mapping") {
        ColorArgs(parserOf("--red")).color shouldBe Color.RED
        ColorArgs(parserOf("--green")).color shouldBe Color.GREEN
        ColorArgs(parserOf("--blue")).color shouldBe Color.BLUE

        // Last one takes precedence
        ColorArgs(parserOf("--blue", "--red")).color shouldBe Color.RED
        ColorArgs(parserOf("--blue", "--green")).color shouldBe Color.GREEN
        ColorArgs(parserOf("--red", "--blue")).color shouldBe Color.BLUE

        val args = ColorArgs(parserOf())
        shouldThrow<MissingValueException> {
            args.color
        }.run {
            message shouldBe "missing --red|--green|--blue"
        }
    }

    class OptionalColorArgs(parser: ArgParser) {
        val color by parser.mapping(
                "--red" to Color.RED,
                "--green" to Color.GREEN,
                "--blue" to Color.BLUE,
                help = TEST_HELP)
                .default(Color.GREEN)
    }

    test("Mapping_withDefault") {
        OptionalColorArgs(parserOf("--red")).color shouldBe Color.RED
        OptionalColorArgs(parserOf("--green")).color shouldBe Color.GREEN
        OptionalColorArgs(parserOf("--blue")).color shouldBe Color.BLUE
        OptionalColorArgs(parserOf()).color shouldBe Color.GREEN
    }

    test("UnrecognizedShortOpt") {
        shouldThrow<UnrecognizedOptionException> {
            OptionalColorArgs(parserOf("-x")).color
        }.run {
            message shouldBe "unrecognized option '-x'"
        }
    }

    test("UnrecognizedLongOpt") {
        shouldThrow<UnrecognizedOptionException> {
            OptionalColorArgs(parserOf("--ecks")).color
        }.run {
            message shouldBe "unrecognized option '--ecks'"
        }
    }

    test("StoringNoArg") {
        class Args(parser: ArgParser) {
            val x by parser.storing("-x", "--ecks",
                    help = TEST_HELP)
        }

        // Note that name actually used for option is used in message
        shouldThrow<OptionMissingRequiredArgumentException> {
            Args(parserOf("-x")).x
        }.run {
            message shouldBe "option '-x' is missing a required argument"
        }

        // Note that name actually used for option is used in message
        shouldThrow<OptionMissingRequiredArgumentException> {
            Args(parserOf("--ecks")).x
        }.run {
            message shouldBe "option '--ecks' is missing a required argument"
        }
    }

    test("ShortStoringNoArgChained") {
        class Args(parser: ArgParser) {
            val y by parser.flagging("-y",
                    help = TEST_HELP)
            val x by parser.storing("-x",
                    help = TEST_HELP)
        }

        // Note that despite chaining, hyphen appears in message
        shouldThrow<OptionMissingRequiredArgumentException> {
            Args(parserOf("-yx")).x
        }.run {
            message shouldBe "option '-x' is missing a required argument"
        }
    }

    test("InitValidation") {
        class Args(parser: ArgParser) {
            val yDelegate = parser.storing("-y",
                    help = TEST_HELP) { toInt() }
            val y by yDelegate

            val xDelegate = parser.storing("-x",
                    help = TEST_HELP) { toInt() }
            val x by xDelegate

            init {
                if (y >= x)
                    throw InvalidArgumentException("${yDelegate.errorName} must be less than ${xDelegate.errorName}")

                // A better way to accomplish validation that only depends on one Delegate is to use
                // Delegate.addValidator. See testAddValidator for an example of this.
                if (x.rem(2) != 0)
                    throw InvalidArgumentException("${xDelegate.errorName} must be even, $x is odd")
            }
        }

        // This should pass validation
        val opts0 = Args(parserOf("-y1", "-x10"))
        opts0.y shouldBe 1
        opts0.x shouldBe 10

        shouldThrow<InvalidArgumentException> {
            Args(parserOf("-y20", "-x10")).x
        }.run {
            message shouldBe "Y must be less than X"
        }

        shouldThrow<InvalidArgumentException> {
            Args(parserOf("-y10", "-x15")).x
        }.run {
            message shouldBe "X must be even, 15 is odd"
        }

        shouldThrow<InvalidArgumentException> {
            Args(parserOf("-y10", "-x15")).x
        }.run {
            message shouldBe "X must be even, 15 is odd"
        }
    }

    test("AddValidator") {
        class Args(parser: ArgParser) {
            val yDelegate = parser.storing("-y",
                    help = TEST_HELP) { toInt() }
            val y by yDelegate

            val xDelegate = parser.storing("-x",
                    help = TEST_HELP) { toInt() }
                    .addValidator {
                        if (value.rem(2) != 0)
                            throw InvalidArgumentException("$errorName must be even, $value is odd")
                    }
            val x by xDelegate

            init {
                if (y >= x)
                    throw InvalidArgumentException("${yDelegate.errorName} must be less than ${xDelegate.errorName}")
            }
        }

        // This should pass validation
        val opts0 = Args(parserOf("-y1", "-x10"))
        opts0.y shouldBe 1
        opts0.x shouldBe 10

        shouldThrow<InvalidArgumentException> {
            Args(parserOf("-y20", "-x10")).x
        }.run {
            message shouldBe "Y must be less than X"
        }

        shouldThrow<InvalidArgumentException> {
            Args(parserOf("-y10", "-x15")).x
        }.run {
            message shouldBe "X must be even, 15 is odd"
        }

        shouldThrow<InvalidArgumentException> {
            Args(parserOf("-y10", "-x15")).x
        }.run {
            message shouldBe "X must be even, 15 is odd"
        }
    }

    test("Unconsumed") {
        class Args(parser: ArgParser) {
            val y by parser.flagging("-y", "--why",
                    help = TEST_HELP)
            val x by parser.flagging("-x", "--ecks",
                    help = TEST_HELP)
        }

        // No problem.
        Args(parserOf("-yx")).run {
            x shouldBe true
            y shouldBe true
        }

        // Attempting to give -y a parameter, "z", is treated as unrecognized option.
        shouldThrow<UnrecognizedOptionException> {
            Args(parserOf("-yz")).y
        }.run {
            message shouldBe "unrecognized option '-z'"
        }

        // Unconsumed "z" again, but note that it triggers even if we don't look at y.
        shouldThrow<UnrecognizedOptionException> {
            Args(parserOf("-yz")).x
        }.run {
            message shouldBe "unrecognized option '-z'"
        }

        // No problem again, this time with long opts.
        Args(parserOf("--why", "--ecks")).run {
            x shouldBe true
            y shouldBe true
        }

        // Attempting to give --why a parameter, "z" causes an error.
        shouldThrow<UnexpectedOptionArgumentException> {
            Args(parserOf("--why=z")).y
        }.run {
            message shouldBe "option '--why' doesn't allow an argument"
        }

        // Unconsumed "z" again, but note that it triggers even if we don't look at y.
        shouldThrow<UnexpectedOptionArgumentException> {
            Args(parserOf("--why=z")).x
        }.run {
            message shouldBe "option '--why' doesn't allow an argument"
        }
    }

    test("Positional_basic") {
        class Args(parser: ArgParser) {
            val flag by parser.flagging("-f", "--flag",
                    help = TEST_HELP)
            val store by parser.storing("-s", "--store",
                    help = TEST_HELP).default("DEFAULT")
            val sources by parser.positionalList("SOURCE",
                    help = TEST_HELP)
            val destination by parser.positional("DEST",
                    help = TEST_HELP)
        }

        Args(parserOf("foo", "bar", "baz", "quux")).run {
            flag shouldBe false
            store shouldBe "DEFAULT"
            sources shouldBe listOf("foo", "bar", "baz")
            destination shouldBe "quux"
        }

        Args(parserOf("-f", "foo", "bar", "baz", "quux")).run {
            flag shouldBe true
            store shouldBe "DEFAULT"
            sources shouldBe listOf("foo", "bar", "baz")
            destination shouldBe "quux"
        }

        Args(parserOf("-s", "foo", "bar", "baz", "quux")).run {
            flag shouldBe false
            store shouldBe "foo"
            sources shouldBe listOf("bar", "baz")
            destination shouldBe "quux"
        }

        Args(parserOf("-s", "foo", "bar", "-f", "baz", "quux")).run {
            flag shouldBe true
            store shouldBe "foo"
            sources shouldBe listOf("bar", "baz")
            destination shouldBe "quux"
        }

        // "--" disables option processing for all further arguments.
        // Note that "-f" is now considered a positional argument.
        Args(parserOf("-s", "foo", "--", "bar", "-f", "baz", "quux")).run {
            flag shouldBe false
            store shouldBe "foo"
            sources shouldBe listOf("bar", "-f", "baz")
            destination shouldBe "quux"
        }

        // "--" disables option processing for all further arguments.
        // Note that the second "--" is also considered a positional argument.
        Args(parserOf("-s", "foo", "--", "bar", "--", "-f", "baz", "quux")).run {
            flag shouldBe false
            store shouldBe "foo"
            sources shouldBe listOf("bar", "--", "-f", "baz")
            destination shouldBe "quux"
        }

        Args(parserOf("-s", "foo", "bar", "-f", "baz", "quux", mode = ArgParser.Mode.POSIX)).run {
            flag shouldBe false
            store shouldBe "foo"
            sources shouldBe listOf("bar", "-f", "baz")
            destination shouldBe "quux"
        }
    }

    test("Positional_withParser") {
        class Args(parser: ArgParser) {
            val flag by parser.flagging("-f", "--flag",
                    help = TEST_HELP)
            val store by parser.storing("-s", "--store",
                    help = TEST_HELP).default("DEFAULT")
            val start by parser.positionalList("START", 3..4,
                    help = TEST_HELP) { toInt() }
            val end by parser.positionalList("END", 3..5,
                    help = TEST_HELP) { toInt() }
        }

        shouldThrow<MissingRequiredPositionalArgumentException> {
            Args(parserOf("1", "2")).flag
        }.run {
            message shouldBe "missing START operand"
        }

        shouldThrow<MissingRequiredPositionalArgumentException> {
            Args(parserOf("1", "2", "3", "4", "5")).flag
        }.run {
            message shouldBe "missing END operand"
        }

        Args(parserOf("1", "2", "3", "4", "5", "6")).run {
            flag shouldBe false
            store shouldBe "DEFAULT"

            // end needs at least 3 args, so start only consumes 3
            start shouldBe listOf(1, 2, 3)
            end shouldBe listOf(4, 5, 6)
        }

        Args(parserOf("1", "2", "3", "4", "5", "6", "7")).run {
            flag shouldBe false
            store shouldBe "DEFAULT"

            // end only needs at 3 args, so start can consume 4
            start shouldBe listOf(1, 2, 3, 4)
            end shouldBe listOf(5, 6, 7)
        }

        Args(parserOf("1", "2", "3", "4", "5", "6", "7", "8")).run {
            flag shouldBe false
            store shouldBe "DEFAULT"

            // start can't consume more than 4, so end gets the rest.
            start shouldBe listOf(1, 2, 3, 4)
            end shouldBe listOf(5, 6, 7, 8)
        }

        Args(parserOf("1", "2", "3", "4", "5", "6", "7", "8", "9")).run {
            flag shouldBe false
            store shouldBe "DEFAULT"

            // once again, start can't consume more than 4, so end gets the rest.
            start shouldBe listOf(1, 2, 3, 4)
            end shouldBe listOf(5, 6, 7, 8, 9)
        }

        shouldThrow<UnexpectedPositionalArgumentException> {
            Args(parserOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")).flag
        }.run {
            message shouldBe "unexpected argument after END"
        }
    }

    test("Counting") {
        class Args(parser: ArgParser) {
            val verbosity by parser.counting("-v", "--verbose",
                    help = TEST_HELP)
        }

        Args(parserOf()).run {
            verbosity shouldBe 0
        }

        Args(parserOf("-v")).run {
            verbosity shouldBe 1
        }

        Args(parserOf("-v", "-v")).run {
            verbosity shouldBe 2
        }
    }

    test("Help") {
        class Args(parser: ArgParser) {
            val dryRun by parser.flagging("-n", "--dry-run",
                    help = "don't do anything")
            val includes by parser.adding("-I", "--include",
                    help = "search in this directory for header files")
            val outDir by parser.storing("-o", "--output",
                    help = "directory in which all output should be generated")
            val verbosity by parser.counting("-v", "--verbose",
                    help = "increase verbosity")
            val sources by parser.positionalList("SOURCE",
                    help = "source file")
            val destination by parser.positional("DEST",
                    help = "destination file")
        }

        shouldThrow<ShowHelpException> {
            Args(parserOf("--help",
                    helpFormatter = DefaultHelpFormatter(
                            prologue = """
                            This is the prologue. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aliquam malesuada maximus eros. Fusce luctus risus eget quam consectetur, eu auctor est ullamcorper. Maecenas eget suscipit dui, sed sodales erat. Phasellus.
                            """,
                            epilogue = """
                            This is the epilogue. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec vel tortor nunc. Sed eu massa sed turpis auctor faucibus. Donec vel pellentesque tortor. Ut ultrices tempus lectus fermentum vestibulum. Phasellus.
                            """))).dryRun
        }.run {
            val writer = StringWriter()
            printUserMessage(writer, "program_name", 60)
            val help = writer.toString()
            help shouldBe """
usage: program_name [-h] [-n] [-I INCLUDE]... -o OUTPUT
                    [-v]... SOURCE... DEST


This is the prologue. Lorem ipsum dolor sit amet, consectetur
adipiscing elit. Aliquam malesuada maximus eros. Fusce
luctus risus eget quam consectetur, eu auctor est
ullamcorper. Maecenas eget suscipit dui, sed sodales erat.
Phasellus.


required arguments:
  -o OUTPUT,        directory in which all output should
  --output OUTPUT   be generated


optional arguments:
  -h, --help        show this help message and exit

  -n, --dry-run     don't do anything

  -I INCLUDE,       search in this directory for header
  --include INCLU   files
  DE

  -v, --verbose     increase verbosity


positional arguments:
  SOURCE            source file

  DEST              destination file



This is the epilogue. Lorem ipsum dolor sit amet, consectetur
adipiscing elit. Donec vel tortor nunc. Sed eu massa sed
turpis auctor faucibus. Donec vel pellentesque tortor. Ut
ultrices tempus lectus fermentum vestibulum. Phasellus.

""".trimStart()
        }
    }

    test("ImplicitLongFlagName") {
        class Args(parser: ArgParser) {
            val flag1 by parser.flagging(help = TEST_HELP)
            val flag2 by parser.flagging(help = TEST_HELP)
            val count by parser.counting(help = TEST_HELP)
            val store by parser.storing(help = TEST_HELP)
            val store_int by parser.storing(help = TEST_HELP) { toInt() }
            val adder by parser.adding(help = TEST_HELP)
            val int_adder by parser.adding(help = TEST_HELP) { toInt() }
            val int_set_adder by parser.adding(initialValue = mutableSetOf<Int>(), help = TEST_HELP) { toInt() }
            val positional by parser.positional(help = TEST_HELP)
            val positional_int by parser.positional(help = TEST_HELP) { toInt() }
            val positionalList by parser.positionalList(sizeRange = 2..2, help = TEST_HELP)
            val positionalList_int by parser.positionalList(sizeRange = 2..2, help = TEST_HELP) { toInt() }
        }

        Args(parserOf(
                "--flag1", "--count", "--count", "--store=hello", "--store-int=42",
                "--adder=foo", "--adder=bar",
                "--int-adder=2", "--int-adder=4", "--int-adder=6",
                "--int-set-adder=64", "--int-set-adder=128", "--int-set-adder=20",
                "1", "1", "2", "3", "5", "8"
        )).run {
            flag1 shouldBe true
            flag2 shouldBe false
            count shouldBe 2
            store shouldBe "hello"
            store_int shouldBe 42
            adder shouldBe listOf("foo", "bar")
            int_adder shouldBe listOf(2, 4, 6)
            int_set_adder shouldBe setOf(20, 64, 128)
            positional shouldBe "1"
            positional_int shouldBe 1
            positionalList shouldBe listOf("2", "3")
            positionalList_int shouldBe listOf(5, 8)
        }

        shouldThrow<MissingRequiredPositionalArgumentException> {
            Args(parserOf(
                    "13", "21", "34", "55", "89"
            )).run {
                flag1 shouldBe false
            }
        }.run {
            message shouldBe "missing POSITIONALLIST_INT operand"
        }
    }

    fun nullableString() : String? = null

    test("NullableOptional") {
        class Args(parser: ArgParser) {
            val path by parser.storing("The path", ::File)
                    .default(nullableString()?.let(::File))

        }
    }

    test("NullableOptional_withoutTransform") {
        class Args(parser: ArgParser) {
            val str by parser.storing(TEST_HELP)
                    .default(nullableString())
        }
        Args(parserOf("--str=foo")).run {
            str shouldBe "foo"
        }
        Args(parserOf()).run {
            str shouldBe null
        }
    }

    test("DefaultGeneralization") {
        class Args(parser: ArgParser) {
            val shape by parser.storing("The path", ::Rectangle)
                    .default(Circle())
            val rect by parser.storing("The path", ::Rectangle)
        }
        val args = Args(parserOf("--rect=foo"))
        staticType(args.shape) shouldBe Shape::class
        args.shape should beOfType<Circle>()
        staticType(args.rect) shouldBe Rectangle::class

        val args2 = Args(parserOf())
        shouldThrow<MissingValueException> {
            args2.rect
        }.run {
            message shouldBe "missing RECT"
        }
    }

    test("DefaultGeneralization_withoutTransform") {
        class Args(parser: ArgParser) {
            val str by parser.storing(TEST_HELP)
                    .default(5)
        }
        Args(parserOf("--str=foo")).run {
            str shouldBe "foo"
        }
        Args(parserOf()).run {
            str shouldBe 5
        }
    }

    // TODO: test auto-naming on positional args
    // TODO: test default on argument
    // TODO: test default on argumentList
    // TODO: test addValidator on argument
    // TODO: test addValidator on argumentList
})
