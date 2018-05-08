package diagram

import time.Stopwatch
import xadd.XADD
import java.io.File
import java.util.*
import kotlin.collections.HashSet
import org.json.JSONObject
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


/**
 * Created by samuelkolb on 11/07/2017.
 * @author Samuel Kolb
 */
class QueryEngine {

    private interface Integrator {
        fun setup(theoryVars: List<String>, queryVars: List<String>) : XADD
        fun integrate(xadd: XADDiagram, variables: List<String>) : XADDiagram
    }

    inner class MassIntegrator : Integrator {
        private var integrator: ResolveAllIntegration? = null

        override fun setup(theoryVars: List<String>, queryVars: List<String>): XADD {
            // Setting the ordering - TODO find theory good ordering
            val order = theoryVars + queryVars
            val orderedXadd = OrderedXADD(order, false)
            this.integrator = ResolveAllIntegration(orderedXadd, order.map { getType(it) })
            return orderedXadd
        }

        override fun integrate(xadd: XADDiagram, variables: List<String>): XADDiagram {
            return XADDiagram(xadd.xadd, this.integrator!!.integrate(xadd.number, HashSet(variables)))
        }

    }

    inner class OriginalIntegrator : Integrator {
        override fun setup(theoryVars: List<String>, queryVars: List<String>): XADD {
            return XADD()
        }

        override fun integrate(xadd: XADDiagram, variables: List<String>): XADDiagram {
            val booleanVars = ArrayList<String>()
            val realVars = ArrayList<String>()
            for(variable in variables) {
                val variableType = getType(variable)
                when (variableType) {
                    "bool" -> booleanVars.add(variable)
                    "real" -> realVars.add(variable)
                    else -> throw IllegalStateException("Unrecognized type $variableType for variable $variable")
                }
            }
            return xadd.getIntegratedDiagram(booleanVars, realVars)
        }

    }

    inner class ResolveIntegrator : Integrator {
        override fun setup(theoryVars: List<String>, queryVars: List<String>): XADD {
            return XADD()
        }

        override fun integrate(xadd: XADDiagram, variables: List<String>): XADDiagram {
            return xadd.eliminateVars(variables, variables.map { getType(it) })
        }
    }

    inner class SymbolicResolveIntegrator : Integrator {
        override fun setup(theoryVars: List<String>, queryVars: List<String>): XADD {
            return XADD()
        }

        override fun integrate(xadd: XADDiagram, variables: List<String>): XADDiagram {
            return xadd.eliminateVarsSym(variables, variables.map { getType(it) })
        }
    }

    val variables = ArrayList<String>()
    val types = ArrayList<String>()
    val times: HashMap<String, Double> = HashMap()

    fun addRealVar(name: String) : QueryEngine {
        return addVar(name, "real")
    }

    fun addBoolVar(name: String) : QueryEngine {
        return addVar(name, "bool")
    }

    fun addVar(name: String, type: String) : QueryEngine {
        variables.add(name)
        types.add(type)
        return this
    }

    fun getType(name: String) : String {
        try {
            return types[variables.indexOf(name)]
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("Unknown variable $name")
        }
    }

    fun getVars(query: XADDiagram) : Set<String> {
        return query.xadd.collectVars(query.number)
    }

    fun getVarsString(queryString: String) : Set<String> {
        val vars = HashSet<String>()
        Regex("\\(var (real|bool) (\\w+)\\)").findAll(queryString).forEach {
            vars.add(it.groups[2]!!.value)
        }
        return vars
    }

    fun getVarsStrings(queryStrings: List<String>) : Set<String> {
        val vars = HashSet<String>()
        queryStrings.forEach { vars.addAll(getVarsString(it)) }
        return vars
    }

    fun runQueriesWithoutCaching(diagramString: String, weightString: String, queryStrings: List<String>)
            : List<Double> {
        val orderedXadd = OrderedXADD(variables, false)
        val integrator = ResolveAllIntegration(orderedXadd, orderedXadd.variableOrder.map { getType(it) })
        val parser = XADDParser(orderedXadd)
        val theoryDiagram = parser.parseXadd(diagramString).reduceLp()
        println("Parsed theory")
        // theoryDiagram.show("Theory")
        val weightDiagram = parser.parseXadd(weightString).reduceLp()
        println("Parsed weights")
        // weightDiagram.show("Weights")

        val fullDiagram = theoryDiagram.times(weightDiagram).reduceLp()
        // fullDiagram.show("Full diagram")
        println("Combined to full diagram")

        val stopWatch = Stopwatch(true)
        val totalVolume = XADDiagram(orderedXadd, integrator.integrate(fullDiagram.number)).evaluate(Assignment())
        times.put("Pre-processing", stopWatch.stop() / 1000.0)
        println(times["Pre-processing"])
        val queryProbabilities = ArrayList<Double>()

        for(queryString in queryStrings) {
            val query = BoolXADD.convert(parser.parseXadd(queryString))
            stopWatch.start()
            val queryDiagram = fullDiagram.times(query).reduceLp()

            val queryVolume = XADDiagram(orderedXadd, integrator.integrate(queryDiagram.number)).evaluate(Assignment())

            println(queryVolume.toString() + " " + totalVolume.toString())
            queryProbabilities.add(queryVolume / totalVolume)
            times.put("Q%d".format(times.size), stopWatch.stop() / 1000.0)
            println(times["Q%d".format(times.size - 1)])
        }
        return queryProbabilities
    }

