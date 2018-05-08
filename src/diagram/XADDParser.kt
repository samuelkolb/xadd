package diagram

import time.Stopwatch
import xadd.XADD
import java.lang.Math.abs
import java.lang.Math.pow
import java.lang.System.exit
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Created by samuelkolb on 17/06/2017.
 * @author Samuel Kolb
 */
val operators = listOf("ite", "^", "~", "&", "|", "*", "+", "<=", "<", "const", "var")

class XADDParser(val xadd: XADD) {

    val builder: XADDBuild.Builder
    init {
        builder = XADDBuild.builder(xadd)
    }

    // fun parseBooleanXadd(string: String) : BoolXADD = astToBoolXadd(Parser(operators).parseString(string))

    fun parseXadd(string: String) : XADDiagram = astToXadd(NestedParser(operators).parseString(string))

    private fun astToXadd(node: NestedParser.Node) : XADDiagram {
        println()
        println(node)

        if(node is NestedParser.OperatorNode) {
            if(node.name == null) { return astToXadd(node.childNodes[0]).reduceLp() }
            if(node.name in arrayOf("~", "&", "|", "<=", "<")) return astToBoolXadd(node)
            if(node.name in arrayOf("const", "var")) {
                if(node.childNodes[0].name == "real") {
                    return builder.`val`(node.childNodes[1].name)
                } else if(node.childNodes[0].name == "bool"){
                    return astToBoolXadd(node)
                }
            }
            if(node.name == "^") {
                val (childVar, childExp) = node.childNodes
                if(childVar is NestedParser.OperatorNode && childVar.name == "var" && childVar.childNodes[0].name == "real" &&
                        childExp is NestedParser.OperatorNode && childExp.name == "const" && childVar.childNodes[0].name == "real") {
                    val exp = childExp.childNodes[1].name!!.toFloat().toInt()
                    val sym = childVar.childNodes[1].name!!
                    if(exp == 0) {
                        return builder.`val`(1)
                    }
                    return builder.`val`(Collections.nCopies(exp, sym).joinToString("*"))
                }
            }
            return when(node.name) {
                "*" -> {
                    var diagram = builder.`val`(1)
                    for(child in node.childNodes) {
                        diagram = diagram.times(astToXadd(child))
                    }
                    diagram
                    // node.childNodes.map({astToXadd(it)}).fold(builder.`val`(1), XADDiagram::times).reduceLp()
                }
                "+" -> {
                    var diagram = builder.`val`(0)
                    for(child in node.childNodes) {
                        diagram = diagram.plus(astToXadd(child))
                    }
                    diagram
                    // node.childNodes.map({astToXadd(it)}).fold(builder.`val`(0), XADDiagram::plus).reduceLp()
                }
                "ite" -> astToBoolXadd(node.childNodes[0]).assignWeights(
                        astToXadd(node.childNodes[1]), astToXadd(node.childNodes[2])
                )
                else -> throw IllegalArgumentException("Operator ${node.name} not currently supported at the top level")
            }
        } else if (node is NestedParser.LeafNode) {
            throw NotImplementedError()
        } else {
            throw IllegalArgumentException("Node $node of unknown type")
        }
    }

    fun astToBoolXadd(node: NestedParser.Node) : BoolXADD {
        if(node is NestedParser.OperatorNode) {
            if(node.name in arrayOf("<=", "<")) {
                val parsedInequality = astToInequality(node)
                return builder.test(parsedInequality)
            }
            if(node.name in arrayOf("const", "var") && node.childNodes[0].name == "bool") {
                val value = node.childNodes[1].name!!
                if(node.name == "var") {
                    return builder.bool(value)
                } else if(node.name == "const") {
                    if(value.toLowerCase() in arrayOf("true", "false")) {
                        return builder.`val`(value.toLowerCase() == "true")
                    } else {
                        throw IllegalArgumentException("Illegal boolean constant %s".format(value))
                    }
                }
            }
            when(node.name) {
                "&" -> return node.childNodes.map({astToBoolXadd(it)}).fold(builder.`val`(true), BoolXADD::and)
                "|" -> return node.childNodes.map({astToBoolXadd(it)}).fold(builder.`val`(false), BoolXADD::or)
                "~" -> return astToBoolXadd(node.childNodes[0]).not()
                else -> throw IllegalArgumentException("Operator ${node.name} not currently as boolean connective")
            }
        } else if (node is NestedParser.LeafNode) {
            throw IllegalArgumentException("Leaf nodes (such as $node) cannot be parsed directly")
        } else {
            throw IllegalArgumentException("Node $node of unknown type")
        }
    }

    private fun astToInequality(node: NestedParser.Node) : String {
        if(node is NestedParser.OperatorNode) {
            val op = node.name ?: throw IllegalArgumentException("Uninitialized node name")
            if(op in arrayOf("const", "var")) {
                val type = node.childNodes[0].name
                if(type == "bool") throw IllegalArgumentException("Illegal type '$type' in inequality")
                return node.childNodes[1].name!!
            }
            if(op in arrayOf("<=", "<")) {
                val lhs = astToInequality(node.childNodes[0])
                val rhs = astToInequality(node.childNodes[1])
                return "$lhs $op $rhs"
            }
            if(op in arrayOf("*", "+")) {
                return node.childNodes.map({astToInequality(it)}).joinToString(op)
            }
            throw IllegalArgumentException("Operator $op could not be parsed as inequality")
        } else if (node is NestedParser.LeafNode) {
            throw IllegalArgumentException("Leaf nodes (such as $node) cannot be parsed directly")
        } else {
            throw IllegalArgumentException("Node $node of unknown type")
        }
    }
}

