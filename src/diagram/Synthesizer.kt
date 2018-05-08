package diagram

import time.Stopwatch
import xadd.XADD
import java.util.*
import kotlin.collections.ArrayList

fun <T> List<T>.shuffled(rnd: Random): List<T> {
    val list = ArrayList(this)
    Collections.shuffle(list, rnd)
    return list
}

class Synthesizer(variables: Int, seed: Long, xadd: XADD?=null) {
    private var random: Random = Random(seed)
    private var xadd: XADD
    private var build: XADDBuild.Builder
    private var variables: List<String> = (0 until variables).map { "x$it" }

    init {
        this.xadd = xadd ?: XADD()
        this.build = XADDBuild.builder(this.xadd)
    }

    fun getVariables() : List<String> {
        return ArrayList(variables)
    }

    fun randomFixedTest(varsPerTest: Int) : BoolXADD {
        val vars = this.variables.shuffled(this.random).subList(0, varsPerTest)
        val coefficients = this.random.doubles(varsPerTest.toLong()).toArray().toList()
        val constant = this.random.nextDouble()
        val testString = vars.zip(coefficients).joinToString(" + ") { "${it.second} * ${it.first}" }
        return this.build.test("$testString <= $constant")
    }

    fun randomFunc(varsPerExpression: Int) : XADDiagram {
        val vars = this.variables.shuffled(this.random).subList(0, varsPerExpression)
        val coefficients = this.random.doubles(varsPerExpression.toLong()).toArray().toList()
        val testString = vars.zip(coefficients).joinToString(" + ") { "${it.second} * ${it.first}" }
        return this.build.`val`(testString)
    }

    fun getBounds() : BoolXADD {
        var bounds = this.build.`val`(true)
        variables.forEach { bounds = bounds.and(build.test("$it >= 0").and(build.test("$it <= 1")))}
        return bounds
    }

    fun generateStructuredDiagram(layers: Int, breadth: Int, varsPerTest: Int) : XADDiagram {
        if(layers < breadth) {
            throw IllegalArgumentException("Insufficient layers given ($layers), should be at least ($breadth = breadth).")
        }
        return getBounds().times(generateLayer(0, layers, breadth, varsPerTest)[0])
    }

    fun generateLayer(parents: Int, layers: Int, breadth: Int, varsPerTest: Int) : List<XADDiagram> {
        val size = Math.min(breadth, parents + 1)
        if(layers == 0) {
            return (0 until size).map { randomFunc(varsPerTest) }
        }
        val newTests = (0 until size).map { randomFixedTest(varsPerTest) }
        val children = generateLayer(newTests.size, layers - 1, breadth, varsPerTest)
        return newTests.mapIndexed { i, test -> test.assignWeights(children[i], children[(i + 1) % children.size]) }
    }

    fun generateExactlyOne(terms: Int) : BoolXADD {
        val baseLb = 0.13
        val baseUb = 0.89
        val step = 0.01

        var bounds = build.test("x >= $baseLb").and(build.test("x <= $baseUb"))
        (0 until terms).forEach {
            val lb = build.test("c$it >= " + (baseLb + it * step))
            val ub = build.test("c$it <= " + (baseUb - it * step))
            bounds = bounds.and(lb.and(ub))
        }

        val termList = ArrayList<BoolXADD>()
        (0 until terms).forEach { termList.add(build.test("x <= c$it")) }

        var disjunction = termList.fold(build.`val`(false)) { cumulative, new ->
            cumulative.or(new)
        }

        for(i in 0 until terms) {
            for(j in i + 1 until terms) {
                disjunction = disjunction.and(termList.get(i).not().or(termList.get(j).not()))
            }
        }

        return bounds.and(disjunction);
    }

    /*
    def xor(d1, d2):
        return (d1 | d2) & ~(d1 & d2)


    def get_xor_diagram():
        b = Builder()
        b.ints("x", "c1", "c2", "c3", "c4")

        x_c1 = b.test("x < c1")
        x_c2 = b.test("x < c2 + c1")
        x_c3 = b.test("x < c3 + c2")
        x_c4 = b.test("x < c4 + c3")

        return xor(xor(xor(x_c1, x_c2), x_c3), x_c4)

     */

    fun BoolXADD.xor(diagram: BoolXADD) : BoolXADD {
        return this.or(diagram).and(this.and(diagram).not())
    }

    fun generateXor(terms: Int) : BoolXADD {
        val baseLb = 0.13
        val baseUb = 0.89
        val step = 0.01

        var bounds = build.test("x >= $baseLb").and(build.test("x <= $baseUb"))
        (0 until terms).forEach {
            val lb = build.test("c$it >= " + (baseLb + it * step))
            val ub = build.test("c$it <= " + (baseUb - it * step))
            bounds = bounds.and(lb.and(ub))
        }

        val termList = ArrayList<BoolXADD>()
        (0 until terms).forEach { termList.add(build.test("x <= c$it")) }

        val xor = termList.fold(build.`val`(false)) { cumulative, new ->
            cumulative.xor(new)
        }

        return bounds.and(xor)
    }
}

fun main(args: Array<String>) {
    // TODO Last layer two nodes

    val seed = System.currentTimeMillis()
    println("Seed: $seed")
    val synthesizer = Synthesizer(10, seed)
    /*
    val diagram = synthesizer.generateStructuredDiagram(5, 5, 2).reduceLp()
    diagram.show("Synthetic")

    diagram.eliminateRealVars(synthesizer.getVariables()).show("Eliminated resolve")
    diagram.getIntegratedDiagram(emptyList(), synthesizer.getVariables()).show("Eliminated original")*/
    for(terms in 1..30) {
        val variables = (0 until terms).reversed().map { "c$it" } + listOf("x")
        // val xadd = OrderedXADD(variables.reversed(), false)
        // val synthesizer = Synthesizer(10, seed, xadd)
        val diagram = synthesizer.generateExactlyOne(terms)
        // val diagram = synthesizer.generateXor(terms)
        // diagram.show("XOR " + terms)
        // diagram.show("Exactly 1 of $terms")
        val timer = Stopwatch(true)
        // val result = diagram.eliminateRealVars(variables)
        val integrator = ResolutionIntegrator(diagram.xadd)
        val result = integrator.integrate(diagram, variables.map { Variable.real(it) })
        // val result = diagram.getIntegratedDiagram(listOf(), variables).evaluate()
        // val integrator = ResolveAllIntegration(xadd, variables.map { "real" })
        // val result = XADDiagram(xadd, integrator.integrate(diagram.number)).evaluate()
        println("Took " + (timer.stop() / 1000) + "s for $terms terms, got $result")
    }
}