    fun runQueries(diagramString: String, weightString: String, queryStrings: List<String>,
                   evidence: List<Assignment> = emptyList(), integratorType: String = "original") : List<Double> {
        times.clear()
        val evidenceVars = HashSet<String>()
        for(assignment in evidence) {
            evidenceVars.addAll(assignment.booleanVariables.keys)
            evidenceVars.addAll(assignment.continuousVariables.keys)
        }
        val queryVars = getVarsStrings(queryStrings).toList() - evidenceVars
        val theoryVars = variables - queryVars - evidenceVars
        val integrator = when(integratorType) {
            "original" -> OriginalIntegrator()
            "mass" -> MassIntegrator()
            "resolve" -> ResolveIntegrator()
            "resolve-sym" -> SymbolicResolveIntegrator()
            else -> throw IllegalArgumentException("Unrecognized integrator $integratorType")
        }

        val xadd = integrator.setup(theoryVars, queryVars)
        this.variables.filter { getType(it) == "bool" }.forEach { XADDBuild.builder(xadd).bool(it) }
        val parser = XADDParser(xadd)
        val theoryDiagram = parser.parseXadd(diagramString).reduceLp()
        println("Parsed theory")
        // theoryDiagram.show("Theory")
        theoryDiagram.exportGraph("srn_theory_diagram.dot")

        val weightDiagram = parser.parseXadd(weightString).reduceLp()
        println("Parsed weights")
        // weightDiagram.show("Weights")
        weightDiagram.exportGraph("srn_weight_diagram.dot")

        val fullDiagram = theoryDiagram.times(weightDiagram).reduceLp()
        // val fullDiagram = parser.parseXadd("(* $diagramString $weightString)").reduceLp()

        // fullDiagram.show("Full diagram")
        fullDiagram.exportGraph("srn_full_diagram.dot")
        println("Combined to full diagram")

        // Thread.sleep(1000000)

        // val integratedDiagram = XADDiagram(xadd, integrator.integrate(fullDiagram.number))
        // integratedDiagram.show("Integrated")
        // println("Eliminate all")

        val stopWatch = Stopwatch(true)
        // integrator.isVerbose = true
        var partialDiagram = integrator.integrate(fullDiagram, theoryVars).reduceLp()
        partialDiagram = partialDiagram.evaluatePartial(evidence[0].booleanVariables, evidence[0].continuousVariables)
        println("Reduced partially")
        // partialDiagram.show("Partial diagram")
        partialDiagram.exportGraph("srn_partial_diagram.dot")


        val totalVolumeDiagram = integrator.integrate(partialDiagram, queryVars)
        // totalVolumeDiagram.show("Total volume")
        val totalVolume = totalVolumeDiagram.evaluate(Assignment())
        times.put("Pre-processing", stopWatch.stop() / 1000.0)
        println(times["Pre-processing"])
        val queryProbabilities = ArrayList<Double>()

        for(queryString in queryStrings) {
            var query = BoolXADD.convert(parser.parseXadd(queryString))
            query = query.evaluatePartial(evidence[0].booleanVariables, evidence[0].continuousVariables) // TODO hack with evidedence[0

            // val queryDiagram = partialDiagram.times(query)
            stopWatch.start()
            // TODO Reduce?
            val queryDiagram = partialDiagram.times(query)
            // queryDiagram.show("Query diagram")
            // val queryVolume = XADDiagram(xadd, integrator.integrate(queryDiagram.number, HashSet(queryVars))).evaluate(Assignment())

            println(queryVars)
            val queryVolume = integrator.integrate(queryDiagram, queryVars).evaluate(Assignment())

            // val queryVolume = XADDiagram(xadd, queryDiagram.getIntegratedDiagram()integrator.integrate(queryDiagram.number)).evaluate(Assignment())

            println(queryVolume.toString() + " " + totalVolume.toString())
            queryProbabilities.add(queryVolume / totalVolume)
            times.put("Q%d".format(times.size), stopWatch.stop() / 1000.0)
            println(times["Q%d".format(times.size - 1)])
        }
        return queryProbabilities
    }
}

fun main(args: Array<String>) {
    val file = args[0]
    val integrator = args[1]
    val engine = QueryEngine()

    val text = File(file).readText()
    val obj = JSONObject(text)
    val domainArr = obj.getJSONArray("domain")
    (0 until domainArr.length())
            .map { domainArr.getJSONObject(it) }
            .forEach { engine.addVar(it.getString("name"), it.getString("type")) }
    val theoryString = obj.getString("support")
    val weightsString = obj.getString("weight")
    val queryArr = obj.getJSONArray("queries")
    val queries = (0 until queryArr.length()).map { queryArr.getJSONObject(it).getString("formula") }
    val evidence = (0 until queryArr.length()).map { queryArr.getJSONObject(it).getJSONObject("evidence") }
            .map {
                var assignment = Assignment()
                val ev = it
                it.keySet().forEach {
                    assignment = if(engine.getType(it) == "real") {
                        assignment.setReal(it, ev.getDouble(it))
                    } else {
                        assignment.setBool(it, ev.getBoolean(it))
                    }
                }
                assignment
            }

    engine.runQueries(theoryString, weightsString, queries, evidence, integrator)
}