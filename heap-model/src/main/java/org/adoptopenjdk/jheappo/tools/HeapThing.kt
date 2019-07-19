package org.adoptopenjdk.jheappo.tools

import org.adoptopenjdk.jheappo.parser.FieldType
import org.adoptopenjdk.jheappo.parser.HeapDumpSegment
import org.adoptopenjdk.jheappo.parser.HeapProfile
import org.adoptopenjdk.jheappo.parser.LoadClass
import org.adoptopenjdk.jheappo.parser.StackFrame
import org.adoptopenjdk.jheappo.parser.StackTrace
import org.adoptopenjdk.jheappo.parser.UTF8StringSegment
import org.adoptopenjdk.jheappo.parser.heap.ClassMetadata
import org.adoptopenjdk.jheappo.parser.heap.InstanceObject
import org.adoptopenjdk.jheappo.parser.heap.ObjectArray
import org.adoptopenjdk.jheappo.parser.heap.PrimitiveArray
import org.adoptopenjdk.jheappo.parser.heap.RootJNIGlobal
import org.adoptopenjdk.jheappo.parser.heap.RootJNILocal
import org.adoptopenjdk.jheappo.parser.heap.RootJavaFrame
import org.adoptopenjdk.jheappo.parser.heap.RootMonitorUsed
import org.adoptopenjdk.jheappo.parser.heap.RootStickyClass
import org.adoptopenjdk.jheappo.parser.heap.RootThreadObject
import org.adoptopenjdk.jheappo.parser.heap.UTF8String
import org.adoptopenjdk.jheappo.parser.Id
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.HashMap

class HeapThing {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(HeapThing::class.java)
    }

    private val stringTable = HashMap<Id, UTF8String>()
    private val clazzTable = HashMap<Id, ClassMetadata>()
    private val loadClassTable = HashMap<Id, LoadClass>()
    private val classStuff = HashMap<Id, ClassStuff>()

    fun populateFrom(heapDump: HeapProfile) {
        val header = heapDump.readHeader()
        val classTodo = mutableListOf<ClassMetadata>()

        while (!heapDump.isAtHeapDumpEnd) {
            when (val frame = heapDump.extract()) {
                is StackFrame -> {
                    logger.debug("Stack frame")
                    // Do Nothing
                }
                is StackTrace -> {
                    logger.debug("Stack trace")
                    // Do Nothing
                }
                is UTF8StringSegment -> {
                    logger.debug("UTF8 string")
                    val string = frame.toUtf8String()
                    stringTable[string.id] = string
                }
                is LoadClass -> {
                    logger.debug("Load class")
                    loadClassTable[frame.classObjectID] = frame //store mapping of class to class name.
                }
                is HeapDumpSegment -> {
                    logger.debug("Heap dump")

                    while (frame.hasNext()) {
                        val heapObject = frame.next()
                        if (heapObject == null) {
                            logger.warn("parser error resolving type in HeapDumpSegment....")
                            continue
                        }

                        when (heapObject) {
                            is ClassMetadata -> {
                                clazzTable[heapObject.id] = heapObject

                                val inflated = inflateClass(heapObject)
                                if (inflated != null) {
                                    classStuff[heapObject.id] = inflated
                                } else {
                                    classTodo.add(heapObject)
                                }
                            }
                            is InstanceObject, is RootJNIGlobal, is RootJNILocal, is PrimitiveArray, is ObjectArray, is RootJavaFrame, is RootThreadObject, is RootMonitorUsed, is RootStickyClass -> {
                                // not handledyet
                            }
                            else -> logger.warn("missed heap dump element : $heapObject")
                        }
                    }
                }
                else -> logger.warn("missed frame: $frame")
            }.exhaustive()
        }

        logger.info("inflated: ${classStuff.size}, todo: ${classTodo.size}")

        // Iteratively make progress on classes awaiting data.
        // In practice, it seems that this only takes one pass.
        while (classTodo.isNotEmpty()) {
            val iter = classTodo.iterator()
            var madeProgress = false

            while (iter.hasNext()) {
                val cm = iter.next()
                val inflated = inflateClass(cm)
                if (inflated != null) {
                    madeProgress = true
                    iter.remove()
                    classStuff[cm.id] = inflated
                }
            }

            if (!madeProgress) {
                throw IllegalStateException("Did not complete parsing of any classes")
            }
            logger.info("remaining classes: ${classTodo.size}")
        }

        // free up memory; don't need these any more
        clazzTable.clear()
        loadClassTable.clear()

        classStuff.forEach { (id, stuff) ->
            logger.info("$id - $stuff")
        }
    }

    /**
     * @return non-null if all class data is available
     */
    private fun inflateClass(cm: ClassMetadata): ClassStuff? {
        val loadClass = loadClassTable[cm.id] ?: return null
        val className = stringTable[loadClass.classNameStringID] ?: return null

        val fields = cm.instanceFields.map { f ->
            val name = stringTable[f.nameId] ?: return null
            FieldStuff(name.string, f.type)
        }

        val superclass = if (cm.superClassObjectID.id == 0UL) {
            // Object has superclass id 0
            null
        } else {
            inflateClass(clazzTable[cm.superClassObjectID] ?: return null) ?: return null
        }
        return ClassStuff(className.string, fields, superclass)
    }
}

class FieldStuff(val name: String, val type: FieldType) {
    override fun toString(): String {
        return "$name: $type"
    }
}
data class ClassStuff(val name: String, val instanceFields: List<FieldStuff>, val superclass: ClassStuff?)

// force `when` expression to match all variants
fun Any.exhaustive(): Unit = Unit