fun run(diagramText: String, boolVars: List<String>, realVars: List<String>, resultPa: Double, timeOut: Int) {
    val DELTA = 0.0001
    val executor = Executors.newSingleThreadExecutor()
    val timer = Stopwatch()

    val runMode = fun(name: String, ordered: Boolean, callable: (XADDiagram) -> XADDiagram) {
        val xadd: XADD
        if(ordered) {
            val order = ArrayList(boolVars)
            order.addAll(realVars)
            xadd = OrderedXADD(order, false);
        } else {
            xadd = XADD()
        }
        val diagram = XADDParser(xadd).parseXadd(diagramText)
        // diagram.getIntegratedDiagram(ArrayList(boolVars), ArrayList(realVars))
        val future = executor.submit<XADDiagram>({callable(diagram)})
        try {
            // println("Compute %s-VE".format(name))
            timer.start()
            val result = future.get(timeOut.toLong(), TimeUnit.SECONDS).evaluate(Assignment())
            val divergence = abs(resultPa - result)
            val success = if(divergence < abs(resultPa * DELTA)) "SUCCESS" else "FAILED (%.2f)".format(divergence)
            println("%s %s Obtained %.2f in %.2fs".format(name, success, result, timer.stop() / 1000.0))
        } catch (e: TimeoutException) {
            println("%s timed out (time out = %d)".format(name, timeOut))
        } catch (e: Exception) {
            println("%s encountered exception: %s".format(name, e.message))
        }
    }

    try {
        // runMode("PE", false) { it.getIntegratedDiagram(ArrayList(boolVars), ArrayList(realVars)) }
        // runMode("BR", false) { it.eliminateBoolVars(boolVars).eliminateRealVars(realVars) }
        runMode("BR-SYM", false) {
            val integrator = ResolutionIntegrator(it.xadd)
            integrator.integrateReals(integrator.integrateBools(it, boolVars), realVars)
        }
        // runMode("BR Ordered", true) { it.eliminateBoolVars(boolVars).eliminateRealVars(realVars) }
        /*runMode("BR Ordered", true) {
            val types = ArrayList<String>()
            for(i in boolVars) {
                types.add("bool")
            }
            for(i in realVars) {
                types.add("real")
            }
            val vars = HashSet(boolVars + realVars)
            val element = realVars[realVars.size - 1]
            println("Leaving %s".format(element))
            vars.remove(element)
            it.show("Original")
            val resultId = ResolveAllIntegration(it.xadd as OrderedXADD?, types).integrate(it.number, vars)
            val resultDiagram = XADDiagram(it.xadd, resultId)
            resultDiagram.show("Diagram")
            Thread.sleep(1000000)
            resultDiagram
        }*/
        /*runMode("BR All", true) {
            val types = ArrayList<String>()
            for(i in boolVars) {
                types.add("bool")
            }
            for(i in realVars) {
                types.add("real")
            }
            val vars = HashSet(boolVars + realVars)
            val resultId = ResolveAllIntegration(it.xadd as OrderedXADD?, types).integrate(it.reduceLp().number, vars)
            XADDiagram(it.xadd, resultId)
        }*/
    } finally {
        executor.shutdown()
    }
}


fun runQueries(diagramText: String, boolVars: List<String>, realVars: List<String>, queryStrings: List<String>) {
    val xadd = OrderedXADD(boolVars + realVars, false)
    val parser = XADDParser(xadd)
    val diagram = parser.parseXadd(diagramText).reduce()
    val queries = queryStrings.map { parser.parseXadd(it) }
    // For the moment we assume automatically that we know

}


fun main(vararg args: String) {
    val resultFilename = "samuel_results.txt"
    val resultLines = NestedParser::class.java.getResource("/wmi/%s".format(resultFilename)).readText().split(Regex("\\n"))
    for(line in resultLines) {
        val trimmed = line.trim()
        if(trimmed != "") {
            val (filename, result, time, mode) = trimmed.split(",")
            println("Checking file %s".format(filename))
            val problemFile = "/wmi/problem_%s".format(filename)
            val params = filename.split("_").subList(0, 6)
            val diagramText = NestedParser::class.java.getResource(problemFile).readText().toLowerCase()
            val boolVars = (0..params[0].toInt()-1).map { "b_%d".format(it) }
            val realVars = (0..params[1].toInt()-1).map { "x_%d".format(it) }
            val boolVarRegex = Regex("\\(var bool ([a-zA-Z0-9_]+)\\)")
            val booleanVariables = boolVarRegex.findAll(diagramText).map { it.groupValues[1] }.toHashSet()
            val boolIgnored = HashSet(boolVars)
            boolIgnored.removeAll(booleanVariables)
            val resultPa = pow(2.0, boolIgnored.size.toDouble()) * result.toFloat()
            println("%s Obtained %.2f in %.2fs".format(mode, resultPa, time.toFloat()))
            run(diagramText, boolVars, realVars, resultPa, 60)
        }
    }
}