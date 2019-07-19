package org.adoptopenjdk.jheappo.tools;

import org.adoptopenjdk.jheappo.parser.HeapProfile;
import java.io.FileInputStream
import java.nio.file.Paths

fun main() {

//    val path = Paths.get("tools/src/test/resources/org/adoptopenjdk/jheappo/heaptextoutput/heap.dump")
    val path = Paths.get("tmp/mastermind2.hprof")
    val heapDump = HeapProfile.open(path, FileInputStream(path.toFile()))

    HeapThing().populateFrom(heapDump)
}
