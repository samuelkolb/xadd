package diagram

import java.io.File
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.collections.ArrayList

/**
 * Created by samuelkolb on 12/07/2017.
 * @author Samuel Kolb
 */
fun runQueries() {
    // val files = listOf(Pair(2, "sequential_663.txt"))
    //val files = listOf(Pair(2, "sequential_663.txt"), Pair(2, "sequential_665.txt"), Pair(2, "sequential_667.txt"),
    //        Pair(2, "sequential_669.txt"))
    // val files = listOf(Pair(4, "sequential_665_4_4_4.txt"))
    // val files = listOf(Pair(4, "sequential_666_4_4_3.txt"))
    // val files = listOf(10).map { Pair(4, "sequential_${it}_4_4_2.txt") }
    // val files = listOf(1, 2, 5, 6, 7, 8, 10, 12, 13, 15).map { Pair(4, "sequential_${it}_4_4_2.txt") }
    val files = listOf(10).map { Pair(4, "sequential_${it}_4_4_2.txt") }

    // Parameters
    val DELTA = 0.0001
    val timeOut: Long = 180
    // val integratorTypes = listOf("original", "resolve", "resolve-sym")
    val integratorTypes = listOf("resolve-sym")

    for((variableCount, file) in files) {
        val lines = NestedParser::class.java.getResource("/queries/%s".format(file)).readText().split(Regex("\\n"))
        val theoryString = lines[0].toLowerCase()
        val weightsString = lines[1].toLowerCase()
        var queries = ArrayList<String>()
        val queryResults = ArrayList<Double>()
        val queryTimes = ArrayList<Double>()
        for(i in 2 until lines.size) {
            if(lines[i] != "") {
                val parts = lines[i].split(",")
                // println("Query took %ss to compute %s".format(parts[2], parts[1]))
                if(queries.size < 100) {
                    queries.add(parts[0])
                    queryResults.add(parts[1].toDouble())
                    queryTimes.add(parts[2].toDouble())
                }
            }
        }

        // Upgrade to two queries
        /*queries = ArrayList(queries.map {
            it.replace("(var real x_0)", "(+ (var real x_0) (var real x_1))")
        })*/

        val engine = QueryEngine()
        engine.addRealVar("x_0")
        for(i in 1 until variableCount) {
            engine.addRealVar("x_%d".format(i))
        }
        for(i in 0 until variableCount) {
            engine.addBoolVar("a_%d".format(i))
        }

        val path = URI(NestedParser::class.java.getResource("/queries/").toURI().toString() + "/$file.log.txt")
        val resultsFile = File(path)
        println("Printing results to ${resultsFile.absolutePath}")
        resultsFile.createNewFile()

        val titles = ArrayList<String>()
        val columns = ArrayList<List<String>>()

        fun List<Double>.cumulative() : List<Double> {
            var current = 0.0
            return this.map {
                current += it
                current
            }
        }

        // Insert PA results
        titles.add("Result predicate-abstraction")
        columns.add(queryResults.map {it.toString()})
        titles.add("Time predicate-abstraction")
        columns.add(queryTimes.map {it.toString()})
        titles.add("Cumulative time")
        columns.add(queryTimes.cumulative().map(Double::toString))

        for(integratorType in integratorTypes) {
            titles += listOf("Result $integratorType", "Equal?", "Time $integratorType", "Cumulative time")
            val executor = Executors.newSingleThreadExecutor()
            try {
                val future = executor.submit<List<Double>>({
                    if(integratorType == "all") {
                        engine.runQueriesWithoutCaching(theoryString, weightsString, queries)
                    } else {
                        engine.runQueries(theoryString, weightsString, queries, emptyList(), integratorType)
                    }
                })
                val results = future.get(timeOut, TimeUnit.SECONDS)
                println("Obtained results in under $timeOut seconds")
                val brTimes = engine.times
                brTimes.put("Q1", brTimes["Q1"]!! + brTimes["Pre-processing"]!!)
                val times = (0 until queries.size).map { brTimes["Q" + (it + 1)]!! }

                columns.add(results.map { it.toString() })
                val equal = (0 until queries.size).map {
                    (Math.abs(queryResults[it] - results[it]) <= DELTA * queryResults[it])
                }
                columns.add(equal.map { if(it) "yes" else "no" })
                columns.add(times.map { it.toString() })
                columns.add(times.cumulative().map(Double::toString))

                /*resultsFile.printWriter().use { out ->
                    out.println("EQ\tRes. PA\tRes. BR\tTime PA\tTime BR")
                    for (i in 0..queries.size - 1) {
                        val resPa = queryResults[i]
                        val resBr = brResults[i]
                        var brTime: Double = brTimes["Q" + (i + 1)]!!
                        if (i == 0) {
                            brTime += engine.times["Pre-processing"]!!
                        }
                        val same = if (Math.abs(resPa - resBr) <= DELTA * resPa) 1 else 0
                        out.println("$same\t$resPa\t$resBr\t${queryTimes[i]}\t$brTime")
                    }
                }*/
            } catch (e: TimeoutException) {
                println("$integratorType timed out")
                columns.add((0 until queries.size).map {"?"})
                columns.add((0 until queries.size).map {"?"})
                columns.add((0 until queries.size).map {"TIMEOUT"})
                columns.add((0 until queries.size).map {"?"})
                /*println("Out of time while processing $file")
                resultsFile.printWriter().use { out ->
                    out.println("EQ\tRes. PA\tRes. BR\tTime PA\tTime BR")
                    for (i in 0..queries.size - 1) {
                        val resPa = queryResults[i]
                        out.println("-\t$resPa\t-\t${queryTimes[i]}\tTIMEOUT")
                    }
                }*/
            } catch (e: OutOfMemoryError) {
                columns.add((0 until queries.size).map {"?"})
                columns.add((0 until queries.size).map {"?"})
                columns.add((0 until queries.size).map {"OUT OF MEMORY"})
                columns.add((0 until queries.size).map {"?"})
                /*println("Out of memory while processing $file")
                resultsFile.printWriter().use { out ->
                    out.println("EQ\tRes. PA\tRes. BR\tTime PA\tTime BR")
                    for (i in 0..queries.size - 1) {
                        val resPa = queryResults[i]
                        out.println("-\t$resPa\t-\t${queryTimes[i]}\tOUT OF MEMORY")
                    }
                }*/
            }
            executor.shutdownNow()
            System.gc()
        }
        resultsFile.printWriter().use { out ->
            out.println(titles.joinToString("\t"))
            if(columns.size > 0) {
                for(i in 0 until columns[0].size) {
                    out.println(columns.joinToString("\t") { it[i] })
                }
            }
        }

    }
}

fun main(args: Array<String>) {
    // TODO Gurobi supports irreducible set, can be exploited!

    runQueries()
    System.exit(0)
}