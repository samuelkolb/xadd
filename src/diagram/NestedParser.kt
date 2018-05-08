package diagram

import java.util.*

/**
 * Created by samuelkolb on 17/06/2017.
 * @author Samuel Kolb
 */

class NestedParser(var operators: Collection<String>? = null) {
    class ParsingException(message: String) : RuntimeException(message)

    abstract class Node(var name: String?)

    class OperatorNode(name: String? = null, val childNodes: ArrayList<Node> = ArrayList()) : Node(name) {
        override fun toString(): String {
            return "($name ${childNodes.joinToString(" ")})"
        }
    }

    class LeafNode(name: String) : Node(name) {
        fun isFloat() : Boolean {
            try {
                this.name!!.toDouble()
                return true
            } catch (e: NumberFormatException) {
                return false
            }
        }

        fun isInt() : Boolean {
            try {
                this.name!!.toInt()
                return true
            } catch (e: NumberFormatException) {
                return false
            }
        }

        fun toDouble() : Double = this.name!!.toDouble()

        fun toInt() : Int = this.name!!.toInt()

        override fun toString(): String {
            return "$name"
        }
    }

    fun parseString(string: String) : Node {
        return tokensToAst(tokenize(string))
    }

    private fun tokensToAst(tokens: List<String>) : Node {
        val stack = Stack<OperatorNode>()
        var root: Node? = null
        var current: OperatorNode?
        val operatorSet: Set<String>? = if(operators != null) HashSet(operators) else null
        for(token in tokens) {
            current = if(stack.empty()) null else stack.peek()
            if(token == "(") {
                val node = OperatorNode()
                stack.add(node)
                if(current == null) {
                    root = node;
                } else {
                    current.childNodes.add(node)
                }
            } else if(token == ")") {
                stack.pop()
            } else {
                if(current == null) {
                    throw ParsingException("Error with token '$token'")
                }
                if(current.name == null) {
                    if(operatorSet != null && token !in operatorSet)
                        throw ParsingException("Unrecognized token '$token'")
                    current.name = token
                } else {
                    current.childNodes.add(LeafNode(token))
                }
            }
        }
        return root ?: throw ParsingException("No root found")
    }

    fun tokenize(string: String) : List<String> {
        return Regex("\\s+").split(string.replace("(", " ( ").replace(")", " ) ").trim())
    }
}